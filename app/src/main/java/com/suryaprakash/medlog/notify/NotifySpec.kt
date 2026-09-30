package com.suryaprakash.medlog.notify

/**
 * Every notification MedLog can show, as one table. Pure Kotlin (no Android types) so it can be unit-tested.
 *
 * INVENTORY (who shows it, today):
 *  medicine due / repeat / snooze ....... meds/DoseAlert, meds/Scheduler        -> DOSE_DUE
 *  medicine missed ...................... meds/Scheduler.tick                     -> DOSE_MISSED (patient), HELPER_MISSED_DOSE
 *  refill ............................... care/Care.refill                        -> REFILL, HELPER_REFILL
 *  morning check-in ..................... care/Care, AlertActivity.CHECKIN        -> CHECKIN, HELPER_NO_CHECKIN
 *  water / food / BP / sugar ............ care/Care                               -> WATER, FOOD, BP_CHECK, SUGAR_CHECK
 *  appointment .......................... care/Care                               -> APPOINTMENT
 *  SOS / fall ........................... help/Sos, FallService, AlertActivity    -> SOS, FALL, HELPER_ALERT
 *  safety-rule notes to family .......... help/Alerts.dangerToHelpers/amber       -> HELPER_ALERT, HELPER_NOTE
 *  phone battery low .................... help/Alerts.Type.LOW_BATTERY            -> HELPER_LOW_BATTERY
 *  messages ............................. help/FamilyChat, Nearby                 -> MESSAGE
 *  entry added by patient / helper ...... sync agent -> Alerts.entryAdded         -> ENTRY_BY_PATIENT, ENTRY_BY_HELPER
 *  permission / battery / relay warnings  Perms                                   -> PERMISSION_WARNING, BATTERY_WARNING, RELAY_DOWN
 *
 * Wording rules (hospital sign): title <= 5 words, body <= 8 words, buttons <= 3 words and verb first.
 * Placeholders in {braces} count as one word.
 */
object NotifySpec {
    enum class Tier { INFO, ROUTINE, IMPORTANT, URGENT, EMERGENCY }
    enum class Who { PATIENT, HELPERS }
    enum class Sound { NONE, SOFT, CHIME, ALARM }
    enum class FullScreen { NEVER, WHEN_LOCKED_OR_OFF, ALWAYS }
    /** What quiet hours do: DELIVER as normal, SOFTEN the sound, SILENT (shown, no sound), HOLD until morning. */
    enum class Quiet { DELIVER, SOFTEN, SILENT, HOLD }
    enum class Result { ACKNOWLEDGED, SNOOZED, SKIPPED, DEFERRED, DECLINED, CANCELLED, CALLED, OPENED, ESCALATED }
    enum class After(val minutes: Int) { NOW(0), SEC_30(0), MIN_1(1), MIN_5(5), MIN_10(10), MIN_30(30), MIN_60(60), SNOOZE(-1), TWO_SNOOZES(-2), HELPER_DELAY(-3), MISS(-4), DAY_1(24 * 60) }
    enum class Act { REPEAT_LOUDER, REPEAT, TELL_HELPERS, MARK_MISSED, SHOW_MISSED, START_SOS, CALL_HELPERS, NEXT_HELPER, CALL_EMERGENCY, RING_LOUDER }
    data class Step(val after: After, val act: Act)
    data class Chan(val id: String, val importance: Int)   // importance: 2 LOW, 3 DEFAULT, 4 HIGH
    /** [tell]: who else hears about this answer. [onlyIfEscalated]: only when helpers were already alerted. */
    data class Resp(val id: String, val label: String, val result: Result, val asksWhy: Boolean = false,
                    val tell: Set<Who> = emptySet(), val onlyIfEscalated: Boolean = false)

    enum class Type { DOSE_DUE, DOSE_MISSED, REFILL, CHECKIN, WATER, FOOD, BP_CHECK, SUGAR_CHECK, APPOINTMENT, SOS, FALL, MESSAGE,
        PERMISSION_WARNING, BATTERY_WARNING, RELAY_DOWN,
        HELPER_ALERT, HELPER_NOTE, HELPER_MISSED_DOSE, HELPER_REFILL, HELPER_NO_CHECKIN, HELPER_LOW_BATTERY, ENTRY_BY_PATIENT, ENTRY_BY_HELPER }

