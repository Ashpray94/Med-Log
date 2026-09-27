package com.suryaprakash.medlog.help

/**
 * Every message MedLog sends to family, in one place, so the tone can be checked (plan 10.3: calm, not scary).
 *
 * Rules: say what happened and what would help, in plain words. No alarm words, no capitals, no exclamation
 * marks, no medical conclusions. Family should feel asked to help, not frightened. [check] enforces this in tests.
 */
object Wording {
    private fun n(name: String) = name.ifBlank { "Your family member" }

    fun sos(name: String, reason: String, lastNote: String?, where: String?) = buildString {
        append("MedLog: ${n(name)} pressed the SOS button and would like help. Please call or go to them.")
        if (reason.isNotBlank() && reason != "SOS") append(" They said: $reason.")
        lastNote?.let { append(" Last note: $it.") }
        append(if (where != null) " Where they are: $where" else " Their location could not be found.")
    }

    /** A note the app's safety rules say needs medical help straight away. */
    fun seeDoctorNow(name: String, problem: String) =
        "MedLog: ${n(name)} noted \"$problem\". MedLog suggested getting medical help straight away. Please call or go to them."

    /** A note the app's rules say is worth a call to the doctor today. */
    fun seeDoctorToday(name: String, problem: String) =
        "MedLog: ${n(name)} noted \"$problem\". MedLog suggested calling their doctor today. You may want to check in with them."

    fun missedDose(name: String, time: String, medicine: String, feed: Boolean = false) =
        if (feed) "MedLog: The $time feed ($medicine) for ${n(name)} hasn't been marked as given yet. A quick call may help."
        else "MedLog: ${n(name)} hasn't marked the $time medicine ($medicine) as taken yet. A quick call may help."

    fun takenTwice(name: String, medicine: String) =
        "MedLog: ${n(name)} marked $medicine as taken twice today. You may want to check with them."

    fun noCheckIn(name: String) =
        "MedLog: ${n(name)} hasn't answered the morning check-in yet. A quick call may help."

    fun refill(name: String, text: String) = "MedLog: ${n(name)}: $text Could you help get more?"

    fun message(name: String, text: String, voice: Boolean) =
        "MedLog: ${n(name)} says \"$text\"" + if (voice) " (a voice message, in MedLog on your phone)" else ""

    fun answer(name: String, answer: String) = "${n(name)}: $answer"

    /** Titles on the helper's phone. */
    fun alertTitle(from: String, urgent: Boolean) = if (urgent) "${n(from)} asked for help" else "Message from ${n(from)}"

    // ───────────────────── tone check ─────────────────────

    private val ALARM = listOf(
        "danger", "dangerous", "urgent", "urgently", "emergency", "critical", "severe", "serious", "fatal", "death", "dying", "die",
        "life-threatening", "life threatening", "immediately", "asap", "alarm", "alarming", "warning", "panic", "terrible", "horrible",
        "worst", "overdose", "double dose", "collapse", "collapsed", "unconscious", "help now", "right now", "hurry",
    )

    /** Problems with [text]'s tone; empty when it is calm. */
    fun check(text: String): List<String> {
        val t = text.lowercase()
        val found = ALARM.filter { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(t) }.map { "alarm word \"$it\"" }.toMutableList()
        if ('!' in text) found += "exclamation mark"
        Regex("\\b[A-Z]{4,}\\b").findAll(text).map { it.value }.filter { it !in setOf("MEDLOG") }.forEach { found += "shouting \"$it\"" }
        return found
    }
}
