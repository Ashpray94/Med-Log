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
    private const val TEAL = 0xFF006B62.toInt()
    private data class Metric(val label: String, val value: String, val detail: String, val tone: Int = TEAL)

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

    /** Remove duplicated/internal answers from dated rows while retaining clinically useful observations. */
    internal fun includeEpisodeFact(label: String, value: String): Boolean {
        val l = label.lowercase(); val v = value.lowercase()
        if (!includeGlanceFact(label, value)) return false
        return when {
            l == "started" || l == "which side" -> false
            l == "medicinetaken" || l == "tookmedicine" || "tookmedicine:" in v -> false
            l == "diagnosed" && ("not seen" in v || "not known" in v) -> false
            else -> true
        }
    }

    /** Use the same ailment-localized crop as the in-app body picker, inferred from the recorded pin. */
    internal fun localizedView(pins: List<Pair<Int, String>>): com.suryaprakash.medlog.pictogram.View {
        val mark = BodyMarkers.layout(pins).firstOrNull() ?: return com.suryaprakash.medlog.pictogram.WHOLE
        val part = com.suryaprakash.medlog.pictogram.Body.parts.filter { it.back == mark.back }.minByOrNull {
            val dx = (mark.x - it.cx) / (it.rx + 3f); val dy = (mark.y - it.cy) / (it.ry + 3f); dx * dx + dy * dy
        }
        return com.suryaprakash.medlog.pictogram.viewFor(part?.region)
    }

    /** Plain words for every body marker, using the same hit areas as the in-app body picker. */
    internal fun locationLabels(pins: List<Pair<Int, String>>): List<String> = BodyMarkers.layout(pins).mapNotNull { mark ->
        com.suryaprakash.medlog.pictogram.Body.parts.filter { it.back == mark.back }.minByOrNull {
            val dx = (mark.x - it.cx) / (it.rx + 3f); val dy = (mark.y - it.cy) / (it.ry + 3f); dx * dx + dy * dy
        }?.label
    }.distinct()

    /** Match recorded depth words to the depth pictures already used by the interview. */
    internal fun depthSprite(depth: String): String = when {
        "bone" in depth.lowercase() || "joint" in depth.lowercase() -> "depth_bone"
        "muscle" in depth.lowercase() -> "depth_muscle"
        "under" in depth.lowercase() -> "depth_under"
        "deep" in depth.lowercase() || "inside" in depth.lowercase() -> "depth_deep"
        else -> "depth_skin"
    }

    /** Tissue depth is meaningful only for the musculoskeletal problems that ask this question. */
    internal fun reportDepth(problemId: String, depth: String?): String? = depth?.takeIf {
        problemId in setOf("back_pain", "neck_pain", "shoulder_pain", "arm_pain", "hip_pain", "knee_pain", "leg_pain", "foot_pain", "body_ache", "cramps", "joint_swelling")
    }

    fun write(ctx: Context, n: DoctorNote, nut: com.suryaprakash.medlog.nutrition.Nutrition.Report? = null): File {
        val doc = PdfDocument()
        ReportValidation.requireAccurate(n)
        val w = Writer(doc, n.period, n.footer)
        val hasNutrition = nut?.let { r -> r.days.any { it.logged } || r.feeds.isNotEmpty() } == true
        w.page()

        // Page 1: information a clinician needs before opening the detailed history.
        w.pair("Patient-reported health summary", n.period, 15f)
        w.text(n.patient, 11f, bold = true)
        // only what was recorded: no "none known" or "none recorded" rows
        if (n.allergies.isNotBlank()) w.kv("Allergies", n.allergies, RED)
        if (n.conditions.isNotBlank()) w.kv("Conditions", n.conditions)
        if (n.currentMeds.isNotBlank()) w.kv("Medicines", n.currentMeds)
        w.rule()

        val doseDone = n.medicines.sumOf { it.done }; val doseDue = n.medicines.sumOf { it.due }
        val metrics = mutableListOf(
            Metric("SYMPTOMS", "${n.symptoms.size}", "tracked in this period"),
            Metric("REPORTS", "${n.symptoms.sumOf { it.reportCount }}", "symptom reports"),
            Metric("LAST 24 HOURS", "${n.symptoms.sumOf { it.reports24h }}", "new reports", if (n.symptoms.sumOf { it.reports24h } > 0) AMBER else TEAL),
            Metric("MEDICINES", if (doseDue > 0) "$doseDone/$doseDue" else "—", "scheduled doses recorded", if (doseDue > 0 && doseDone * 100 / doseDue < 80) AMBER else TEAL),
        )
        nut?.weightChange?.let { metrics += Metric("WEIGHT CHANGE", "${if (it > 0) "+" else ""}${"%.1f".format(it)} kg", "in ${nut.weightDays} days", if (it < 0) RED else TEAL) }
        nut?.kcalPct?.let { metrics += Metric("LOGGED CALORIES", "$it%", "of estimated target", if (it < 60) RED else if (it < 85) AMBER else TEAL) }
        w.dashboard(metrics.take(6))

        val attention = buildList {
            nut?.findings?.filter { it.level == "RED" || it.level == "AMBER" }?.take(2)?.forEach { add(it.text to if (it.level == "RED") RED else AMBER) }
            n.symptoms.filter { (it.sevHigh ?: 0) >= 7 }.take(2).forEach { add("${it.name}: severity reached ${it.sevHigh}/10" to AMBER) }
        }.distinctBy { it.first }.take(3)
        if (attention.isNotEmpty()) { w.section("Needs attention"); w.callouts(attention) }
        if (n.questions.isNotEmpty()) {
            w.section("Discuss with doctor")
            w.callouts(n.questions.map { "Question: $it" to AMBER })
        }
        if (n.symptoms.isNotEmpty()) {
            w.section("Symptom overview")
            w.table(listOf("Symptom", "Reports", "Severity", "Last noted"), floatArrayOf(0.30f, 0.12f, 0.18f, 0.40f),
                n.symptoms.map { r -> listOf("${r.n}. ${r.name}", "${r.reportCount}",
                    r.sevLow?.let { "${it}–${r.sevHigh ?: it}/10" }.orEmpty(), r.lastNoted.orEmpty()) to
                    when (r.urgent) { "RED" -> RED; "AMBER" -> AMBER; else -> INK } })
        }
        if (n.readings.isNotEmpty()) { w.section("Latest readings"); n.readings.forEach { w.text(it, 10f) } }

        // Pages 2–3: each recurring issue at a glance, while preserving what changed between reports.
        // Frequency charts share one scale so their line heights can be compared across ailments.
        val frequencyScale = maxOf(10, n.symptoms.flatMap { it.daily }.maxOrNull() ?: 0)
        fun issueDetails(rows: List<DoctorNote.Row>) {
            rows.forEach { r ->
                val reports = n.entries.filter { it.symptomNumber == r.n && it.noted }
                // Keep a heading with its visual instead of orphaning it at the foot of a page.
                if (!w.reserve(if (reports.size >= 5) 240f else 215f, lastPage = 3)) return@forEach
                w.section("${r.n}. ${r.name}")
                w.text(listOfNotNull("${r.reportCount} report${if (r.reportCount == 1) "" else "s"}",
                    r.sevLow?.let { "severity ${it}–${r.sevHigh ?: it}/10; latest ${r.sevLast ?: it}/10" },
                    r.began?.let { "started $it" }, r.lastNoted?.let { "last $it" }, r.trend?.let { "reported $it" })
                    .joinToString(" · "), 9.5f, bold = true,
                    color = when (r.urgent) { "RED" -> RED; "AMBER" -> AMBER; else -> INK })
                val severities = reports.mapNotNull { e -> e.facts.firstNotNullOfOrNull { (_, value) ->
                    Regex("(\\d+)\\s*out of 10", RegexOption.IGNORE_CASE).find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
                } }
                if (reports.isNotEmpty()) {
                    val allRows = reports.map { e ->
                        val full = (listOfNotNull(e.site, reportDepth(r.problemId, e.depth)) + e.facts.filter { includeEpisodeFact(it.first, it.second) }
                            .take(7).map { "${it.first}: ${it.second}" } + listOfNotNull(e.remark))
                            .filter { it.isNotBlank() }.distinct().joinToString("; ")
                        val details = if (full.length <= 240) full else full.take(237).substringBeforeLast(' ') + "…"
                        e.date to details.ifBlank { "Symptom reported" }
                    }
                    val rows = allRows.takeLast(3)
                    val earlier = (allRows.size - rows.size).takeIf { it > 0 }?.let { "$it earlier dated report${if (it == 1) "" else "s"} remain in MedLog." }
                    val pins = reports.flatMap { e -> e.pins.map { r.n to it } }.distinct()
                    val depths = reports.mapNotNull { reportDepth(r.problemId, it.depth) }.distinct()
                    val recordedLocations = reports.mapNotNull { it.site?.takeIf(String::isNotBlank) }.distinct()
                    val bodyView = localizedView(pins)
                    val art = { back: Boolean -> com.suryaprakash.medlog.pictogram.BodyArt.bitmap(ctx, back, bodyView, 500) }
                    val depthArt = { depth: String -> com.suryaprakash.medlog.pictogram.Sprites.bitmap(ctx, depthSprite(depth), 160) }
                    if (severities.size >= 2) w.trendTable(severities, "Recorded severity", 10, rows, pins, recordedLocations, depths, bodyView, earlier, art, depthArt)
                    else w.trendTable(r.daily, "Report frequency", frequencyScale, rows, pins, recordedLocations, depths, bodyView,
                        listOfNotNull("Frequency does not prove recovery", earlier).joinToString(" · "), art, depthArt)
                }
                val updates = n.entries.count { it.symptomNumber == r.n && !it.noted }
                if (updates > 0) w.text("$updates improvement/update entr${if (updates == 1) "y" else "ies"} recorded separately.", 8.5f, color = SOFT)
            }
        }
        w.page()
        w.pair("Recurring issues — report detail", n.period, 15f)
        val recurring = n.symptoms.filter { it.reportCount > 1 }
        issueDetails(recurring.take(2))

        w.page()
        w.pair("Other recurring issues and treatment", n.period, 15f)
        issueDetails(recurring.drop(2))
        if (!hasNutrition && n.medicines.isNotEmpty()) {
            w.section("Medicines in this period")
            w.table(listOf("Medicine", "Dose", "Taken", "Changes / notes"), floatArrayOf(0.25f, 0.22f, 0.12f, 0.41f),
                n.medicines.map { m -> listOf(m.name, m.dose, m.taken, m.change) to INK }, dropEmpty = true)
        }
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
            if (logged.isNotEmpty()) w.kv("Calories", if (r.feedNutritionUnknown > 0) "At least ${r.avgKcal.toInt()} kcal a day from entries with nutrition values" else r.kcalTarget?.let { "${r.avgKcal.toInt()} of ${it.toInt()} kcal a day (${r.kcalPct}%)" } ?: "${r.avgKcal.toInt()} kcal a day")
            if (logged.isNotEmpty()) w.kv("Protein", if (r.feedNutritionUnknown > 0) "At least ${r.avgProtein.toInt()} g a day from entries with nutrition values" else r.proteinTarget?.let { "${r.avgProtein.toInt()} of ${it.toInt()} g a day (${r.proteinPct}%)" } ?: "${r.avgProtein.toInt()} g a day")
            r.weightChange?.let { w.kv("Weight", "${"%.1f".format(r.weights.first().second)} → ${"%.1f".format(r.weights.last().second)} kg in ${r.weightDays} days") }
            if (logged.isNotEmpty()) w.kv("Targets", if (r.targetsFromDoctor) "Set by the doctor" else "30 kcal and 1 g protein per kg (to be confirmed)")
            if (r.feeds.isNotEmpty()) w.kv("Feeds", r.feeds.joinToString("; "))
            if (r.feedCounts.total > 0) w.kv("Feed outcomes", r.feedCounts.words())
            w.rule()
            if (logged.isNotEmpty()) {
                w.section("Recent logged days")
                w.table(listOf("Day", "kcal", "Protein", "Water", "What went in"), floatArrayOf(0.14f, 0.09f, 0.1f, 0.08f, 0.59f),
                    logged.takeLast(5).reversed().map { d -> listOf(d.date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")), "${d.kcal.toInt()}",
                        "${d.protein.toInt()} g", if (d.water > 0) "${d.water}" else "", d.items.joinToString(", ").take(110)) to
                        (r.kcalTarget?.let { t -> if (d.kcal < t * 0.6) RED else if (d.kcal < t * 0.85) AMBER else INK } ?: INK) }, dropEmpty = true)
                if (logged.size > 5) w.text("${logged.size - 5} earlier logged days remain available in MedLog.", 9f, color = SOFT)
            }
            if (n.medicines.isNotEmpty()) {
                w.section("Medicines in this period")
                w.table(listOf("Medicine", "Dose", "Taken", "Changes / notes"), floatArrayOf(0.25f, 0.22f, 0.12f, 0.41f),
                    n.medicines.map { m -> listOf(m.name, m.dose, m.taken, m.change) to INK }, dropEmpty = true)
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
        fun reserve(h: Float, lastPage: Int): Boolean {
            if (y + h <= bottom) return true
            if (n >= lastPage) return false
            return ensure(h)
        }

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

        fun dashboard(metrics: List<Metric>) {
            if (metrics.isEmpty()) return
            val cols = 3; val gap = 8f; val tileW = (W - 2 * M - gap * (cols - 1)) / cols; val tileH = 58f
            val rows = (metrics.size + cols - 1) / cols
            if (!ensure(rows * (tileH + gap))) return
            metrics.forEachIndexed { i, m ->
                val x = M + (i % cols) * (tileW + gap); val top = y + (i / cols) * (tileH + gap)
                val bg = when (m.tone) { RED -> 0xFFFFE9E6.toInt(); AMBER -> 0xFFFFF1D6.toInt(); else -> 0xFFE5F3F1.toInt() }
                c.drawRoundRect(RectF(x, top, x + tileW, top + tileH), 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
                tp.typeface = medium; tp.textSize = 7.5f; tp.color = m.tone; tp.letterSpacing = 0.05f
                c.drawText(m.label, x + 10f, top + 13f, tp); tp.letterSpacing = 0f
                tp.typeface = bold; tp.textSize = 16f; tp.color = INK; c.drawText(m.value, x + 10f, top + 34f, tp)
                val detail = layout(m.detail, 7.5f, regular, SOFT, (tileW - 20f).toInt())
                c.save(); c.translate(x + 10f, top + 40f); detail.draw(c); c.restore()
            }
            y += rows * (tileH + gap) + 2f
        }

        fun callouts(items: List<Pair<String, Int>>) {
            items.forEach { (words, tone) ->
                val l = layout(words, 9.5f, bold, INK, (W - 2 * M - 28f).toInt()); val h = maxOf(30f, l.height + 12f)
                if (!ensure(h + 5f)) return
                val bg = if (tone == RED) 0xFFFFE9E6.toInt() else 0xFFFFF1D6.toInt()
                c.drawRoundRect(RectF(M, y, W - M, y + h), 7f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
                c.drawRoundRect(RectF(M, y, M + 5f, y + h), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tone })
                c.save(); c.translate(M + 16f, y + 6f); l.draw(c); c.restore(); y += h + 5f
            }
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
        fun bodyDiagram(x: Float, top: Float, width: Float, pins: List<Pair<Int, String>>, art: (Boolean) -> android.graphics.Bitmap?,
            view: com.suryaprakash.medlog.pictogram.View = com.suryaprakash.medlog.pictogram.WHOLE) {
            if (full) return
            val marks = BodyMarkers.layout(pins)
            val fig = width / 2f - 4f
            val vw = view.x1 - view.x0; val vh = view.y1 - view.y0; val u = fig / vw
            val figH = vh * u
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            for ((k, back) in listOf(0 to false, 1 to true)) {
                val ox = x + k * (fig + 8f)
                art(back)?.let { c.drawBitmap(it, null, RectF(ox, top, ox + fig, top + figH), paint) }
                tp.textSize = 8f; tp.typeface = regular; tp.color = SOFT
                val lbl = if (back) "Back" else "Front"
                c.drawText(lbl, ox + (fig - tp.measureText(lbl)) / 2, top + figH + 12f, tp)
                for (mark in marks.filter { it.back == back && it.x in view.x0..view.x1 && it.y in view.y0..view.y1 }) {
                    if (mark.all) art(back)?.let { a -> c.drawBitmap(a, null, RectF(ox, top, ox + fig, top + figH), Paint(paint).apply {
                        colorFilter = android.graphics.PorterDuffColorFilter(AMBER, android.graphics.PorterDuff.Mode.SRC_IN); alpha = 70
                    }) }
                    val ax = ox + (mark.x - view.x0) * u; val ay = top + (mark.y - view.y0) * u
                    val lx = ox + (mark.labelX.coerceIn(view.x0 + 7f, view.x1 - 7f) - view.x0) * u
                    val ly = top + (mark.labelY.coerceIn(view.y0 + 7f, view.y1 - 7f) - view.y0) * u
                    paint.color = RED; paint.strokeWidth = 1.2f
                    c.drawLine(ax, ay, lx, ly, paint)
                    val anchorR = maxOf(4.5f, 4.5f * u)
                    paint.color = 0x33B3261E; c.drawCircle(ax, ay, anchorR * 1.8f, paint)
                    paint.color = RED; c.drawCircle(ax, ay, anchorR, paint)
                    paint.color = Color.WHITE; c.drawCircle(ax, ay, anchorR * 0.34f, paint)
                    val labelR = maxOf(7f, 8.5f * u)
                    paint.color = Color.WHITE; c.drawCircle(lx, ly, labelR + 1.5f, paint)
                    paint.color = RED; c.drawCircle(lx, ly, labelR, paint)
                    tp.textSize = maxOf(8f, 10f * u); tp.typeface = bold; tp.color = Color.WHITE
                    val t = "${mark.number}"; c.drawText(t, lx - tp.measureText(t)/2, ly + tp.textSize * 0.34f, tp)
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
            val top = y; val height = 24f; val left = M + 18f; val width = W - M - left
            val maximum = (fixedMax ?: values.maxOrNull() ?: 1).coerceAtLeast(1)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER; strokeWidth = 2f; style = Paint.Style.STROKE }
            val point = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER; style = Paint.Style.FILL }
            c.drawLine(left, top, W - M, top, lp)
            c.drawLine(left, top + height / 2f, W - M, top + height / 2f, lp)
            c.drawLine(left, top + height, W - M, top + height, lp)
            tp.textSize = 7f; tp.typeface = regular; tp.color = SOFT
            c.drawText("$maximum", M, top + 5f, tp); c.drawText("0", M, top + height, tp)
            val points = values.mapIndexed { i, value ->
                val x = if (values.size == 1) left + width / 2f else left + width * i / (values.size - 1)
                val py = top + height - height * value.coerceIn(0, maximum) / maximum
                x to py
            }
            for (i in 1 until points.size) {
                c.drawLine(points[i - 1].first, points[i - 1].second, points[i].first, points[i].second, line)
            }
            points.forEach { (x, py) -> c.drawCircle(x, py, 2.7f, point) }
            y = top + height + 3f
            tp.textSize = 7.5f; tp.typeface = regular; tp.color = SOFT
            c.drawText("Oldest", left, y + 7f, tp)
            val latest = "Latest"; c.drawText(latest, W - M - tp.measureText(latest), y + 7f, tp)
            y += 12f
        }

        /** Large visual trend and body/depth map beside the exact episode rows. */
        fun trendTable(values: List<Int>, label: String, fixedMax: Int?, rows: List<Pair<String, String>>,
            pins: List<Pair<Int, String>>, recordedLocations: List<String>, depths: List<String>, bodyView: com.suryaprakash.medlog.pictogram.View,
            note: String? = null,
            art: (Boolean) -> android.graphics.Bitmap?, depthArt: (String) -> android.graphics.Bitmap? = { null }) {
            val leftW = 158f; val gap = 12f; val tableX = M + leftW + gap; val tableW = W - M - tableX
            val dateW = 86f; val detailW = tableW - dateW - 8f
            val layouts = rows.map { (date, detail) ->
                layout(date, 8f, regular, INK, (dateW - 5f).toInt()) to layout(detail, 8f, regular, INK, detailW.toInt())
            }
            val tableH = 18f + layouts.sumOf { maxOf(it.first.height, it.second.height) + 7 }.toFloat()
            val noteLayout = note?.let { layout(it, 7f, regular, SOFT, leftW.toInt()) }
            val bodyTopOffset = if (noteLayout != null) 104f else 86f
            val locations = recordedLocations.ifEmpty { locationLabels(pins) }
            val locationWords = when (locations.size) { 0 -> null; 1 -> locations.single(); else -> "Varies by dated report; see table" }
            val locationLayout = locationWords?.let { layout("Location: $it", 7.5f, medium, INK, leftW.toInt()) }
            val variedDepthLayout = depths.takeIf { it.size > 1 }?.let { layout("Depth: Varies by dated report; see table", 7f, medium, INK, leftW.toInt()) }
            val figureWidth = 92f / 2f - 4f
            val figureHeight = (bodyView.y1 - bodyView.y0) * (figureWidth / (bodyView.x1 - bodyView.x0))
            val depthHeight = when { depths.size == 1 -> 22f; variedDepthLayout != null -> variedDepthLayout.height.toFloat(); else -> 0f }
            val detailHeight = (locationLayout?.height?.toFloat() ?: 0f) + (if (locationLayout != null && depthHeight > 0f) 5f else 0f) + depthHeight
            val visualH = if (pins.isNotEmpty()) bodyTopOffset + figureHeight + 20f + detailHeight else 94f
            val h = maxOf(tableH, visualH)
            if (!ensure(h + 8f)) return
            val top = y
            tp.textSize = 8.5f; tp.typeface = bold; tp.color = SOFT; c.drawText(label, M, top + 10f, tp)
            val plotTop = top + 18f; val plotH = 52f; val graphLeft = M + 16f; val graphW = leftW - 18f
            val maximum = (fixedMax ?: values.maxOrNull() ?: 1).coerceAtLeast(1)
            c.drawRoundRect(RectF(M - 5f, plotTop - 6f, M + leftW, plotTop + plotH + 17f), 7f, 7f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7F6F3.toInt() })
            val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE4E0D9.toInt(); strokeWidth = 0.55f }
            for (step in 0..10) {
                val gy = plotTop + plotH * step / 10f
                c.drawLine(graphLeft, gy, graphLeft + graphW, gy, grid)
            }
            tp.textSize = 7f; tp.typeface = regular; tp.color = SOFT
            c.drawText("$maximum", M, plotTop + 5f, tp)
            c.drawText("${maximum / 2f}".removeSuffix(".0"), M, plotTop + plotH / 2f + 3f, tp)
            c.drawText("0", M, plotTop + plotH, tp)
            val pts = values.mapIndexed { i, v ->
                (if (values.size == 1) graphLeft + graphW / 2 else graphLeft + graphW * i / (values.size - 1)) to
                    (plotTop + plotH - plotH * v.coerceIn(0, maximum) / maximum)
            }
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER; strokeWidth = 2.2f; style = Paint.Style.STROKE }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER; style = Paint.Style.FILL }
            for (i in 1 until pts.size) c.drawLine(pts[i-1].first, pts[i-1].second, pts[i].first, pts[i].second, line)
            pts.forEach { c.drawCircle(it.first, it.second, 3f, dot) }
            tp.textSize = 7f; tp.color = SOFT; c.drawText("Oldest", graphLeft, plotTop + plotH + 10f, tp)
            val latest = "Latest"; c.drawText(latest, graphLeft + graphW - tp.measureText(latest), plotTop + plotH + 10f, tp)
            noteLayout?.let { l -> c.save(); c.translate(M, plotTop + plotH + 16f); l.draw(c); c.restore() }
            if (pins.isNotEmpty()) {
                val bodyTop = top + bodyTopOffset
                bodyDiagram(M, bodyTop, 92f, pins, art, bodyView)
                var detailTop = bodyTop + figureHeight + 20f
                locationLayout?.let { l ->
                    c.save(); c.translate(M, detailTop); l.draw(c); c.restore()
                    detailTop += l.height + 5f
                }
                if (depths.isNotEmpty()) {
                    val shown = if (depths.size == 1) depths else emptyList()
                    var dx = M; val dy = detailTop + 10f
                    shown.forEach { depth ->
                        depthArt(depth)?.let { bmp -> c.drawBitmap(bmp, null, RectF(dx, dy - 14f, dx + 18f, dy + 4f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
                        val caption = layout(depth, 7f, regular, SOFT, 42)
                        c.save(); c.translate(dx + 20f, dy - 10f); caption.draw(c); c.restore()
                        dx += 51f
                    }
                    variedDepthLayout?.let { l ->
                        c.save(); c.translate(M, detailTop); l.draw(c); c.restore()
                    }
                }
            }
            c.drawRect(tableX, top, tableX + tableW, top + 17f, Paint().apply { color = 0xFFF3F1ED.toInt() })
            tp.textSize = 8f; tp.typeface = medium; tp.color = SOFT; c.drawText("Date", tableX + 3f, top + 11f, tp); c.drawText("Specific recorded details", tableX + dateW, top + 11f, tp)
            var ty = top + 18f
            layouts.forEach { (dl, xl) ->
                val rh = maxOf(dl.height, xl.height) + 7f
                c.save(); c.translate(tableX + 3f, ty + 3f); dl.draw(c); c.restore()
                c.save(); c.translate(tableX + dateW, ty + 3f); xl.draw(c); c.restore()
                ty += rh; c.drawLine(tableX, ty - 1f, tableX + tableW, ty - 1f, lp)
            }
            y = top + h + 8f
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