    data class Row(val type: Type, val chan: Chan, val tier: Tier, val to: Set<Who>, val sound: Sound, val vibrate: Boolean, val fullScreen: FullScreen,
                   val quiet: Quiet, val repeat: List<Step>, val escalation: List<Step>, val title: String, val body: String, val responses: List<Resp>, val notifId: Int) {
        fun resp(id: String) = responses.firstOrNull { it.id == id }
    }

    val DOSE = Chan("dose", 4); val ALERT = Chan("alert", 4); val CARE = Chan("care", 4)
    val P = setOf(Who.PATIENT); val H = setOf(Who.HELPERS); val BOTH = setOf(Who.PATIENT, Who.HELPERS)
    const val MISS_AFTER_MIN = 180
    const val QUIET_FROM = 22
    const val QUIET_TO = 7
    const val DOSE_ID = 7000
    const val MISSED_ID = 7100

    private fun r(id: String, label: String, res: Result, asks: Boolean = false, tell: Set<Who> = emptySet(), esc: Boolean = false) = Resp(id, label, res, asks, tell, esc)
    private val later = r("later", "Snooze 30 min", Result.SNOOZED)
    private val clear = r("clear", "Clear", Result.ACKNOWLEDGED)
    private val settings = listOf(r("open", "Open settings", Result.OPENED), r("remind", "Remind later", Result.SNOOZED))
    private val helperAnswers = listOf(r("coming", "Coming now", Result.ACKNOWLEDGED, tell = BOTH), r("cant", "Decline", Result.DECLINED, tell = H), r("call", "Call patient", Result.CALLED))

