package com.suryaprakash.medlog.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.MedLogApp
import com.suryaprakash.medlog.care.FollowUp
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Interview
import com.suryaprakash.medlog.clinical.Interview.Ask
import com.suryaprakash.medlog.clinical.Interview.Kind
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.Problem
import com.suryaprakash.medlog.clinical.Triage
import com.suryaprakash.medlog.help.Alerts
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Mention
import com.suryaprakash.medlog.nlu.Normalize
import com.suryaprakash.medlog.nlu.Parsed
import com.suryaprakash.medlog.nlu.Source
import com.suryaprakash.medlog.nlu.factsFromJson
import com.suryaprakash.medlog.pictogram.Body
import com.suryaprakash.medlog.pictogram.BodyMap
import com.suryaprakash.medlog.pictogram.Pin
import com.suryaprakash.medlog.pictogram.SpriteIcon
import com.suryaprakash.medlog.speech.Listener
import com.suryaprakash.medlog.speech.Localize
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Chip
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.LocalSettings
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Perms
import com.suryaprakash.medlog.ui.ReadAloud
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Tile
import com.suryaprakash.medlog.ui.TileGrid
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.UndoHost
import com.suryaprakash.medlog.ui.rememberPermissionAsker
import com.suryaprakash.medlog.ui.savedFeedback
import com.suryaprakash.medlog.ui.steady
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Phase { OPEN, CONFIRM, PICK, ASK, SUMMARY, DANGER }

/**
 * "Tell how you feel" as a conversation:
 * one short question at a time, spoken and shown as large captions, with a live caption of the answer.
 * Answers save as they come. Core questions first; more only if the person wants to.
 */
