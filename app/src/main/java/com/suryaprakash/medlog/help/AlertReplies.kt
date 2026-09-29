package com.suryaprakash.medlog.help

/** One answer a helper can give to an alert: what goes back to the person's phone, and the words on the button. */
data class Reply(val code: String, val words: String)

/**
 * The three answers on every helper alert, in one place. The alert screen, the notification buttons and the
 * helper's home card all read this table, so they can never disagree. Exactly three, always in this order:
 * 1 answering, 2 later or skip, 3 asking someone else.
 */
object AlertReplies {
    const val COMING = "coming"
    const val IN_5 = "5min"
    const val ASK = "ask"
    const val GIVE = "give"
    const val SKIP_DOSE = "skipdose"
    const val HANDLE = "handle"
    const val SKIP = "skip"

    private val RED = listOf(Reply(COMING, "I'm coming"), Reply(IN_5, "In 5 min"), Reply(ASK, "Ask another helper"))
    private val DOSE = listOf(Reply(GIVE, "I'll give it"), Reply(SKIP_DOSE, "Skip this dose"), Reply(ASK, "Ask another helper"))
    private val OTHER = listOf(Reply(HANDLE, "I'll handle it"), Reply(SKIP, "Skip"), Reply(ASK, "Ask another helper"))

    fun forType(type: Alerts.Type): List<Reply> = when (type) {
        Alerts.Type.SOS, Alerts.Type.FALL, Alerts.Type.DANGER, Alerts.Type.MESSAGE -> RED
        Alerts.Type.MISSED_DOSE -> DOSE
        Alerts.Type.AMBER, Alerts.Type.CHECKIN, Alerts.Type.REFILL, Alerts.Type.LOW_BATTERY -> OTHER
    }

    /** By the kind name a phone sent; anything else (a handed-over message, an unknown kind) is treated as a message. */
    fun forKind(kind: String): List<Reply> = forType(Alerts.Type.values().firstOrNull { it.name == kind } ?: Alerts.Type.MESSAGE)

    /** SOS, danger and fall: the red alerts. */
    fun urgent(kind: String) = kind == "SOS" || kind == "DANGER" || kind == "FALL"

    /** The title of an alert card, the same on the alert screen and on the helper's home card. */
    fun title(kind: String, who: String, text: String) = when (kind) {
        "SOS" -> "SOS from $who"
        "DANGER" -> "$who may need urgent help"
        "FALL" -> "$who may have fallen"
        "AMBER" -> "$who noted something to watch"
        "MISSED_DOSE" -> if ("feed" in text) "A feed isn't marked as given" else "A medicine isn't marked as taken"
        "CHECKIN" -> "No answer to the check-in"
        "LOW_BATTERY" -> "$who's phone battery is low"
        "REFILL" -> "A medicine is running low"
        else -> "Message from $who"
    }

    /** What the person's phone shows for a reply; older phones also send "call", "cant" and "got". */
    fun words(r: String): String = when {
        r.startsWith("ask:") -> "asked ${r.removePrefix("ask:")} to go"
        else -> when (r) {
            COMING -> "I'm coming"; IN_5 -> "In 5 minutes"; ASK -> "Asked another helper to go"
            GIVE -> "I'll give it"; SKIP_DOSE -> "Skipped this dose"; HANDLE -> "I'll handle it"; SKIP -> "Skipped"
            "call" -> "I'll call you"; "cant" -> "Can't come now, I'll call"; "got" -> "Got it"
            else -> r
        }
    }
}
