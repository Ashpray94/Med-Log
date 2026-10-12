package com.suryaprakash.medlog.doctor

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * The doctor page as A4: a quiet clinical note. Black text, one accent for urgent items,
 * a small body diagram with numbered pins that match the symptom table. Nothing about the app.
 */
object Pdf {
    private const val W = 595
    private const val H = 842
    private const val M = 36f
    private const val INK = 0xFF1C1C1E.toInt()
    private const val SOFT = 0xFF5B5B60.toInt()
    private const val LINE = 0xFFD9D6D0.toInt()
    private const val RED = 0xFFB3261E.toInt()
    private const val AMBER = 0xFF8A4B00.toInt()

    /** Keep abnormal positives; omit routine reassuring answers to red-flag questions from the glance report. */
    internal fun includeGlanceFact(label: String, value: String): Boolean {
        val l = label.lowercase(); val v = value.lowercase()
        return when {
            "blood" in l && ("no blood" in v || v == "no" || v == "false") -> false
            "coffee" in l && ("not like" in v || v == "no" || v == "false") -> false
            "forceful" in l && ("not forceful" in v || v == "no" || v == "false") -> false
            "keep water" in l && "can keep" in v -> false
            "passed urine" in l && "passed urine" in v -> false
            "tookmedicine" in l && v == "tookmedicine: false" -> false
            else -> true
        }
    }