@Composable
fun TellScreen(nav: Nav, route: Route.Tell) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val cat = app.catalogue
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    val lang = s.languages.firstOrNull() ?: "en-IN"

    var phase by remember { mutableStateOf(if (route.pick) Phase.PICK else if (route.problemId != null || route.noteId != null) Phase.ASK else Phase.OPEN) }
    var problem by remember { mutableStateOf(cat.problem(route.problemId)) }
    val facts = remember { mutableStateMapOf<String, Fact>() }
    val queue = remember { mutableStateListOf<Ask>() }
    var index by remember { mutableStateOf(0) }
    var noteId by remember { mutableStateOf(route.noteId) }
    var offeredMore by remember { mutableStateOf(route.noteId != null) }
    var toldMore by remember { mutableStateOf(route.noteId != null) }
    var triage by remember { mutableStateOf(Triage.OK) }
    var parsed by remember { mutableStateOf<Parsed?>(null) }
    var caption by remember { mutableStateOf("") }
    var unclear by remember { mutableStateOf(0) }
    var micOk by remember { mutableStateOf(Perms.has(ctx, *Perms.MIC)) }
    var pins by remember { mutableStateOf<List<Pin>>(emptyList()) }
    var reAsk by remember { mutableStateOf(false) }
    var openAudio by remember { mutableStateOf<String?>(null) }
    val askMic = rememberPermissionAsker { micOk = it }

    DisposableEffect(Unit) { onDispose { app.listener.stop(); app.speaker.stop() } }

    suspend fun evaluate(): Triage {
        val p = problem ?: return Triage.OK
        return DangerRules.evaluate(p.id, facts, parsed?.readings.orEmpty(), app.repo.recentForRules(), app.repo.person())
    }

    /** Saves progress: the note exists from the start, so nothing is ever lost. */
    suspend fun persist() {
        val id = noteId ?: return
        app.repo.updateFacts(id, facts)
        val t = evaluate()
        triage = t
        app.repo.updateTriage(id, t)
        app.refreshWidgets()
        if (t.level == Level.RED && phase != Phase.DANGER) {
            app.listener.stop()
            Alerts.dangerToHelpers(ctx, problem?.label.orEmpty(), t)
            savedFeedback(ctx)
            phase = Phase.DANGER
        }
    }

    fun toSummary() {
        app.listener.stop(); app.speaker.stop()
        scope.launch { persist(); if (phase != Phase.DANGER) phase = Phase.SUMMARY }
    }

    fun advance() {
        caption = ""; unclear = 0
        if (index + 1 < queue.size) { index++; return }
        if (reAsk) { reAsk = false; toSummary(); return }
        val p = problem ?: return toSummary()
        if (!offeredMore) {
            offeredMore = true
            if (Interview.extended(cat, p, facts).isNotEmpty()) { queue.add(Interview.MORE); index++; return }
        }
        toSummary()
    }

    fun answer(a: Ask, value: Any, label: String) {
        app.listener.stop()
        app.speaker.stop()
        caption = label
        when (a.id) {
            Interview.MORE.id -> {
                if (value == true) { toldMore = true; problem?.let { p -> queue.addAll(Interview.extended(cat, p, facts)) } }
                else noteId?.let { FollowUp.schedule(ctx, it) }
            }
            Interview.TOOK_MED.id -> {
                facts[a.field] = Fact(value, Source.ASKED)
                if (value == true && queue.getOrNull(index + 1)?.id != Interview.WHICH_MED.id) queue.add(index + 1, Interview.WHICH_MED)
            }
            Interview.WHEN.id -> facts[a.field] = Fact(label, Source.ASKED)   // the note keeps the time it was reported
            else -> facts[a.field] = Fact(value, Source.ASKED)
        }
        scope.launch { persist(); if (phase == Phase.ASK) { delay(450); advance() } }
    }

    /** Starts the conversation about [pid]: the note is created straight away. */
    fun begin(pid: String, known: Map<String, Fact> = emptyMap(), others: List<Mention> = emptyList(), transcript: String? = null, audio: String? = null) {
        val p = cat.problem(pid) ?: return
        scope.launch {
            problem = p
            facts.putAll(known)
            val id = noteId
            if (id == null) {
                val at = parsed?.occurredAt ?: System.currentTimeMillis()
                noteId = app.repo.saveTold(listOf(Mention(pid, facts = facts.toMutableMap())) + others, transcript, at, Triage.OK,
                    parsed?.readings.orEmpty(), parsed?.medicinesTaken.orEmpty(), audio).firstOrNull()
            } else app.repo.changeProblem(id, pid)
            queue.clear(); queue.addAll(Interview.core(cat, p, facts)); index = 0; caption = ""
            persist()
            if (phase == Phase.DANGER) return@launch
            if (queue.isEmpty()) { offeredMore = true; val ext = Interview.extended(cat, p, facts); if (ext.isNotEmpty()) { queue.add(Interview.MORE); phase = Phase.ASK } else toSummary() }
            else phase = Phase.ASK
        }
    }

    // "Tell me more" reminder: the extended questions of an existing note.
    LaunchedEffect(route.noteId) {
        val id = route.noteId ?: return@LaunchedEffect
        val n = app.db.notes().get(id) ?: return@LaunchedEffect
        val p = cat.problem(n.problemId) ?: return@LaunchedEffect
        FollowUp.remove(ctx, id)
        problem = p; facts.putAll(factsFromJson(n.details))
        queue.clear(); queue.addAll(Interview.extended(cat, p, facts)); index = 0
        phase = if (queue.isEmpty()) Phase.SUMMARY else Phase.ASK
    }
    // A tapped problem (widget, home, picture list): start straight away.
    LaunchedEffect(route.problemId) { if (route.problemId != null && route.noteId == null && noteId == null) begin(route.problemId) }
    // Words handed in by Google Assistant or a link.
    LaunchedEffect(route.text) {
        val t = route.text ?: return@LaunchedEffect
        val pr = app.parser.parse(t, null)
        parsed = pr
        phase = if (pr.main != null) Phase.CONFIRM else Phase.OPEN
    }

    val p = LocalPalette.current
    val sc = LocalScale.current

    when (phase) {
        // ───────────── free talk ─────────────
        Phase.OPEN -> {
            val q = "How are you feeling? Tell me in your own words."
            val listening by app.listener.listening.collectAsState()
            val partial by app.listener.partial.collectAsState()
            val ready by app.listener.state.collectAsState()
            fun listen() {
                if (!micOk) { askMic(Perms.MIC); return }
                caption = ""
                app.listener.start { h ->
                    caption = h.original
                    openAudio = h.audio?.absolutePath
                    if (h.text.isBlank()) { unclear++; return@start }
                    scope.launch {
                        val pr = app.parser.parse(h.text, null, app.db.medicines().active().map { it.name })
                        parsed = pr
                        if (pr.main == null) unclear++ else { unclear = 0; phase = Phase.CONFIRM }
                    }
                }
            }
            LaunchedEffect(micOk, ready, unclear) {
                if (unclear < 3 && micOk && (ready is Listener.State.Ready || app.listener.phoneAvailable()) && !app.listener.listening.value)
                    speak(app, if (unclear > 0) "Sorry, I didn't catch that. Please say it again." else q, lang) { listen() }
            }
            Conversation(
                title = "Tell how you feel", problem = null, onChange = null, onBack = { nav.back() }, progress = null,
                question = if (unclear > 0) "Sorry, I didn't catch that. Please say it again." else q, lang = lang,
                caption = if (listening) partial else caption, listening = listening,
                onMic = { if (listening) app.listener.finish() else listen() },
                onSkip = null, onDone = null,
            ) {
                if (!micOk) BigButton("Allow the microphone", onClick = { askMic(Perms.MIC) })
                Hint("For example: “I have a headache since morning”", center = true, modifier = Modifier.fillMaxWidth())
                BigButton("Choose from pictures", tone = Tone.QUIET, icon = Icons.Rounded.GridView, onClick = { app.listener.stop(); phase = Phase.PICK })
            }
        }

        // ───────────── "Is it headache?" ─────────────
        Phase.CONFIRM -> {
            val pr = parsed ?: return
            val m = pr.main ?: return
            val label = cat.problem(m.problemId)?.label ?: ""
            val q = "Is it ${label.lowercase()}?"
            fun yes() = begin(m.problemId, m.facts, pr.others, pr.transcript, openAudio)
            LaunchedEffect(q) { speak(app, q, lang) { listenYesNo(app) { ok -> if (ok) yes() else phase = Phase.PICK } } }
            val listening by app.listener.listening.collectAsState()
            val partial by app.listener.partial.collectAsState()
            Conversation("Tell how you feel", cat.problem(m.problemId), null, { phase = Phase.OPEN }, null, q, lang, if (listening) partial else caption, listening,
                onMic = { listenYesNo(app) { ok -> if (ok) yes() else phase = Phase.PICK } }, onSkip = null, onDone = null) {
                YesNoBig(onYes = { app.listener.stop(); yes() }, onNo = { app.listener.stop(); phase = Phase.PICK })
            }
        }

        // ───────────── choose, or change, the problem ─────────────
        Phase.PICK -> PickProblem(
            nav,
            onPicked = { pid -> app.listener.stop(); begin(pid) },
            onBack = { phase = if (problem != null) Phase.ASK else Phase.OPEN },
            onSay = if (problem == null) ({ phase = Phase.OPEN }) else null,
            title = if (problem != null) "Change to…" else "What's wrong?",
        )

        // ───────────── one question at a time ─────────────
        Phase.ASK -> {
            val pr = problem
            val a = queue.getOrNull(index)
            if (pr == null || a == null) { Box(Modifier.fillMaxSize().background(p.paper)); return }
            val listening by app.listener.listening.collectAsState()
            val partial by app.listener.partial.collectAsState()
            fun onHeard(h: Listener.Heard) {
                if (a.kind == Kind.BODY) {
                    val t = " " + Normalize.text(h.text) + " "
                    if (listOf(" all over ", " whole body ", " everywhere ", " full body ", " all my body ").any { t.contains(it) }) {
                        pins = emptyList(); facts["pin"] = Fact(ALL_OVER_PIN, Source.ASKED); facts.remove("side")
                        answer(a, ALL_OVER, ALL_OVER); return
                    }
                    val part = Body.parts.filter { !it.back }.filter { part -> part.label.lowercase().split(" ").all { w -> t.contains(" $w ") } }.maxByOrNull { it.label.length }
                    if (part != null) {
                        val pin = Pin(false, part.cx, part.cy, part)
                        pins = listOf(pin)
                        facts["pin"] = Fact(pin.encode(), Source.ASKED)
                        part.side?.let { facts["side"] = Fact(it, Source.ASKED) }
                        answer(a, part.label, part.label)
                    } else { caption = h.original; unclear++ }
                    return
                }
                when (val r = Interview.understand(a, h.text)) {
                    is Interview.Heard.Value -> answer(a, if (a.kind == Kind.FREE) h.text else r.value, if (a.kind == Kind.FREE) h.original else r.label)
                    Interview.Heard.Skip -> { caption = h.original; advance() }
                    Interview.Heard.Done -> toSummary()
                    Interview.Heard.Unclear -> { caption = h.original; unclear++ }
                }
            }
            fun listen() {
                if (!micOk) { askMic(Perms.MIC); return }
                app.listener.start(grammar = grammarFor(a), keepAudio = false, maxMs = if (a.kind == Kind.FREE) 45_000 else 12_000, silenceMs = if (a.kind == Kind.FREE) 2200 else 1300) { h ->
                    if (h.text.isNotBlank()) onHeard(h)
                }
            }
            val question = if (unclear > 0) "Sorry, I didn't catch that. ${a.text}" else a.text
            LaunchedEffect(a.id, index, unclear) {
                if (unclear >= 3) return@LaunchedEffect           // stop asking by voice; wait for a tap
                speak(app, question, lang) { listen() }
            }
            val coreCount = queue.count { it.core && it.id != Interview.MORE.id }
            val progress = if (a.core && a.id != Interview.MORE.id && !reAsk) "Question ${(index + 1).coerceAtMost(coreCount)} of $coreCount" else null
            Conversation(
                title = pr.label, problem = pr, onChange = { app.listener.stop(); phase = Phase.PICK },
                onBack = { app.listener.stop(); if (index > 0) { index--; caption = ""; unclear = 0 } else nav.back() },
                progress = progress, question = question, lang = lang,
                caption = if (listening) partial else caption, listening = listening,
                onMic = { if (listening) app.listener.finish() else listen() },
                onSkip = if (a.id == Interview.MORE.id) null else ({ app.listener.stop(); advance() }),
                onDone = { toSummary() },
            ) {
                AnswerPad(a, pins, pr.region, onPin = { pin ->
                    app.listener.stop()
                    pins = listOf(pin)
                    facts["pin"] = Fact(pin.encode(), Source.ASKED)
                    pin.part.side?.let { facts["side"] = Fact(it, Source.ASKED) }
                    caption = pin.part.label
                }, onAll = {
                    app.listener.stop()
                    pins = emptyList(); facts["pin"] = Fact(ALL_OVER_PIN, Source.ASKED); facts.remove("side")
                    answer(a, ALL_OVER, ALL_OVER)
                }, onAnswer = { v, l -> answer(a, v, l) })
            }
        }

        // ───────────── what was noted ─────────────
        Phase.SUMMARY -> {
            val pr = problem ?: return
            val rows = summaryRows(app, facts)
            val more = !toldMore && Interview.extended(cat, pr, facts).isNotEmpty()
            val say = "Here's what I noted. " + rows.joinToString(". ") { "${it.first}: ${it.second}" } + if (triage.level == Level.AMBER) ". " + triage.say else ""
            Screen("Here's what I noted", say, onHome = { nav.home() }) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpriteIcon(pr.id, 64.dp); Spacer(Modifier.width(14.dp))
                        Text(pr.label, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
                    }
                    rows.forEach { (k, v) -> SummaryRow(k, v) }
                    if (rows.isEmpty()) Hint("No details yet.")
                }
                if (triage.level == Level.AMBER) Card(color = p.amberSoft) {
                    Text("▲ " + triage.say, color = p.amber, fontWeight = FontWeight.Bold, fontSize = sc.body)
                    triage.reasons.forEach { Hint(it) }
                    DoctorCallButton()
                }
                BigButton("Done", tone = Tone.OK, icon = Icons.Rounded.Check, height = sc.target * 1.2f, onClick = {
                    scope.launch {
                        persist()
                        val id = noteId
                        if (id != null) { if (!toldMore) FollowUp.schedule(ctx, id) else FollowUp.remove(ctx, id) }
                        com.suryaprakash.medlog.meds.Scheduler.reschedule(ctx)
                        savedFeedback(ctx)
                        app.speaker.say("Saved. Get well soon.")
                        UndoHost.show("Saved.") { scope.launch { id?.let { app.repo.remove(listOf(it)); app.refreshWidgets() } } }
                        nav.home()
                    }
                })
                if (more) BigButton("Tell a little more", tone = Tone.QUIET, onClick = {
                    toldMore = true; offeredMore = true
                    queue.clear(); queue.addAll(Interview.extended(cat, pr, facts)); index = 0; phase = Phase.ASK
                })
                BigButton("Change an answer", tone = Tone.SECONDARY, icon = Icons.Rounded.Edit, onClick = {
                    val all = (Interview.core(cat, pr, emptyMap()) + Interview.extended(cat, pr, emptyMap())).filter { facts.containsKey(it.field) }.distinctBy { it.field }
                    if (all.isNotEmpty()) { queue.clear(); queue.addAll(all); index = 0; offeredMore = true; reAsk = true; phase = Phase.ASK }
                })
            }
        }

        Phase.DANGER -> DangerScreen(nav, triage, onChange = { phase = Phase.SUMMARY })
    }
}

