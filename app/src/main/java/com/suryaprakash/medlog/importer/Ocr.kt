package com.suryaprakash.medlog.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Offline text reading (ML Kit with its model bundled inside the app; nothing is uploaded). */
object Ocr {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private suspend fun recognise(img: InputImage): Text = suspendCancellableCoroutine { c ->
        client.process(img).addOnSuccessListener { c.resume(it) }.addOnFailureListener { c.resumeWithException(it) }
    }

    /** Lines, top to bottom. */
    suspend fun lines(ctx: Context, uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val mime = ctx.contentResolver.getType(uri) ?: ""
        if (mime == "application/pdf" || uri.toString().endsWith(".pdf", true)) pdfLines(ctx, uri)
        else ordered(recognise(InputImage.fromFilePath(ctx, uri)))
    }

    private fun ordered(t: Text): List<String> =
        t.textBlocks.flatMap { it.lines }.sortedWith(compareBy({ (it.boundingBox?.top ?: 0) / 12 }, { it.boundingBox?.left ?: 0 })).map { it.text }

    private suspend fun pdfLines(ctx: Context, uri: Uri): List<String> {
        val out = ArrayList<String>()
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            PdfRenderer(fd).use { r ->
                for (i in 0 until minOf(r.pageCount, 30)) {
                    r.openPage(i).use { page ->
                        val scale = 2
                        val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out += ordered(recognise(InputImage.fromBitmap(bmp, 0)))
                        out += "-- ${i + 1} of ${r.pageCount} --"
                        bmp.recycle()
                    }
                }
            }
        }
        return out
    }

    data class MedGuess(val name: String?, val strength: String?, val candidates: List<String>)

    private val NOT_NAMES = Regex("\\b(tablets?|capsules?|ip|usp|bp|each|film coated|contains|mfd|mfg|batch|exp|price|rs|schedule|warning|store|keep|dosage|manufactured|marketed|pharma|ltd|limited|pvt|rx|only|strip|of)\\b", RegexOption.IGNORE_CASE)

    /** Guesses the medicine name (biggest words on the strip) and strength ("500 mg"). */
    suspend fun medicineGuess(ctx: Context, uri: Uri): MedGuess = withContext(Dispatchers.IO) {
        val t = recognise(InputImage.fromFilePath(ctx, uri))
        val lines = t.textBlocks.flatMap { it.lines }
        val strength = lines.firstNotNullOfOrNull { Regex("\\b(\\d+(?:\\.\\d+)?)\\s*(mg|mcg|g|ml|iu)\\b", RegexOption.IGNORE_CASE).find(it.text)?.value }
        val candidates = lines
            .filter { l -> l.text.count(Char::isLetter) >= 3 && l.text.length <= 30 && NOT_NAMES.findAll(l.text).count() < 2 }
            .sortedByDescending { it.boundingBox?.height() ?: 0 }
            .map { l -> l.text.replace(Regex("\\b(\\d+(?:\\.\\d+)?)\\s*(mg|mcg|g|ml|iu)\\b", RegexOption.IGNORE_CASE), "").replace(NOT_NAMES, "").trim().trim('-', ',', '.') }
            .filter { it.length >= 3 }
            .distinct()
        MedGuess(candidates.firstOrNull()?.let { tidy(it) }, strength?.replace(Regex("\\s+"), " "), candidates.map { tidy(it) }.take(8))
    }

    private fun tidy(s: String) = s.lowercase().split(" ").filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