    fun write(ctx: Context, n: DoctorNote, nut: com.suryaprakash.medlog.nutrition.Nutrition.Report? = null): File {
        val doc = PdfDocument()
        ReportValidation.requireAccurate(n)
        val w = Writer(doc, n.period, n.footer)
        w.page()

        // Page 1: information a clinician needs before opening the detailed history.
        w.pair("Patient-reported health summary", n.period, 15f)
        w.text(n.patient, 11f, bold = true)
        // only what was recorded: no "none known" or "none recorded" rows
        if (n.allergies.isNotBlank()) w.kv("Allergies", n.allergies, RED)
        if (n.conditions.isNotBlank()) w.kv("Conditions", n.conditions)
        if (n.currentMeds.isNotBlank()) w.kv("Medicines", n.currentMeds)
        w.rule()

        if (n.concerns.isNotEmpty()) {
            w.section("Priorities for review")
            n.concerns.forEachIndexed { i, concern -> w.text("${i + 1}. $concern", 11f, bold = true,
                color = when (n.concernLevels.getOrNull(i)) { "RED" -> RED; "AMBER" -> AMBER; else -> INK }) }
        }
        if (n.symptoms.isNotEmpty()) {
            w.section("Symptom overview")
            w.table(listOf("Symptom", "Reports", "Severity", "Last noted"), floatArrayOf(0.30f, 0.12f, 0.18f, 0.40f),
                n.symptoms.map { r -> listOf("${r.n}. ${r.name}", "${r.reportCount}",
                    r.sevLow?.let { "${it}–${r.sevHigh ?: it}/10" }.orEmpty(), r.lastNoted.orEmpty()) to
                    when (r.urgent) { "RED" -> RED; "AMBER" -> AMBER; else -> INK } })
        }
        if (n.readings.isNotEmpty()) { w.section("Latest readings"); n.readings.forEach { w.text(it, 10f) } }
        if (n.questions.isNotEmpty()) { w.section("Patient's questions"); n.questions.forEach { w.text("•  $it", 10.5f) } }
        if (n.medicines.isNotEmpty()) {
            w.section("Medicines in this period")
            w.table(listOf("Medicine", "Dose", "Taken", "Changes / notes"), floatArrayOf(0.25f, 0.22f, 0.12f, 0.41f),
                n.medicines.map { m -> listOf(m.name, m.dose, m.taken, m.change) to INK }, dropEmpty = true)
        }

        // Pages 2–3: each recurring issue at a glance, while preserving what changed between reports.
        fun issueDetails(rows: List<DoctorNote.Row>) {
            rows.forEach { r ->
                w.section("${r.n}. ${r.name}")
                w.text(listOfNotNull("${r.reportCount} report${if (r.reportCount == 1) "" else "s"}",
                    r.sevLow?.let { "severity ${it}–${r.sevHigh ?: it}/10; latest ${r.sevLast ?: it}/10" },
                    r.began?.let { "started $it" }, r.lastNoted?.let { "last $it" }, r.trend?.let { "reported $it" })
                    .joinToString(" · "), 9.5f, bold = true,
                    color = when (r.urgent) { "RED" -> RED; "AMBER" -> AMBER; else -> INK })
                val reports = n.entries.filter { it.symptomNumber == r.n && it.noted }
                val severities = reports.mapNotNull { e -> e.facts.firstNotNullOfOrNull { (_, value) ->
                    Regex("(\\d+)\\s*out of 10", RegexOption.IGNORE_CASE).find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
                } }
                if (severities.size >= 2) w.miniTrend(severities, "Recorded severity — oldest to latest", 10)
                else w.miniTrend(r.daily, "Report frequency by day — not a recovery measure")
                if (reports.isNotEmpty()) {
                    w.table(listOf("Date", "Specific recorded details"), floatArrayOf(0.25f, 0.75f), reports.map { e ->
                        val details = (listOfNotNull(e.site, e.depth) + e.facts.filter { includeGlanceFact(it.first, it.second) }
                            .map { "${it.first}: ${it.second}" } + listOfNotNull(e.remark))
                            .filter { it.isNotBlank() }.distinct().joinToString("; ")
                        listOf(e.date, details.ifBlank { "Symptom reported" }) to INK
                    })
                }
                val updates = n.entries.count { it.symptomNumber == r.n && !it.noted }
                if (updates > 0) w.text("$updates improvement/update entr${if (updates == 1) "y" else "ies"} recorded separately.", 8.5f, color = SOFT)
            }
        }
        w.page()
        w.pair("Recurring issues — report detail", n.period, 15f)
        issueDetails(n.symptoms.take(2))

        w.page()
        w.pair("Other issues and treatment", n.period, 15f)
        issueDetails(n.symptoms.drop(2))
        if (n.links.isNotEmpty()) { w.section("Timing noticed"); n.links.forEach { w.text("•  $it", 10f) } }

        // Page 4: nutrition. Only recent representative days are printed; totals cover the selected period.
        nut?.takeIf { r -> r.days.any { it.logged } || r.feeds.isNotEmpty() }?.let { r ->
            w.page()
            w.pair("Nutrition and weight", n.period, 15f)
            val found = (listOf(r.headline) + r.findings.drop(1)).filter { !it.text.startsWith("Nothing logged") }
            found.firstOrNull()?.let { h -> w.text(h.text, 12.5f, bold = true, color = if (h.level == "RED") RED else if (h.level == "AMBER") AMBER else INK) }
            found.drop(1).forEach { f -> w.text("•  ${f.text}", 10.5f, color = if (f.level == "RED") RED else if (f.level == "AMBER") AMBER else INK) }
            w.gap(4f)
            val logged = r.days.filter { it.logged }
            if (logged.isNotEmpty()) w.kv("Calories", r.kcalTarget?.let { "${r.avgKcal.toInt()} of ${it.toInt()} kcal a day (${r.kcalPct}%)" } ?: "${r.avgKcal.toInt()} kcal a day")
            if (logged.isNotEmpty()) w.kv("Protein", r.proteinTarget?.let { "${r.avgProtein.toInt()} of ${it.toInt()} g a day (${r.proteinPct}%)" } ?: "${r.avgProtein.toInt()} g a day")
            r.weightChange?.let { w.kv("Weight", "${"%.1f".format(r.weights.first().second)} → ${"%.1f".format(r.weights.last().second)} kg in ${r.weightDays} days") }
            if (logged.isNotEmpty()) w.kv("Targets", if (r.targetsFromDoctor) "Set by the doctor" else "30 kcal and 1 g protein per kg (to be confirmed)")
            if (r.feeds.isNotEmpty()) w.kv("Feeds", r.feeds.joinToString("; "))
            w.rule()
            if (logged.isNotEmpty()) {
                w.section("Recent logged days")
                w.table(listOf("Day", "kcal", "Protein", "Water", "What went in"), floatArrayOf(0.14f, 0.09f, 0.1f, 0.08f, 0.59f),
                    logged.takeLast(7).reversed().map { d -> listOf(d.date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")), "${d.kcal.toInt()}",
                        "${d.protein.toInt()} g", if (d.water > 0) "${d.water}" else "", d.items.joinToString(", ").take(110)) to
                        (r.kcalTarget?.let { t -> if (d.kcal < t * 0.6) RED else if (d.kcal < t * 0.85) AMBER else INK } ?: INK) }, dropEmpty = true)
                if (logged.size > 7) w.text("${logged.size - 7} earlier logged days remain available in MedLog.", 9f, color = SOFT)
            }
            if (r.changes.isNotEmpty()) { w.section("What changed"); r.changes.forEach { w.text("•  $it", 10f) } }
            if (r.observed.isNotEmpty()) { w.section("Also noticed"); r.observed.forEach { w.text("•  $it", 10f) } }
            if (logged.isNotEmpty()) w.text("Food values are estimates for home cooking.", 9f, color = SOFT)
            }
        w.finish()

        // a new name each time, so a viewer never shows a copy it kept from before; older ones are cleared
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles { x -> x.name.startsWith("Symptom-summary") || x.name.startsWith("Health-summary") }?.forEach { it.delete() }
        val f = File(dir, "Health-summary-${java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.ENGLISH).format(java.util.Date())}.pdf")
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        return f
    }