    val table: List<Row> = listOf(
        Row(Type.DOSE_DUE, DOSE, Tier.IMPORTANT, P, Sound.ALARM, true, FullScreen.ALWAYS, Quiet.SOFTEN,
            listOf(Step(After.SNOOZE, Act.REPEAT_LOUDER), Step(After.TWO_SNOOZES, Act.REPEAT_LOUDER)),
            listOf(Step(After.HELPER_DELAY, Act.TELL_HELPERS), Step(After.MISS, Act.SHOW_MISSED)),
            "Time for {medicine}", "{medicines}",
            listOf(r("take", "Mark taken", Result.ACKNOWLEDGED, tell = H, esc = true), r("snooze", "Snooze {n} min", Result.SNOOZED),
                r("skip", "Skip", Result.SKIPPED, asks = true, tell = H, esc = true), r("later", "Dismiss", Result.DEFERRED)), DOSE_ID),
        Row(Type.DOSE_MISSED, DOSE, Tier.IMPORTANT, P, Sound.CHIME, true, FullScreen.NEVER, Quiet.SOFTEN, emptyList(),
            listOf(Step(After.MIN_10, Act.TELL_HELPERS)),
            "Medicine missed", "{medicine} was not taken.",
            listOf(r("take", "Take now", Result.ACKNOWLEDGED, tell = H, esc = true), r("skip", "Skip", Result.SKIPPED, asks = true, tell = H, esc = true),
                r("tell", "Tell helper", Result.ESCALATED, tell = H)), MISSED_ID),
        Row(Type.REFILL, CARE, Tier.ROUTINE, P, Sound.SOFT, false, FullScreen.NEVER, Quiet.HOLD, emptyList(), listOf(Step(After.MIN_1, Act.TELL_HELPERS)),
            "Buy more medicine", "{medicine} is running low.",
            listOf(r("open", "Open meds", Result.OPENED), r("remind", "Remind later", Result.SNOOZED), r("tell", "Tell helper", Result.ESCALATED, tell = H)), 8300),
        Row(Type.CHECKIN, CARE, Tier.ROUTINE, P, Sound.SOFT, false, FullScreen.WHEN_LOCKED_OR_OFF, Quiet.HOLD, emptyList(), listOf(Step(After.MIN_60, Act.TELL_HELPERS)),
            "Morning check-in", "How are you today?",
            listOf(r("well", "Say I'm well", Result.ACKNOWLEDGED), r("unwell", "Say I'm unwell", Result.ACKNOWLEDGED, tell = H), r("later", "Dismiss", Result.DEFERRED)), 8100),
        Row(Type.WATER, CARE, Tier.INFO, P, Sound.SOFT, false, FullScreen.NEVER, Quiet.HOLD, emptyList(), emptyList(),
            "Drink water", "A glass helps.", listOf(r("log", "Log water", Result.ACKNOWLEDGED), later, r("skip", "Skip", Result.SKIPPED)), 8400),
        Row(Type.FOOD, CARE, Tier.ROUTINE, P, Sound.SOFT, false, FullScreen.NEVER, Quiet.HOLD, emptyList(), emptyList(),
            "Time to eat", "{meal} is due.", listOf(r("log", "Log meal", Result.ACKNOWLEDGED), later, r("skip", "Skip", Result.SKIPPED)), 8500),
        Row(Type.BP_CHECK, CARE, Tier.ROUTINE, P, Sound.SOFT, false, FullScreen.NEVER, Quiet.HOLD, emptyList(), emptyList(),
            "Check blood pressure", "Sit and rest first.", listOf(r("log", "Log reading", Result.OPENED), later, r("skip", "Skip", Result.SKIPPED)), 8600),
        Row(Type.SUGAR_CHECK, CARE, Tier.ROUTINE, P, Sound.SOFT, false, FullScreen.NEVER, Quiet.HOLD, emptyList(), emptyList(),
            "Check blood sugar", "Wash hands first.", listOf(r("log", "Log reading", Result.OPENED), later, r("skip", "Skip", Result.SKIPPED)), 8700),
        Row(Type.APPOINTMENT, CARE, Tier.IMPORTANT, BOTH, Sound.CHIME, true, FullScreen.NEVER, Quiet.SOFTEN, listOf(Step(After.MIN_30, Act.REPEAT)), listOf(Step(After.MIN_60, Act.TELL_HELPERS)),
            "Doctor visit soon", "{doctor} at {time}.",
            listOf(r("open", "Open visit", Result.OPENED), r("call", "Call clinic", Result.CALLED), later), 8200),
        Row(Type.SOS, ALERT, Tier.EMERGENCY, P, Sound.ALARM, true, FullScreen.ALWAYS, Quiet.DELIVER, emptyList(),
            listOf(Step(After.MIN_1, Act.CALL_HELPERS), Step(After.MIN_5, Act.CALL_EMERGENCY)),
            "Asking for help", "Your family is being told.",
            listOf(r("ok", "Cancel: I'm OK", Result.CANCELLED, tell = H), r("call", "Call helper", Result.CALLED)), 9000),
        Row(Type.FALL, ALERT, Tier.URGENT, P, Sound.ALARM, true, FullScreen.ALWAYS, Quiet.DELIVER,
            emptyList(), listOf(Step(After.MIN_1, Act.START_SOS)),
            "Did you fall?", "Tap if you need help.",
            listOf(r("ok", "Cancel alert", Result.CANCELLED), r("help", "Ask for help", Result.ESCALATED, tell = H)), 9100),
        Row(Type.MESSAGE, ALERT, Tier.ROUTINE, BOTH, Sound.CHIME, true, FullScreen.NEVER, Quiet.SILENT, emptyList(), emptyList(),
            "New message", "{name}: {text}", listOf(r("reply", "Reply", Result.OPENED), r("call", "Call", Result.CALLED), clear), 9200),
        Row(Type.PERMISSION_WARNING, CARE, Tier.IMPORTANT, P, Sound.SOFT, false, FullScreen.NEVER, Quiet.HOLD, listOf(Step(After.DAY_1, Act.REPEAT)), listOf(Step(After.DAY_1, Act.TELL_HELPERS)),
            "Allow reminders", "Medicine alarms need permission.", settings, 9300),
        Row(Type.BATTERY_WARNING, CARE, Tier.IMPORTANT, P, Sound.SOFT, false, FullScreen.NEVER, Quiet.HOLD, listOf(Step(After.DAY_1, Act.REPEAT)), listOf(Step(After.DAY_1, Act.TELL_HELPERS)),
            "Battery may block alarms", "Allow MedLog in battery settings.", settings, 9310),
        Row(Type.RELAY_DOWN, CARE, Tier.ROUTINE, BOTH, Sound.NONE, false, FullScreen.NEVER, Quiet.HOLD, emptyList(), emptyList(),
            "Family link offline", "Alerts will go by SMS.", listOf(r("open", "Open settings", Result.OPENED), clear), 9320),
        Row(Type.HELPER_ALERT, ALERT, Tier.EMERGENCY, H, Sound.ALARM, true, FullScreen.ALWAYS, Quiet.DELIVER, emptyList(),
            listOf(Step(After.SEC_30, Act.RING_LOUDER), Step(After.MIN_1, Act.NEXT_HELPER), Step(After.MIN_5, Act.CALL_EMERGENCY)),
            "{name} needs help", "{text}", helperAnswers, 9400),
        Row(Type.HELPER_NOTE, ALERT, Tier.IMPORTANT, H, Sound.CHIME, true, FullScreen.NEVER, Quiet.SOFTEN, emptyList(), listOf(Step(After.MIN_30, Act.REPEAT_LOUDER)),
            "Check on {name}", "{text}", helperAnswers, 9410),
        Row(Type.HELPER_MISSED_DOSE, ALERT, Tier.IMPORTANT, H, Sound.CHIME, true, FullScreen.NEVER, Quiet.SOFTEN, emptyList(), listOf(Step(After.MIN_30, Act.REPEAT_LOUDER)),
            "Medicine not taken", "{name} missed {medicine}.", helperAnswers, 9420),
        Row(Type.HELPER_REFILL, CARE, Tier.ROUTINE, H, Sound.SOFT, false, FullScreen.NEVER, Quiet.HOLD, emptyList(), emptyList(),
            "Help buy medicine", "{name} needs {medicine}.",
            listOf(r("buy", "Buy it", Result.ACKNOWLEDGED, tell = P), r("cant", "Decline", Result.DECLINED, tell = H), r("call", "Call patient", Result.CALLED)), 9430),
        Row(Type.HELPER_NO_CHECKIN, ALERT, Tier.IMPORTANT, H, Sound.CHIME, true, FullScreen.NEVER, Quiet.SOFTEN, emptyList(), listOf(Step(After.MIN_30, Act.REPEAT_LOUDER)),
            "No check-in yet", "{name} has not answered.", helperAnswers, 9440),
        Row(Type.HELPER_LOW_BATTERY, CARE, Tier.ROUTINE, H, Sound.SOFT, false, FullScreen.NEVER, Quiet.HOLD, emptyList(), emptyList(),
            "Phone battery low", "{name}'s phone needs charging.", listOf(r("call", "Call patient", Result.CALLED), clear), 9450),
        Row(Type.ENTRY_BY_PATIENT, CARE, Tier.INFO, H, Sound.NONE, false, FullScreen.NEVER, Quiet.HOLD, emptyList(), emptyList(),
            "Entry added by {name}", "{what}", listOf(r("open", "Open entry", Result.OPENED), clear), 9500),
        Row(Type.ENTRY_BY_HELPER, CARE, Tier.INFO, P, Sound.NONE, false, FullScreen.NEVER, Quiet.HOLD, emptyList(), emptyList(),
            "Entry added by {name}", "{what}", listOf(r("open", "Open entry", Result.OPENED), clear), 9510),
    )

