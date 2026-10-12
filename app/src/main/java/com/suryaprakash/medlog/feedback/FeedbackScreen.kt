package com.suryaprakash.medlog.feedback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Chip
import com.suryaprakash.medlog.ui.FlowRowOf
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalSettings
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Section
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.Toggle
import com.suryaprakash.medlog.ui.rememberDictation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Says what is going to happen to the report, in plain words. */
private fun savedLine(): String =
    if (FeedbackSender.configured) "Saved on this phone. It is sent when there is internet."
    else "Saved on this phone. It will be sent when the app is set up to send reports."

/** The report page: the screenshot with a red pen on it, a kind of problem, and words. */
@Composable
fun FeedbackScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val bitmap = remember { FeedbackDraft.bitmap }
    val meta = remember { FeedbackDraft.meta ?: Capture.meta(ctx, nav) }
    val strokes = remember { mutableStateListOf<List<Pair<Float, Float>>>() }
    var category by remember { mutableStateOf(Category.BUG) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sendPicture by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf<String?>(null) }
    val dictate = rememberDictation("Say what went wrong") { note = (note + " " + it).trim() }
    val helper = LocalSettings.current.role == "helper"
    // the picture is used once; drop it when leaving so memory is freed
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { FeedbackDraft.clear() } }

    Screen("Report a problem", "Draw on the picture if you like, choose what kind it is, and say what happened.",
        onHome = if (helper) null else ({ nav.home() }), onBack = { nav.back() }) {
        if (done != null) {
            Card { Body(done!!, bold = true) }
            BigButton("See my reports", tone = Tone.SECONDARY, onClick = { nav.replace(Route.MyReports) })
            BigButton("Done", onClick = { nav.back() })
            return@Screen
        }
        if (bitmap != null) {
            Hint("Draw with your finger to circle the problem.")
            ShotPad(bitmap, strokes)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Undo", Modifier.weight(1f), tone = Tone.SECONDARY, enabled = strokes.isNotEmpty(), onClick = { strokes.removeAt(strokes.lastIndex) })
                BigButton("Clear", Modifier.weight(1f), tone = Tone.SECONDARY, enabled = strokes.isNotEmpty(), onClick = { strokes.clear() })
            }
        }
        Section("What kind of problem?")
        FlowRowOf { Category.entries.forEach { c -> Chip(c.label, category == c) { category = c } } }
        Section("What happened?")
        BigField("Your words", note, { note = it }, lines = 4)
        if (dictate != null) BigButton("Speak", tone = Tone.SECONDARY, icon = Icons.Rounded.Mic, onClick = dictate)
        if (bitmap != null) Toggle("Send the picture", sendPicture) { sendPicture = it }
        Hint("The picture can show health details. It is sent only if the tracker is private; otherwise only your words are sent.")
        BigButton("Send", icon = Icons.Rounded.Send, enabled = !busy && (note.isNotBlank() || (bitmap != null && sendPicture)), onClick = {
            busy = true
            scope.launch {
                val id = "${meta.time}-${(1000..9999).random()}"
                val jpeg = if (bitmap != null && sendPicture) withContext(Dispatchers.Default) { Capture.compose(bitmap, strokes.toList()) } else null
                val r = Report(id, meta.time, category, note.trim(), meta.route, meta.version, meta.role, meta.device, meta.android, hasShot = jpeg != null)
                withContext(Dispatchers.IO) { FeedbackStore(ctx).save(r, jpeg) }
                if (FeedbackSender.configured) FeedbackWorker.enqueue(ctx)
                app.speaker.say("Saved")
                done = savedLine()
                busy = false
            }
        })
    }
}

