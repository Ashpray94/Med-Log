package com.suryaprakash.medlog.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.suryaprakash.medlog.data.DocLine
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.Note
import com.suryaprakash.medlog.importer.Ocr
import com.suryaprakash.medlog.importer.ReportAnalyzer
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Title
import com.suryaprakash.medlog.ui.Toggle
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.savedFeedback
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId

/**
 * "Add my old reports" (from MedLog v1). Photo or PDF → offline text reading → the person picks what to keep.
 * Only lines that are in the report are kept; nothing is rewritten.
 */
@Composable
fun ImportScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ReportAnalyzer.Result?>(null) }
    var source by remember { mutableStateOf("") }
    val keep = remember { mutableStateListOf<Int>() }
    var keepMeds by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<File?>(null) }

    fun read(uri: Uri, name: String) {
        busy = true; error = null
        scope.launch {
            runCatching { Ocr.lines(ctx, uri) }.onSuccess { lines ->
                val r = ReportAnalyzer.analyze(lines)
                result = r; source = name; keep.clear(); keep.addAll(r.findings.indices)
                if (r.findings.isEmpty() && r.medicines.isEmpty()) error = "I couldn't find findings or medicines in this. The text is still saved for search."
            }.onFailure { error = "Couldn't read that file." }
            busy = false
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) read(uri, uri.lastPathSegment?.substringAfterLast('/') ?: "Report") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> pending?.let { if (ok) read(Uri.fromFile(it), "Photo of report") } }

    Screen("Add my old reports", "Take a photo of an old report or prescription, or choose a PDF. MedLog reads it on this phone and adds the important lines to your history.", onHome = { nav.home() }, onBack = { nav.back() }) {
        val r = result
        if (r == null) {
            BigButton("Take a photo", icon = Icons.Rounded.CameraAlt, enabled = !busy, onClick = {
                val f = File(File(ctx.filesDir, "photos").apply { mkdirs() }, "report_${System.currentTimeMillis()}.jpg"); pending = f
                camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
            })
            BigButton("Choose a PDF or picture", tone = Tone.SECONDARY, icon = Icons.Rounded.Description, enabled = !busy, onClick = { pick.launch(arrayOf("application/pdf", "image/*")) })
            if (busy) Hint("Reading… this can take a minute for long reports.")
            error?.let { Card(border = p.amber) { Body(it) } }
            Hint("Nothing is uploaded. The reading happens on this phone.")
            return@Screen
        }
        r.patientName?.let { Hint("Name on report: $it") }
        if (r.findings.isNotEmpty()) {
            Title("Findings (${keep.size} of ${r.findings.size} chosen)")
            r.findings.forEachIndexed { i, f ->
                Toggle(f.content, i in keep, f.date?.toString()) { on -> if (on) keep.add(i) else keep.remove(i) }
            }
        }
        if (r.medicines.isNotEmpty()) {
            Title("Medicines on the report")
            r.medicines.forEach { Body("• $it") }
            Toggle("Keep these for the doctor page", keepMeds) { keepMeds = it }
            Hint("To get reminders, add them in Medicines.")
        }
        BigButton("Add to my history", tone = Tone.PRIMARY, onClick = {
            scope.launch {
                val zone = ZoneId.systemDefault()
                app.db.docLines().insertAll(r.lines.map { DocLine(source = source, content = it) })
                r.findings.filterIndexed { i, _ -> i in keep }.forEach { f ->
                    val at = f.date?.atTime(12, 0)?.atZone(zone)?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis()
                    app.db.notes().insert(Note(kind = Kind.IMPORTED, occurredAt = at, transcript = f.content, text = "From report: ${f.content}"))
                }
                if (keepMeds) r.medicines.forEach { m -> app.db.notes().insert(Note(kind = Kind.IMPORTED, occurredAt = System.currentTimeMillis(), transcript = m, text = "Medicine on report: $m")) }
                savedFeedback(ctx); result = null
                app.speaker.say("Saved to your history.")
            }
        })
        BigButton("Start again", tone = Tone.SECONDARY, onClick = { result = null })
    }
}

@Suppress("unused") private val keepField: @Composable () -> Unit = { BigField("", "", {}) }