    private val byType = table.associateBy { it.type }
    operator fun get(t: Type): Row = byType.getValue(t)
    fun label(t: Type, respId: String, n: Int = 10) = get(t).resp(respId)?.label?.replace("{n}", n.toString()) ?: respId

    // ───────────── schedule helpers used by Scheduler ─────────────

    /** Minutes after the due time at which the schedule step happens (snooze-based steps are relative to the snooze). */
    fun minutes(a: After, snooze: Int, escalate: Int, escalateCritical: Int, critical: Boolean): Int = when (a) {
        After.SNOOZE -> snooze; After.TWO_SNOOZES -> 2 * snooze
        After.HELPER_DELAY -> if (critical) escalateCritical else escalate
        After.MISS -> MISS_AFTER_MIN
        else -> a.minutes
    }
    /** How many louder repeats a dose gets (snooze-based repeat steps of DOSE_DUE). */
    val doseRepeats: Int = get(Type.DOSE_DUE).repeat.count { it.after == After.SNOOZE || it.after == After.TWO_SNOOZES }
    fun helperDelayMin(critical: Boolean, escalate: Int, escalateCritical: Int) = if (critical) escalateCritical else escalate
    fun isQuiet(hour: Int) = hour >= QUIET_FROM || hour < QUIET_TO

    // ───────────── the state matrix ─────────────

    enum class App { OPEN, CLOSED }
    enum class Perm { OK, NOTIFICATIONS_DENIED, FULLSCREEN_DENIED }
    enum class Net { ONLINE, OFFLINE, RELAY_DOWN }
    enum class Show { FULL_SCREEN, HEADS_UP, SHADE, SILENT_SHADE, IN_APP_BANNER, HOLD_UNTIL_MORNING, OPEN_ON_NEXT_LAUNCH, NOT_ADDRESSED }
    enum class Fallback { IN_APP_BANNER, SPEAK, SMS_HELPERS, NEARBY, RELAY_QUEUE }
    data class State(val receiver: Who, val screenOn: Boolean, val app: App, val locked: Boolean, val dnd: Boolean, val quiet: Boolean, val perm: Perm, val net: Net)
    data class Behaviour(val show: Show, val sound: Sound, val vibrate: Boolean, val fallbacks: Set<Fallback>, val escalateEarlier: Boolean)