/** The screenshot at full width with the pen on top. Points are kept as fractions so any size works. */
@Composable
private fun ShotPad(bitmap: android.graphics.Bitmap, strokes: androidx.compose.runtime.snapshots.SnapshotStateList<List<Pair<Float, Float>>>) {
    val p = LocalPalette.current
    var live by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height).clip(RoundedCornerShape(12.dp)).border(1.dp, p.line, RoundedCornerShape(12.dp)),
    ) {
        Image(bitmap.asImageBitmap(), contentDescription = "Picture of the page", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
        Canvas(Modifier.matchParentSize().pointerInput(Unit) {
            fun frac(o: Offset) = Pair((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f))
            detectDragGestures(
                onDragStart = { live = listOf(frac(it)) },
                onDrag = { change, _ -> change.consume(); live = live + frac(change.position) },
                onDragEnd = { if (live.isNotEmpty()) strokes.add(live); live = emptyList() },
                onDragCancel = { live = emptyList() },
            )
        }) {
            val w = size.width * 0.008f
            fun draw(s: List<Pair<Float, Float>>) {
                if (s.size < 2) return
                val path = Path().apply {
                    moveTo(s[0].first * size.width, s[0].second * size.height)
                    for (q in s.drop(1)) lineTo(q.first * size.width, q.second * size.height)
                }
                drawPath(path, Color.Red, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            strokes.forEach { draw(it) }
            draw(live)
        }
    }
}

/** Plain words for where a report is. */
fun statusLine(r: Report): String = when (r.status) {
    Status.QUEUED -> "Waiting to send"
    Status.SENT -> "Sent, the team will look at it"
    Status.CLOSED -> "The team says it's fixed"
    Status.CLOSED_NO_FIX -> "Closed without a fix"
    Status.VERIFIED -> "You checked it works"
}

/** Opens the Android share sheet with the words and details, and the picture if there is one. */
private fun shareReport(ctx: android.content.Context, r: Report, store: FeedbackStore) {
    val text = Github.shareText(r)
    val shot = store.shotFile(r.id).takeIf { r.hasShot && it.exists() }
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).putExtra(android.content.Intent.EXTRA_TEXT, text)
        .putExtra(android.content.Intent.EXTRA_SUBJECT, "MedLog problem report")
    if (shot != null) {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", shot)
        send.setType("image/jpeg").putExtra(android.content.Intent.EXTRA_STREAM, uri)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newRawUri("", uri)
    } else send.setType("text/plain")
    ctx.startActivity(android.content.Intent.createChooser(send, "Share this report").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Every report with its status. A fixed one can be confirmed ("It works now") or reopened ("Still broken"). */
@Composable
fun MyReportsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val store = remember { FeedbackStore(ctx) }
    var reports by remember { mutableStateOf<List<Report>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Report?>(null) }
    var why by remember { mutableStateOf("") }
    val helper = LocalSettings.current.role == "helper"

    // Saves what the sender changed and shows it. Runs off the main thread; a missing connection is not an error.
    fun work(change: (FeedbackSender, Report) -> Report, only: Report? = null) {
        if (busy) return
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) {
                if (FeedbackSender.configured) runCatching {
                    val sender = FeedbackSender.forApp()
                    (only?.let { listOf(it) } ?: store.all()).forEach { r -> store.save(change(sender, r)) }
                }
                reports = store.all()
            }
            busy = false
        }
    }
    LaunchedEffect(Unit) {
        reports = withContext(Dispatchers.IO) { store.all() }
        // ask GitHub how the sent ones are doing
        work({ s, r -> if (r.issue > 0) s.refresh(r) else r })
    }

    Screen("My reports", "The problems you told us about, and what happened to them.", onHome = if (helper) null else ({ nav.home() }), onBack = { nav.back() }) {
        if (!FeedbackSender.configured) Card(border = LocalPalette.current.amber) { Body("This version of the app can't send reports by itself.", bold = true) }
        if (reports.isEmpty()) Body("No reports yet. Shake the phone, or use Report a problem in Settings.")
        reports.forEach { r ->
            Card {
                Body(r.category.label, bold = true)
                if (r.note.isNotBlank()) Body(r.note.take(140))
                Hint(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(r.createdAt)))
                Body(statusLine(r), bold = true)
                if (r.issue > 0) Hint("#${r.issue}")
                if (r.hasShot && r.textOnly) Hint("Sent without the picture")
                if (r.status == Status.CLOSED) {
                    BigButton("It works now", onClick = { work({ s, x -> s.verify(x) }, r) })
                    BigButton("Still broken", tone = Tone.SECONDARY, onClick = { problem = r; why = "" })
                }
                if (r.status == Status.QUEUED && !FeedbackSender.configured) BigButton("Share it", tone = Tone.SECONDARY, onClick = { runCatching { shareReport(ctx, r, store) } })
                if (r.status == Status.QUEUED && FeedbackSender.configured) BigButton("Send now", tone = Tone.SECONDARY, onClick = { FeedbackWorker.enqueue(ctx); app.speaker.say("Sending") })
            }
        }
    }
    problem?.let { r ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { problem = null }) {
            Card(color = LocalPalette.current.paper) {
                Body("What is still wrong? (You can leave this empty.)", bold = true)
                BigField("Your words", why, { why = it }, lines = 3)
                BigButton("Send", onClick = { val note = why; problem = null; work({ s, x -> s.stillBroken(x, note) }, r) })
                BigButton("Cancel", tone = Tone.SECONDARY, onClick = { problem = null })
            }
        }
    }
}
