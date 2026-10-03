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

    fun write(ctx: Context, n: DoctorNote, nut: com.suryaprakash.medlog.nutrition.Nutrition.Report? = null): File {
        val doc = PdfDocument()
        ReportValidation.requireAccurate(n)
        val w = Writer(doc, n.period, n.footer)
        w.page()

        // ── heading ──
        w.pair("Patient-reported symptoms", n.period, 15f)
        w.text(n.patient, 11f, bold = true)
        // only what was recorded: no "none known" or "none recorded" rows
        if (n.allergies.isNotBlank()) w.kv("Allergies", n.allergies, RED)
        if (n.conditions.isNotBlank()) w.kv("Conditions", n.conditions)
        if (n.currentMeds.isNotBlank()) w.kv("Medicines", n.currentMeds)
        w.rule()

        // Overview uses the same note counts as the page and the dated appendix.
        if (n.symptoms.isNotEmpty()) {
            w.section("Symptoms in the selected period")
            w.text("Counts show how often a symptom was noted, not how many episodes occurred.", 10f, color = SOFT)
            w.table(listOf("Symptom", "Times noted", "Last noted", "Started (if given)"), floatArrayOf(0.22f, 0.12f, 0.30f, 0.36f),
                n.symptoms.map { r -> listOf("${r.n}. ${r.name}", "${r.reportCount}", r.lastNoted.orEmpty(), r.began.orEmpty()) to INK })
            if (n.pins.isNotEmpty()) {
                w.section("Body locations - overview")
                w.mapBlock(n.pins) { back -> com.suryaprakash.medlog.pictogram.BodyArt.bitmap(ctx, back, com.suryaprakash.medlog.pictogram.WHOLE, 500) }
                n.symptoms.filter { row -> n.pins.any { it.first == row.n } }.forEach { w.text("${it.n}. ${it.name}", 10f) }
                w.text("Dots mark the locations given. Lines connect each dot to its numbered label.", 9f, color = SOFT)
            }
        }
        if (n.medicines.isNotEmpty()) {
            w.section("Medicines in this period")
            w.table(listOf("Medicine", "Dose", "Taken", "Changes / notes"), floatArrayOf(0.26f, 0.2f, 0.14f, 0.4f), n.medicines.map { m -> listOf(m.name, m.dose, m.taken, m.change) to INK }, dropEmpty = true)
        }
        if (n.readings.isNotEmpty()) { w.section("Readings"); n.readings.forEach { w.text(it, 10f) } }
        if (n.links.isNotEmpty()) { w.section("Timing noticed"); n.links.forEach { w.text(it, 10f, color = SOFT) } }
        if (n.questions.isNotEmpty()) { w.section("Patient's questions"); n.questions.forEach { w.text("•  $it", 10.5f) } }
        // One symptom, one graph and every dated entry. Depth is included only when supplied.
        for (r in n.symptoms) {
            w.page()
            w.pair("${r.n}. ${r.name}", n.period, 15f)
            w.text("${r.name} was noted ${r.reportCount} ${if (r.reportCount == 1) "time" else "times"} in this period.", 12f, bold = true)
            r.lastNoted?.let { w.kv("Last noted", it) }
            r.began?.let { w.kv("Started", it) }
            r.quote?.let { w.kv("Remark", it + r.quoteDate?.let { date -> " ($date)" }.orEmpty()) }
            w.chart(r.daily, n.from, n.to)
            w.section("Entries - oldest first")
            for (entry in n.entries.filter { it.symptomNumber == r.n }) w.entry(entry) { back ->
                com.suryaprakash.medlog.pictogram.BodyArt.bitmap(ctx, back, com.suryaprakash.medlog.pictogram.WHOLE, 500)
            }
        }
        // ── nutrition: its own page, verdict first, detail after ──
        // only when food or feeds were logged; days with nothing logged, and "nothing logged" findings, are left out
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
                w.section("Day by day")
                w.table(listOf("Day", "kcal", "Protein", "Water", "What went in"), floatArrayOf(0.14f, 0.09f, 0.1f, 0.08f, 0.59f),
                    logged.reversed().map { d -> listOf(d.date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")), "${d.kcal.toInt()}",
                        "${d.protein.toInt()} g", if (d.water > 0) "${d.water}" else "", d.items.joinToString(", ")) to
                        (r.kcalTarget?.let { t -> if (d.kcal < t * 0.6) RED else if (d.kcal < t * 0.85) AMBER else INK } ?: INK) }, dropEmpty = true)
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
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val bottom = H - 64f
        val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
        val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LINE; strokeWidth = 0.8f }

        fun page() { finish(); n++; page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create()); c = page!!.canvas; y = M; if (n > 1) { text(period, 9f, color = SOFT); gap(5f) } }
        fun finish() { page?.let { footer("$footerText  Page $n"); doc.finishPage(it) }; page = null }
        private fun ensure(h: Float) { if (y + h > bottom) page() }

        private fun layout(s: String, size: Float, tf: Typeface, color: Int, width: Int): StaticLayout {
            tp.textSize = size; tp.typeface = tf; tp.color = color
            return StaticLayout.Builder.obtain(s, 0, s.length, TextPaint(tp), width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(1f, 1.08f).build()
        }

        fun text(s: String, size: Float, bold: Boolean = false, color: Int = INK, right: Float = 0f) {
            val l = layout(s, size, if (bold) this.bold else regular, color, (W - 2 * M - right).toInt())
            var first = 0
            while (first < l.lineCount) {
                ensure(l.getLineBottom(first).toFloat() - l.getLineTop(first))
                var last = first
                while (last + 1 < l.lineCount && y + l.getLineBottom(last+1) - l.getLineTop(first) <= bottom) last++
                val top = l.getLineTop(first); val height = l.getLineBottom(last)-top
                c.save(); c.translate(M,y-top); c.clipRect(0f,top.toFloat(),(W-2*M-right), (top+height).toFloat()); l.draw(c); c.restore()
                y += height + 3f; first = last + 1
                if (first < l.lineCount) page()
            }
        }

        fun pair(left: String, right: String, size: Float) {
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
            ensure(height)
            c.save(); c.translate(M, y + 0.5f); kl.draw(c); c.restore()
            c.save(); c.translate(M + 76, y); vl.draw(c); c.restore()
            y += maxOf(kl.height, vl.height) + 3f
        }

        fun rule() { y += 4f; c.drawLine(M, y, W - M, y, lp); y += 10f }
        fun gap(h: Float) { y += h }

        fun section(title: String, right: Float = 0f) {
            ensure(28f)
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
            ensure(16f)
            tp.textSize = 8.5f; tp.typeface = medium; tp.color = SOFT
            head.forEachIndexed { i, h -> c.drawText(h, xs[i], y + 9f, tp) }
            y += 14f
            c.drawLine(M, y - 2f, W - M, y - 2f, lp)
            for ((cells, color) in rows) {
                val ls = cells.mapIndexed { i, s -> layout(s, 9f, if (i == 1) bold else regular, if (i <= 1) color else INK, ws[i].toInt().coerceAtLeast(20)) }
                val h = ls.maxOf { it.height } + 7f
                ensure(h)
                ls.forEachIndexed { i, l -> c.save(); c.translate(xs[i], y + 3f); l.draw(c); c.restore() }
                y += h
                c.drawLine(M, y - 1f, W - M, y - 1f, lp)
            }
            y += 4f
        }

        /** Actual anchors and separate numbered labels, shared with the on-screen map. */
        fun bodyDiagram(x: Float, top: Float, width: Float, pins: List<Pair<Int, String>>, art: (Boolean) -> android.graphics.Bitmap?) {
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
            ensure(h)
            bodyDiagram(M, y, width, pins, art)
            y += h + 6f
        }

        fun chart(daily: List<Int>, from: Long, to: Long) {
            if (daily.isEmpty()) return
            ensure(112f)
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

        fun entry(e: DoctorNote.Entry, art: (Boolean) -> android.graphics.Bitmap?) {
            val fields = listOfNotNull(e.site?.let { "Location" to it }, e.depth?.let { "Depth" to it }) + e.facts
            val map = e.pins.isNotEmpty()
            val right = if (map) 180f else 0f
            val width = (W-2*M-76-right).toInt()
            val height = maxOf(if (map) 166f else 0f, fields.sumOf { maxOf(layout(it.first, 9.5f, medium, SOFT, 70).height, layout(it.second, 10f, regular, INK, width).height) + 3 }.toFloat()) + 46f
            ensure(height.coerceAtMost(bottom-M))
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
