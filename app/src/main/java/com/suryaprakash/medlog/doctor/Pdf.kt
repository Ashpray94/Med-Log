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
        val w = Writer(doc)
        w.page()

        // ── heading: who, and what they're known to have; only what was recorded ──
        w.pair("Health summary", n.period, 15f)
        w.text(n.patient, 11f, bold = true)
        if (n.allergies.isNotBlank()) w.kv("Allergies", n.allergies, RED)
        if (n.conditions.isNotBlank()) w.kv("Conditions", n.conditions)
        if (n.currentMeds.isNotBlank()) w.kv("Medicines", n.currentMeds)
        w.rule()

        // ── main concerns, with the body diagram beside them ──
        val diagramW = 150f
        val top = w.y
        if (n.concerns.isNotEmpty()) {
            w.section("Main concerns", right = diagramW + 12f)
            n.concerns.forEachIndexed { i, c -> w.text("${i + 1}.  $c", 10.5f, bold = i == 0, right = diagramW + 12f) }
        }
        if (n.pins.isNotEmpty()) {
            w.bodyDiagram(W - M - diagramW, top + 2f, diagramW, n.pins) { back -> com.suryaprakash.medlog.pictogram.BodyArt.bitmap(ctx, back, com.suryaprakash.medlog.pictogram.WHOLE, 400) }
            w.y = maxOf(w.y, top + diagramW * 1.05f)
        }
        w.gap(4f)

        // ── symptoms: one short entry each, in plain words; a line only where something was noted ──
        if (n.symptoms.isNotEmpty()) {
            w.section("Symptoms reported")
            n.symptoms.forEach { r ->
                val tag = when (r.urgent) { "RED" -> "  (urgent)"; "AMBER" -> "  (to watch)"; else -> "" }
                w.text("${r.n}.  ${r.name}$tag", 11f, bold = true, color = if (r.urgent == "RED") RED else if (r.urgent == "AMBER") AMBER else INK)
                w.kv("When", r.whenText, indent = 16f)
                if (r.where.isNotBlank()) w.kv("Where", r.where.replaceFirstChar(Char::uppercase), indent = 16f)
                if (r.nature.isNotBlank()) w.kv("Details", r.nature.replaceFirstChar(Char::uppercase), indent = 16f)
                if (r.notes.isNotBlank()) w.kv("Also", r.notes.replaceFirstChar(Char::uppercase), indent = 16f)
                w.gap(3f)
            }
        }
        // ── medicines: each as a sentence; how many were taken only where doses were due ──
        if (n.medicines.isNotEmpty()) {
            w.section("Medicines in this period")
            n.medicines.forEach { m ->
                w.text("${m.name}${if (m.dose.isNotBlank()) "  ·  ${m.dose}" else ""}", 10.5f, bold = true)
                val line = listOf(m.taken, m.change).filter { it.isNotBlank() }.joinToString("; ")
                if (line.isNotBlank()) w.text(line.replaceFirstChar(Char::uppercase), 10f, color = SOFT, indent = 16f)
            }
        }
        if (n.readings.isNotEmpty()) { w.section("Readings"); n.readings.forEach { w.text(it, 10f) } }
        if (n.links.isNotEmpty()) { w.section("Timing noticed"); n.links.forEach { w.text(it, 10f, color = SOFT) } }
        if (n.questions.isNotEmpty()) { w.section("Patient's questions"); n.questions.forEach { w.text("•  $it", 10.5f) } }
        w.footer(n.footer)
        // ── nutrition: its own page, only when food or feeds were logged; days with nothing logged are left out ──
        nut?.takeIf { r -> r.days.any { it.logged } || r.feeds.isNotEmpty() || r.missed.isNotEmpty() }?.let { r ->
            w.page()
            w.pair("Nutrition and weight", n.period, 15f)
            val found = (listOf(r.headline) + r.findings.drop(1)).filter { !it.text.startsWith("Nothing logged") }
            found.firstOrNull()?.let { h -> w.text(h.text, 12.5f, bold = true, color = if (h.level == "RED") RED else if (h.level == "AMBER") AMBER else INK) }
            found.drop(1).forEach { f -> w.text("•  ${f.text}", 10.5f, color = if (f.level == "RED") RED else if (f.level == "AMBER") AMBER else INK) }
            w.gap(4f)
            val logged = r.days.filter { it.logged }
            if (logged.isNotEmpty()) {
                w.kv("Calories", r.kcalTarget?.let { "${r.avgKcal.toInt()} of ${it.toInt()} kcal a day (${r.kcalPct}%)" } ?: "${r.avgKcal.toInt()} kcal a day")
                w.kv("Protein", r.proteinTarget?.let { "${r.avgProtein.toInt()} of ${it.toInt()} g a day (${r.proteinPct}%)" } ?: "${r.avgProtein.toInt()} g a day")
                w.kv("Averaged over", "${logged.size} day${if (logged.size == 1) "" else "s"} with food logged")
            }
            r.weightChange?.let { w.kv("Weight", "${"%.1f".format(r.weights.first().second)} → ${"%.1f".format(r.weights.last().second)} kg in ${r.weightDays} days") }
            if (logged.isNotEmpty()) w.kv("Targets", if (r.targetsFromDoctor) "Set by the doctor" else "30 kcal and 1 g protein per kg (to be confirmed)")
            if (r.feeds.isNotEmpty()) w.kv("Feeds", r.feeds.joinToString("; "))
            w.rule()
            if (logged.isNotEmpty()) {
                w.section("Day by day")
                w.table(listOf("Day", "kcal", "Protein", "Water", "What was eaten or given"), floatArrayOf(0.14f, 0.09f, 0.1f, 0.08f, 0.59f),
                    logged.reversed().map { d -> listOf(d.date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")), "${d.kcal.toInt()}",
                        "${d.protein.toInt()} g", if (d.water > 0) "${d.water}" else "", d.items.joinToString(", ")) to
                        (r.kcalTarget?.let { t -> if (d.kcal < t * 0.6) RED else if (d.kcal < t * 0.85) AMBER else INK } ?: INK) })
            }
            if (r.missed.isNotEmpty()) { w.section("Missed feeds"); r.missed.sortedBy { it.at }.forEach { m -> w.text("${java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(m.at))}  ·  ${m.feed} ${m.ml.toInt()} ml", 10f) } }
            if (r.changes.isNotEmpty()) { w.section("What changed"); r.changes.forEach { w.text("•  $it", 10f) } }
            if (r.observed.isNotEmpty()) { w.section("Also noticed"); r.observed.forEach { w.text("•  $it", 10f) } }
            if (logged.isNotEmpty()) w.text("Food values are estimates for home cooking.", 9f, color = SOFT)
            w.footer(n.footer)
        }
        w.finish()

        val f = File(File(ctx.cacheDir, "share").apply { mkdirs() }, "Symptom-summary.pdf")
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        return f
    }

    private class Writer(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        lateinit var c: Canvas
        var y = M
        var n = 0
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val bottom = H - 40f
        val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
        val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LINE; strokeWidth = 0.8f }

        fun page() { finish(); n++; page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create()); c = page!!.canvas; y = M }
        fun finish() { page?.let { doc.finishPage(it) }; page = null }
        private fun ensure(h: Float) { if (y + h > bottom) page() }

        private fun layout(s: String, size: Float, tf: Typeface, color: Int, width: Int): StaticLayout {
            tp.textSize = size; tp.typeface = tf; tp.color = color
            return StaticLayout.Builder.obtain(s, 0, s.length, TextPaint(tp), width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(1f, 1.08f).build()
        }

        fun text(s: String, size: Float, bold: Boolean = false, color: Int = INK, right: Float = 0f, indent: Float = 0f) {
            val l = layout(s, size, if (bold) this.bold else regular, color, (W - 2 * M - right - indent).toInt())
            ensure(l.height.toFloat())
            c.save(); c.translate(M + indent, y); l.draw(c); c.restore()
            y += l.height + 3f
        }

        fun pair(left: String, right: String, size: Float) {
            tp.textSize = size; tp.typeface = bold; tp.color = INK
            c.drawText(left, M, y + size, tp)
            tp.textSize = 10f; tp.typeface = regular; tp.color = SOFT
            c.drawText(right, W - M - tp.measureText(right), y + size, tp)
            y += size + 10f
        }

        fun kv(k: String, v: String, color: Int = INK, indent: Float = 0f) {
            val kl = layout(k, 9.5f, medium, SOFT, 70)
            val vl = layout(v, 10f, if (color == RED) bold else regular, color, (W - 2 * M - 76 - indent).toInt())
            ensure(vl.height.toFloat())
            c.save(); c.translate(M + indent, y + 0.5f); kl.draw(c); c.restore()
            c.save(); c.translate(M + indent + 76, y); vl.draw(c); c.restore()
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

        fun table(head: List<String>, frac: FloatArray, rows: List<Pair<List<String>, Int>>) {
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

        /** Two small silhouettes (front, back) with numbered pins. */
        fun bodyDiagram(x: Float, top: Float, width: Float, pins: List<Pair<Int, String>>, art: (Boolean) -> android.graphics.Bitmap?) {
            val fig = width / 2f - 4f
            val u = fig / 100f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            for ((k, back) in listOf(0 to false, 1 to true)) {
                val ox = x + k * (fig + 8f)
                art(back)?.let { c.drawBitmap(it, null, RectF(ox, top, ox + 100f * u, top + 170f * u), paint) }
                tp.textSize = 7f; tp.typeface = regular; tp.color = SOFT
                val lbl = if (back) "Back" else "Front"
                c.drawText(lbl, ox + (fig - tp.measureText(lbl)) / 2, top + 170f * u + 8f, tp)
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED }
                for ((num, code) in pins) {
                    val isBack = code.startsWith("back")
                    if (isBack != back) continue
                    val all = code.endsWith(":all")
                    if (all) art(back)?.let { a ->
                        // the whole figure, tinted: "all over the body"
                        c.drawBitmap(a, null, RectF(ox, top, ox + 100f * u, top + 170f * u), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            colorFilter = android.graphics.PorterDuffColorFilter(RED, android.graphics.PorterDuff.Mode.SRC_IN); alpha = 90
                        })
                    }
                    val xy = if (all) listOf(50f, 62f) else code.substringAfter(":").split(",").mapNotNull { it.toFloatOrNull() }
                    if (xy.size != 2) continue
                    val (px, py) = xy[0] to xy[1]
                    val cx = ox + px * u; val cy = top + py * u
                    c.drawCircle(cx, cy, 5.5f, dot)
                    tp.textSize = 6.5f; tp.typeface = bold; tp.color = Color.WHITE
                    val t = "$num"; c.drawText(t, cx - tp.measureText(t) / 2, cy + 2.3f, tp)
                }
            }
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
                cb.onLayoutFinished(PrintDocumentInfo.Builder("Symptom-summary.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }
            override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
                runCatching { f.inputStream().use { input -> FileOutputStream(dest.fileDescriptor).use { input.copyTo(it) } }; cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
                    .onFailure { cb.onWriteFailed(it.message) }
            }
        }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }
}