// ───────────────────────── speaking and listening ─────────────────────────

/** Speaks [text] in the person's language when a translation and voice exist; English otherwise. */
private fun speak(app: MedLogApp, text: String, lang: String, then: (() -> Unit)? = null) {
    if (text.isBlank()) { then?.invoke(); return }
    val local = Localize.of(text.removePrefix("Sorry, I didn't catch that. "), lang)
    if (local != null && app.speaker.hasVoice(lang)) app.speaker.sayIn(local, lang, onDone = then) else app.speaker.say(text, onDone = then)
}

private fun listenYesNo(app: MedLogApp, onAnswer: (Boolean) -> Unit) {
    app.listener.start(grammar = Listener.YES_NO, keepAudio = false, maxMs = 9000, silenceMs = 1100) { h ->
        when (val r = Interview.understand(Interview.MORE, h.text)) { is Interview.Heard.Value -> onAnswer(r.value == true); else -> {} }
    }
}

/** Words the bundled English model listens for (the phone's own recogniser ignores this). */
private fun grammarFor(a: Ask): List<String>? = when (a.kind) {
    Kind.YESNO -> Listener.YES_NO
    Kind.NUMBER, Kind.TEMP -> Listener.NUMBERS
    Kind.CHOICE, Kind.MULTI, Kind.SCALE -> (a.choices.flatMap { it.words + it.label.lowercase() }.filter { w -> w.all { it.code < 128 } } +
        listOf("skip", "i don't know", "don't know", "that's all", "done", "stop") + if (a.kind == Kind.SCALE) Listener.NUMBERS else emptyList()).distinct()
    else -> null
}