    private class Writer(val doc: PdfDocument, val period: String, val footerText: String) {
        var page: PdfDocument.Page? = null
        lateinit var c: Canvas
        var y = M
        var n = 0
        var full = false
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val bottom = H - 64f
        val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
        val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LINE; strokeWidth = 0.8f }

        fun page() {
            if (n >= 4) { full = true; return }
            finish(); n++; page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create()); c = page!!.canvas; y = M
            if (n > 1) { text(period, 9f, color = SOFT); gap(5f) }
        }
        fun finish() { page?.let { footer("$footerText  Page $n · Full dated record remains in MedLog"); doc.finishPage(it) }; page = null }
        private fun ensure(h: Float): Boolean { if (full) return false; if (y + h > bottom) page(); return !full }

        private fun layout(s: String, size: Float, tf: Typeface, color: Int, width: Int): StaticLayout {
            tp.textSize = size; tp.typeface = tf; tp.color = color
            return StaticLayout.Builder.obtain(s, 0, s.length, TextPaint(tp), width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(1f, 1.08f).build()
        }

        fun text(s: String, size: Float, bold: Boolean = false, color: Int = INK, right: Float = 0f) {
            val l = layout(s, size, if (bold) this.bold else regular, color, (W - 2 * M - right).toInt())
            var first = 0
            while (first < l.lineCount) {
                if (!ensure(l.getLineBottom(first).toFloat() - l.getLineTop(first))) return
                var last = first
                while (last + 1 < l.lineCount && y + l.getLineBottom(last+1) - l.getLineTop(first) <= bottom) last++
                val top = l.getLineTop(first); val height = l.getLineBottom(last)-top
                c.save(); c.translate(M,y-top); c.clipRect(0f,top.toFloat(),(W-2*M-right), (top+height).toFloat()); l.draw(c); c.restore()
                y += height + 3f; first = last + 1
                if (first < l.lineCount) page()
            }
        }

        fun pair(left: String, right: String, size: Float) {
            if (!ensure(size + 10f)) return
            tp.textSize = size; tp.typeface = bold; tp.color = INK
            c.drawText(left, M, y + size, tp)
            tp.textSize = 10f; tp.typeface = regular; tp.color = SOFT
            c.drawText(right, W - M - tp.measureText(right), y + size, tp)
            y += size + 10f
        }

        fun kv(k: String, v: String, color: Int = INK, right: Float = 0f) {
            val kl = layout(k, 9.5f, medium, SOFT, 70)
            val vl = layout(v, 10f, if (color == RED) bold else regular, color, (W - 2 * M - 76 - right).toInt())
            val height = maxOf(kl.height, vl.height).toFloat()
            if (height > bottom - M - 30) { text("$k: $v", 10f, color = color, right = right); return }
            if (!ensure(height)) return
            c.save(); c.translate(M, y + 0.5f); kl.draw(c); c.restore()
            c.save(); c.translate(M + 76, y); vl.draw(c); c.restore()
            y += maxOf(kl.height, vl.height) + 3f
        }

        fun rule() { if (!ensure(14f)) return; y += 4f; c.drawLine(M, y, W - M, y, lp); y += 10f }
        fun gap(h: Float) { if (ensure(h)) y += h }

        fun section(title: String, right: Float = 0f) {
            if (!ensure(28f)) return
            y += 6f
            tp.textSize = 10f; tp.typeface = bold; tp.color = SOFT
            c.drawText(title.uppercase(), M, y + 10f, tp.apply { letterSpacing = 0.06f })
            tp.letterSpacing = 0f
            y += 17f
            if (right == 0f) { c.drawLine(M, y - 3f, W - M, y - 3f, lp) }
        }

        /** With [dropEmpty], a column with nothing in any row is left out and the others share its width. */
        fun table(head0: List<String>, frac0: FloatArray, rows0: List<Pair<List<String>, Int>>, dropEmpty: Boolean = false) {
            val keep = head0.indices.filter { i -> !dropEmpty || rows0.any { it.first.getOrNull(i).orEmpty().isNotBlank() } }
            val head = keep.map { head0[it] }
            val frac = keep.map { frac0[it] }.let { f -> val sum = f.sum(); FloatArray(f.size) { f[it] / sum } }
            val rows = rows0.map { (cells, color) -> keep.map { cells.getOrElse(it) { "" } } to color }
            val total = W - 2 * M
            val xs = FloatArray(frac.size) { i -> M + total * frac.take(i).sum() }
            val ws = FloatArray(frac.size) { total * frac[it] - 6f }
            if (!ensure(16f)) return
            tp.textSize = 8.5f; tp.typeface = medium; tp.color = SOFT
            head.forEachIndexed { i, h -> c.drawText(h, xs[i], y + 9f, tp) }
            y += 14f
            c.drawLine(M, y - 2f, W - M, y - 2f, lp)
            for ((cells, color) in rows) {
                val ls = cells.mapIndexed { i, s -> layout(s, 9f, if (i == 1) bold else regular, if (i <= 1) color else INK, ws[i].toInt().coerceAtLeast(20)) }
                val h = ls.maxOf { it.height } + 7f
                if (!ensure(h)) break
                ls.forEachIndexed { i, l -> c.save(); c.translate(xs[i], y + 3f); l.draw(c); c.restore() }
                y += h
                c.drawLine(M, y - 1f, W - M, y - 1f, lp)
            }
            y += 4f
        }

        /** Actual anchors and separate numbered labels, shared with the on-screen map. */
        fun bodyDiagram(x: Float, top: Float, width: Float, pins: List<Pair<Int, String>>, art: (Boolean) -> android.graphics.Bitmap?) {
            if (full) return
            val marks = BodyMarkers.layout(pins)
            val fig = width / 2f - 4f
            val u = fig / 100f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            for ((k, back) in listOf(0 to false, 1 to true)) {
                val ox = x + k * (fig + 8f)
                art(back)?.let { c.drawBitmap(it, null, RectF(ox, top, ox + 100f * u, top + 170f * u), paint) }
                tp.textSize = 8f; tp.typeface = regular; tp.color = SOFT
                val lbl = if (back) "Back" else "Front"
                c.drawText(lbl, ox + (fig - tp.measureText(lbl)) / 2, top + 170f * u + 12f, tp)
                for (mark in marks.filter { it.back == back }) {
                    if (mark.all) art(back)?.let { a -> c.drawBitmap(a, null, RectF(ox, top, ox + 100f * u, top + 170f * u), Paint(paint).apply {
                        colorFilter = android.graphics.PorterDuffColorFilter(AMBER, android.graphics.PorterDuff.Mode.SRC_IN); alpha = 70
                    }) }
                    val ax = ox + mark.x * u; val ay = top + mark.y * u
                    val lx = ox + mark.labelX * u; val ly = top + mark.labelY * u
                    paint.color = SOFT; paint.strokeWidth = 0.8f
                    c.drawLine(ax, ay, lx, ly, paint)
                    paint.color = INK; c.drawCircle(ax, ay, 2.5f * u, paint)
                    paint.color = Color.WHITE; c.drawCircle(lx, ly, 10f * u, paint)
                    paint.color = AMBER; c.drawCircle(lx, ly, 8.5f * u, paint)
                    tp.textSize = 10f * u; tp.typeface = bold; tp.color = Color.WHITE
                    val t = "${mark.number}"; c.drawText(t, lx - tp.measureText(t)/2, ly + 3.5f*u, tp)
                }
            }
        }

        fun mapBlock(pins: List<Pair<Int, String>>, art: (Boolean) -> android.graphics.Bitmap?) {
            val width = 220f; val h = (width/2-4) * 1.7f + 18f
            if (!ensure(h)) return
            bodyDiagram(M, y, width, pins, art)
            y += h + 6f
        }

        fun chart(daily: List<Int>, from: Long, to: Long) {
            if (daily.isEmpty()) return
            if (!ensure(112f)) return
            text("Times noted each day", 10f, bold = true)
            val top = y; val height = 52f; val width = W - 2*M - 20f
            val maximum = (daily.maxOrNull() ?: 0).coerceAtLeast(1)
            val slot = width / daily.size
            tp.textSize = 8f; tp.color = SOFT; tp.typeface = regular
            c.drawText("$maximum", M, top+7, tp); c.drawText("0", M, top+height, tp)
            c.drawLine(M+16, top+height, W-M, top+height, lp)
            val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER }
            daily.forEachIndexed { i, count ->
                if (count > 0) {
                    val x = M+20+i*slot; val h = height*count/maximum
                    c.drawRect(x+slot*0.15f, top+height-h, x+slot*0.85f, top+height, bar)
                    if (daily.size <= 31) { tp.color = INK; tp.textSize = 8f; c.drawText("$count", x+slot/2-tp.measureText("$count")/2, top+height-h-3, tp) }
                }
            }
            y = top + height + 7f
            val df = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH)
            pair(df.format(java.util.Date(from)), df.format(java.util.Date(to-1)), 9f)
            text("An empty day means the symptom was not noted that day.", 9f, color = SOFT)
        }

        /** A compact, explicitly labelled trend used beside each ailment summary. */
        fun miniTrend(values: List<Int>, label: String, fixedMax: Int? = null) {
            if (values.isEmpty() || !ensure(48f)) return
            text(label, 8.5f, bold = true, color = SOFT)
            val top = y; val height = 24f; val width = W - 2 * M
            val maximum = (fixedMax ?: values.maxOrNull() ?: 1).coerceAtLeast(1)
            val slot = width / values.size.coerceAtLeast(1)
            val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER }
            values.forEachIndexed { i, value ->
                val h = height * value.coerceIn(0, maximum) / maximum
                c.drawRect(M + i * slot + 1f, top + height - h, M + (i + 1) * slot - 1f, top + height, bar)
            }
            c.drawLine(M, top + height, W - M, top + height, lp)
            y = top + height + 3f
            tp.textSize = 7.5f; tp.typeface = regular; tp.color = SOFT
            c.drawText("Oldest", M, y + 7f, tp)
            val latest = "Latest"; c.drawText(latest, W - M - tp.measureText(latest), y + 7f, tp)
            y += 12f
        }

        fun entry(e: DoctorNote.Entry, art: (Boolean) -> android.graphics.Bitmap?) {
            val fields = listOfNotNull(e.site?.let { "Location" to it }, e.depth?.let { "Depth" to it }) + e.facts
            val map = e.pins.isNotEmpty()
            val right = if (map) 180f else 0f
            val width = (W-2*M-76-right).toInt()
            val height = maxOf(if (map) 166f else 0f, fields.sumOf { maxOf(layout(it.first, 9.5f, medium, SOFT, 70).height, layout(it.second, 10f, regular, INK, width).height) + 3 }.toFloat()) + 46f
            if (!ensure(height.coerceAtMost(bottom-M))) return
            text(e.date + if (e.noted) " - symptom noted" else " - update", 11f, bold = true)
            val top = y
            if (map) bodyDiagram(W-M-170f, top, 170f, e.pins.map { e.symptomNumber to it }, art)
            fields.forEach { (key,value) -> kv(key, value, right = right) }
            y = maxOf(y, top + if (map) 160f else 0f)
            e.remark?.takeIf { it.isNotBlank() }?.let { kv("Remark", it) }
            rule()
        }

        fun footer(s: String) {
            val l = layout(s, 7.5f, regular, SOFT, (W - 2 * M).toInt())
            c.drawLine(M, H - 30f - l.height, W - M, H - 30f - l.height, lp)
            c.save(); c.translate(M, H - 24f - l.height); l.draw(c); c.restore()
        }
    }

    fun share(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Share symptom summary").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun print(ctx: Context, f: File) {
        val pm = ctx.getSystemService(PrintManager::class.java) ?: return
        pm.print("Symptom summary", object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
                // the real number of pages: some phones' print preview shows only the first page when it isn't given
                val pages = runCatching { android.graphics.pdf.PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount } }.getOrDefault(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                cb.onLayoutFinished(PrintDocumentInfo.Builder("Symptom-summary.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build(), true)
            }
            override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
                runCatching { f.inputStream().use { input -> FileOutputStream(dest.fileDescriptor).use { input.copyTo(it) } }; cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
                    .onFailure { cb.onWriteFailed(it.message) }
            }
        }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }
}