    fun allStates(): List<State> = buildList {
        for (w in Who.values()) for (s in listOf(true, false)) for (a in App.values()) for (l in listOf(false, true)) for (d in listOf(false, true))
            for (q in listOf(false, true)) for (p in Perm.values()) for (n in Net.values()) add(State(w, s, a, l, d, q, p, n))
    }

    /** What actually happens for [type] in [s]. Total: every combination has a defined behaviour. */
    fun resolve(type: Type, s: State): Behaviour {
        val row = get(type)
        if (s.receiver !in row.to) return Behaviour(Show.NOT_ADDRESSED, Sound.NONE, false, emptySet(), false)
        val visible = s.screenOn && !s.locked && s.app == App.OPEN
        val loud = row.tier >= Tier.URGENT
        val fb = LinkedHashSet<Fallback>()
        var sound = row.sound
        var vibrate = row.vibrate
        var early = false
        var show = when (row.fullScreen) {
            FullScreen.ALWAYS -> Show.FULL_SCREEN
            FullScreen.WHEN_LOCKED_OR_OFF -> if (!s.screenOn || s.locked) Show.FULL_SCREEN else if (visible) Show.IN_APP_BANNER else Show.HEADS_UP
            FullScreen.NEVER -> if (visible) Show.IN_APP_BANNER else if (row.tier >= Tier.IMPORTANT) Show.HEADS_UP else Show.SHADE
        }
        if (s.perm == Perm.FULLSCREEN_DENIED && show == Show.FULL_SCREEN) { show = Show.HEADS_UP; fb += Fallback.SPEAK }
        if (s.perm == Perm.NOTIFICATIONS_DENIED) {
            show = if (visible) Show.IN_APP_BANNER else Show.OPEN_ON_NEXT_LAUNCH
            fb += Fallback.IN_APP_BANNER
            if (row.tier >= Tier.IMPORTANT) fb += Fallback.SPEAK
            if (s.receiver == Who.PATIENT && Who.HELPERS in row.to || s.receiver == Who.HELPERS) early = row.tier >= Tier.IMPORTANT
        }
        if (s.dnd && !loud) { sound = Sound.NONE; early = early || row.tier == Tier.IMPORTANT }
        if (s.quiet) when (row.quiet) {
            Quiet.DELIVER -> {}
            Quiet.SOFTEN -> if (sound > Sound.SOFT) sound = Sound.SOFT
            Quiet.SILENT -> { sound = Sound.NONE; vibrate = false; if (show == Show.HEADS_UP) show = Show.SILENT_SHADE }
            Quiet.HOLD -> { sound = Sound.NONE; vibrate = false; if (show != Show.IN_APP_BANNER && show != Show.OPEN_ON_NEXT_LAUNCH) show = Show.HOLD_UNTIL_MORNING }
        }
        if (s.net != Net.ONLINE) {
            if (s.receiver == Who.PATIENT && Who.HELPERS in row.to) { fb += Fallback.SMS_HELPERS; fb += Fallback.NEARBY }
            if (s.net == Net.RELAY_DOWN) fb += Fallback.RELAY_QUEUE
            if (s.net == Net.OFFLINE && s.receiver == Who.HELPERS) fb += Fallback.NEARBY
        }
        return Behaviour(show, sound, vibrate, fb, early)
    }

    // ───────────── telling the other parties ─────────────

    /** Set by the app once a shared announcer exists; a no-op until then. Args: type, response, who answered. */
    @Volatile var announce: (Type, Resp, Who) -> Unit = { _, _, _ -> }

    /** Records an answer and, if the table says others hear about it, calls [announce]. Returns who should be told. */
    fun responded(type: Type, respId: String, by: Who, escalated: Boolean = false): Set<Who> {
        val resp = get(type).resp(respId) ?: return emptySet()
        val tell = if (resp.onlyIfEscalated && !escalated) emptySet() else resp.tell
        if (tell.isNotEmpty()) runCatching { announce(type, resp, by) }
        return tell
    }

    /** Verbs a button may start with (tests enforce verb-first). */
    val VERBS = setOf("mark", "snooze", "skip", "dismiss", "take", "tell", "open", "remind", "say", "log", "call", "cancel", "ask", "reply", "clear", "coming", "decline", "buy")
}