// ───────────────────────── layout ─────────────────────────

/** Big question caption, the live "You said…" caption, the answers, and Skip / I'm done always there. */
@Composable
private fun Conversation(
    title: String,
    problem: Problem?,
    onChange: (() -> Unit)?,
    onBack: () -> Unit,
    progress: String?,
    question: String,
    lang: String,
    caption: String,
    listening: Boolean,
    onMic: () -> Unit,
    onSkip: (() -> Unit)?,
    onDone: (() -> Unit)?,
    answers: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val local = Localize.of(question.removePrefix("Sorry, I didn't catch that. "), lang)
    LaunchedEffect(question) { ReadAloud.text = question }
    Column(Modifier.fillMaxSize().background(p.paper).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = sc.margin, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).steady("Back", onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = p.brand, modifier = Modifier.size(28.dp))
            }
            if (problem != null) {
                SpriteIcon(problem.id, 40.dp); Spacer(Modifier.width(10.dp))
                Text(problem.label, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else Text(title, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink, modifier = Modifier.weight(1f))
            if (onChange != null) Row(
                Modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(p.fill).steady("Change the problem", onClick = onChange).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Icon(Icons.Rounded.Edit, null, tint = p.ink, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Change", fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = p.ink) }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = sc.margin), verticalArrangement = Arrangement.spacedBy(sc.gap)) {
            Spacer(Modifier.height(4.dp))
            if (progress != null) Text(progress, fontSize = sc.small, color = p.inkSoft, fontWeight = FontWeight.SemiBold)
            Text(question, fontSize = sc.title * 0.95f, fontWeight = FontWeight.Bold, color = p.ink, lineHeight = sc.title * 1.14f,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (local != null) Text(local, fontSize = sc.headline, color = p.inkSoft, lineHeight = sc.headline * 1.35f)
            CaptionBox(caption, listening, onMic)
            answers()
            Spacer(Modifier.height(8.dp))
        }
        if (onSkip != null || onDone != null) Column(Modifier.fillMaxWidth().background(p.paper)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Row(Modifier.fillMaxWidth().padding(horizontal = sc.margin, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onSkip != null) BigButton("Skip", Modifier.weight(1f), Tone.SECONDARY, onClick = onSkip)
                if (onDone != null) BigButton("I'm done", Modifier.weight(1f), Tone.PRIMARY, onClick = onDone)
            }
        }
        val nav = com.suryaprakash.medlog.ui.LocalNav.current
        com.suryaprakash.medlog.ui.BottomBar(onHome = { nav?.home() })
    }
}

/** The live caption: what the person is saying, large, with the microphone state. */
@Composable
private fun CaptionBox(caption: String, listening: Boolean, onMic: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val pulse = rememberInfiniteTransition(label = "mic")
    val k by pulse.animateFloat(1f, 1.2f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "k")
    val reduce = LocalSettings.current.lessMotion
    Row(
        Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(RoundedCornerShape(sc.radius))
            .background(if (listening) p.brandSoft else p.card)
            .border(if (listening) 2.dp else 1.dp, if (listening) p.brand else p.line, RoundedCornerShape(sc.radius))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (listening) Box(Modifier.size(50.dp).scale(if (reduce) 1f else k).clip(CircleShape).background(p.brand.copy(alpha = 0.18f)))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (listening) p.brand else p.fill).steady(if (listening) "Stop listening" else "Answer by voice", onClick = onMic),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Mic, null, tint = if (listening) Color.White else p.ink, modifier = Modifier.size(26.dp)) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (listening) "Listening…" else if (caption.isBlank()) "Tap the microphone to answer by voice" else "You said",
                fontSize = sc.small, color = if (listening) p.brand else p.inkSoft, fontWeight = FontWeight.SemiBold)
            if (caption.isNotBlank()) Text("“$caption”", fontSize = sc.headline, color = p.ink, fontWeight = FontWeight.Medium, lineHeight = sc.headline * 1.3f,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

@Composable
private fun YesNoBig(onYes: () -> Unit, onNo: () -> Unit) {
    val sc = LocalScale.current
    val lang = LocalSettings.current.languages.firstOrNull() ?: "en-IN"
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigButton(Localize.of("Yes", lang)?.let { "Yes · $it" } ?: "Yes", Modifier.weight(1f), Tone.PRIMARY, height = sc.target * 1.5f, onClick = onYes)
        BigButton(Localize.of("No", lang)?.let { "No · $it" } ?: "No", Modifier.weight(1f), Tone.SECONDARY, height = sc.target * 1.5f, onClick = onNo)
    }
}

/** The answers for one question: big, equal, with pictures where they help. */
@Composable
private fun AnswerPad(a: Ask, pins: List<Pin>, region: String?, onPin: (Pin) -> Unit, onAll: () -> Unit = {}, onAnswer: (Any, String) -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val lang = LocalSettings.current.languages.firstOrNull() ?: "en-IN"
    fun label(l: String) = Localize.of(l, lang)?.let { "$l\n$it" } ?: l
    when (a.kind) {
        Kind.YESNO -> YesNoBig(onYes = { onAnswer(true, "Yes") }, onNo = { onAnswer(false, "No") })
        Kind.CHOICE, Kind.SCALE -> {
            if (a.choices.any { it.sprite != null }) TileGrid(a.choices, if (a.kind == Kind.SCALE) 3 else 2, aspect = if (a.kind == Kind.SCALE) 0.92f else 1.0f) { c, m ->
                Tile(c.label, m, onClick = { onAnswer(if (a.kind == Kind.SCALE) c.value.toInt() else c.value, c.label) }) {
                    c.sprite?.let { SpriteIcon(it, if (a.kind == Kind.SCALE) 56.dp else 76.dp) }
                    if (a.kind == Kind.SCALE && a.choices.size == 6) Text(c.value, fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(if (a.kind == Kind.SCALE && a.choices.size == 6) 2.dp else 8.dp))
                    Text(label(c.label), fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = p.ink, textAlign = TextAlign.Center, maxLines = 3, minLines = 2, lineHeight = sc.small * 1.2f)
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                a.choices.forEach { c -> BigButton(label(c.label), tone = Tone.SECONDARY, onClick = { onAnswer(c.value, c.label) }) }
            }
            if (a.kind == Kind.SCALE && a.choices.size == 6) {
                Hint("Or tap the exact number")
                (0..10).chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { n ->
                            val col = listOf(0xFF22B573, 0xFF34C77B, 0xFF6CC24A, 0xFFA3CF3A, 0xFFD6C53A, 0xFFF5B83D, 0xFFF59E3D, 0xFFF97316, 0xFFF45B2A, 0xFFEF4444, 0xFFD62828)[n]
                            Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(col)).steady("$n out of 10") { onAnswer(n, "$n out of 10") },
                                contentAlignment = Alignment.Center) { Text("$n", fontSize = sc.body, fontWeight = FontWeight.Bold, color = Color.White) }
                        }
                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Kind.MULTI -> {
            val chosen = remember(a.id) { mutableStateListOf<String>() }
            val pics = a.choices.any { it.sprite != null }
            TileGrid(a.choices, 2, aspect = if (pics) 1.0f else 2.6f) { c, m ->
                Tile(c.label, m, selected = c.value in chosen, onClick = { if (c.value in chosen) chosen.remove(c.value) else chosen.add(c.value) }) {
                    c.sprite?.let { SpriteIcon(it, 64.dp); Spacer(Modifier.height(8.dp)) }
                    Text(label(c.label), fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = p.ink, textAlign = TextAlign.Center, maxLines = 3)
                }
            }
            BigButton(if (chosen.isEmpty()) "Tap all that fit" else "That's it", tone = Tone.PRIMARY, enabled = chosen.isNotEmpty(), icon = Icons.Rounded.Check,
                onClick = { onAnswer(chosen.toList(), a.choices.filter { it.value in chosen }.joinToString(", ") { it.label }) })
        }
        Kind.NUMBER -> {
            if (a.choices.isNotEmpty()) TileGrid(a.choices, 3, aspect = 1.5f) { c, m ->
                Tile(c.label, m, onClick = { onAnswer(c.value.toInt(), c.label) }) { Text(label(c.label), fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink, textAlign = TextAlign.Center) }
            } else NumberPad(unit = "", allowDecimal = false, range = 0.0..999.0) { onAnswer(it.toInt(), "${it.toInt()}") }
        }
        Kind.TEMP -> NumberPad(unit = "°F", allowDecimal = true, range = 93.0..110.0) { onAnswer(it, "$it °F") }
        Kind.BODY -> {
            var back by remember { mutableStateOf(false) }
            val close = com.suryaprakash.medlog.pictogram.viewFor(region)
            var whole by remember(region) { mutableStateOf(close == com.suryaprakash.medlog.pictogram.WHOLE) }
            com.suryaprakash.medlog.ui.Segmented(listOf("Front", "Back"), if (back) 1 else 0) { back = it == 1 }
            val view = if (whole) com.suryaprakash.medlog.pictogram.WHOLE else close
            // big enough to point at, never so big that the answer button scrolls away
            Box(Modifier.fillMaxWidth().heightIn(max = 380.dp).clip(RoundedCornerShape(sc.radius)).background(p.card), contentAlignment = Alignment.Center) {
                BodyMap(back, pins, Modifier, view = view, onTap = onPin)
            }
            if (close != com.suryaprakash.medlog.pictogram.WHOLE) Text(
                if (whole) "Show close up" else "Show the whole body", fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.brand, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).steady(if (whole) "Show close up" else "Show the whole body") { whole = !whole }.wrapContentHeight(Alignment.CenterVertically),
            )
            val pin = pins.lastOrNull()
            BigButton(if (pin == null) "Tap the body to mark the spot" else "It's here: ${pin.part.label.lowercase()}", tone = Tone.PRIMARY, enabled = pin != null, icon = Icons.Rounded.Check,
                onClick = { pin?.let { onAnswer(it.part.label, it.part.label) } })
            BigButton("It's all over my body", tone = Tone.SECONDARY, onClick = onAll)
        }
        Kind.FREE -> Hint("Just speak. Tap Skip if there's nothing to add.", center = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SummaryRow(k: String, v: String) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(k, fontSize = sc.small, color = p.inkSoft, modifier = Modifier.width(118.dp).alignByBaseline(), fontWeight = FontWeight.SemiBold)
            Text(v, fontSize = sc.body, color = p.ink, modifier = Modifier.weight(1f).alignByBaseline())
        }
    }
}

/**
 * The facts as plain rows: the main facts, then "Warning signs" (only the ones that were there, or "None"),
 * then anything else noticed. Never lists what was *not* there ("not the worst ever").
 */
fun summaryRows(app: MedLogApp, facts: Map<String, Fact>, at: Long = System.currentTimeMillis()): List<Pair<String, String>> {
    val order = listOf("started", "count", "site", "depth", "severity", "character", "burnDepth", "burnSize", "pattern", "worse", "better", "tookMedicine", "medicineTaken", "note")
    val names = mapOf("started" to "Started", "count" to "Times", "site" to "Where", "depth" to "How deep", "severity" to "How bad", "character" to "Feels like",
        "burnDepth" to "Burn looks", "burnSize" to "Burn size", "pattern" to "Pattern", "worse" to "Worse with", "better" to "Better with",
        "tookMedicine" to "Medicine", "medicineTaken" to "Which one", "note" to "Also said")
    val skip = setOf("pin", "side", "more")
    val rows = ArrayList<Pair<String, String>>()
    for (k in order) {
        val f = facts[k] ?: continue
        val v = when (k) {
            "severity" -> app.describe.fact(k, f)
            "character", "worse", "better" -> (f.value as? List<*>)?.joinToString(", ") ?: f.value.toString()
            "medicineTaken", "note" -> "“${f.value}”"
            "tookMedicine" -> if (f.value == true) "Yes" else "No"
            "count" -> "${f.value}"
            "started" -> startedWords(f.value.toString(), at)
            else -> f.value.toString()
        } ?: continue
        if (k == "better" && f.value == true) continue
        rows += names.getValue(k) to v.replaceFirstChar(Char::uppercase)
    }
    val rest = facts.filterKeys { it !in order && it !in skip && !it.startsWith("_") }
    val danger = rest.filterKeys { app.catalogue.field(it)?.danger == true }
    val found = danger.filterValues { it.value == true }.mapNotNull { (k, f) -> app.describe.fact(k, f) }
    if (danger.isNotEmpty()) rows += "Warning signs" to (if (found.isEmpty()) "None" else found.joinToString(", ").replaceFirstChar(Char::uppercase))
    val noticed = rest.filterKeys { it !in danger }.mapNotNull { (k, f) ->
        when {
            k == "better" -> if (f.value == true) "getting better" else null
            f.value == false -> null            // what was not there is not news
            else -> app.describe.fact(k, f)
        }
    }
    if (noticed.isNotEmpty()) rows += "Also noticed" to noticed.joinToString(", ").replaceFirstChar(Char::uppercase)
    return rows
}

/** The answer and pin used when a problem is everywhere, not in one spot. */
const val ALL_OVER = "All over the body"
const val ALL_OVER_PIN = "front:all"

/** Big number keys for readings and counts. */
@Composable
fun NumberPad(unit: String, allowDecimal: Boolean, range: ClosedFloatingPointRange<Double>, initial: String = "", onDone: (Double) -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var text by remember { mutableStateOf(initial) }
    val v = text.toDoubleOrNull()
    val ok = v != null && v in range
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(min = sc.target * 1.2f).clip(RoundedCornerShape(sc.radius)).background(p.card).border(2.dp, p.brand, RoundedCornerShape(sc.radius)), contentAlignment = Alignment.Center) {
            Text(if (text.isEmpty()) "–" else "$text $unit".trim(), fontSize = sc.huge, fontWeight = FontWeight.Bold, color = p.ink)
        }
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(if (allowDecimal) "." else "", "0", "⌫"))
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    if (k.isEmpty()) Spacer(Modifier.weight(1f).heightIn(min = sc.target)) else
                        BigButton(k, Modifier.weight(1f), if (k == "⌫") Tone.SECONDARY else Tone.QUIET, onClick = {
                            text = when (k) { "⌫" -> text.dropLast(1); "." -> if ("." in text) text else "$text."; else -> if (text.length < 6) text + k else text }
                        })
                }
            }
        }
        if (text.isNotEmpty() && !ok) Hint("That number looks wrong. Please check.")
        BigButton("Done", tone = Tone.OK, enabled = ok, onClick = { v?.let(onDone) })
    }
}
