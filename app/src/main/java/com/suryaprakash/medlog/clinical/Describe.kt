package com.suryaprakash.medlog.clinical

import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Mention
import com.suryaprakash.medlog.nlu.Parsed
import com.suryaprakash.medlog.nlu.fmt1

/**
 * Turns facts into short plain sentences: for the read-back ("Vomited 2 times, after lunch, yellow. No blood."),
 * for the timeline, and for the doctor page.
 */
class Describe(private val cat: Catalogue) {

    /** Plain words for yes/no facts. */
    private val yesNo = mapOf(
        "blood" to ("blood seen" to "no blood"),
        "coffeeGround" to ("looked like coffee grounds" to "not like coffee grounds"),
        "keepWater" to ("can keep water down" to "can't keep water down"),
        "urineToday" to ("passed urine" to "no urine for 8 hours"),
        "projectile" to ("forceful" to "not forceful"),
        "chills" to ("shivering" to "no shivering"),
        "sweating" to ("sweating" to "no sweating"),
        "confusion" to ("confused" to "not confused"),
        "breathless" to ("hard to breathe" to "breathing fine"),
        "atRest" to ("even at rest" to "not at rest"),
        "lyingFlat" to ("worse lying flat" to "not worse lying flat"),
        "armJaw" to ("spreads to arm, jaw or back" to "does not spread"),
        "exertion" to ("comes on walking" to "not with walking"),
        "phlegm" to ("with phlegm" to "no phlegm"),
        "hitHead" to ("hit head" to "did not hit head"),
        "couldGetUp" to ("got up alone" to "could not get up"),
        "dizzyBefore" to ("dizzy before" to "not dizzy before"),
        "worstEver" to ("came on suddenly, the worst ever" to "not the worst ever"),
        "visionChange" to ("vision changed" to "vision fine"),
        "stiffNeck" to ("stiff neck" to "no stiff neck"),
        "faceDroop" to ("face drooping" to "no face drooping"),
        "armWeak" to ("arm or leg weak" to "no weakness"),
        "speech" to ("speech slurred" to "speech fine"),
        "spinning" to ("room spinning" to "not spinning"),
        "onStanding" to ("on standing up" to "not on standing"),
        "itchy" to ("itchy" to "not itchy"),
        "spreading" to ("spreading" to "not spreading"),
        "blisters" to ("blisters" to "no blisters"),
        "newMedicine" to ("new medicine recently" to "no new medicine"),
        "lipSwelling" to ("lips/face swelling" to "no lip or face swelling"),
        "oneSide" to ("one side only" to "both sides"),
        "redHot" to ("red and hot" to "not red or hot"),
        "painful" to ("painful" to "not painful"),
        "burning" to ("burning" to "no burning"),
        "frequent" to ("often" to "not often"),
        "cannotPass" to ("cannot pass urine" to "can pass urine"),
        "bleedingStops" to ("bleeding stops with pressure" to "bleeding does not stop"),
        "bloodThinner" to ("on a blood thinner" to "no blood thinner"),
        "choking" to ("choking" to "not choking"),
        "fits" to ("had a fit" to "no fit"),
        "lostConsciousness" to ("passed out" to "did not pass out"),
        "swallowWater" to ("can swallow water" to "can't swallow water"),
        "mucus" to ("mucus" to "no mucus"),
        "blackStool" to ("black stool" to "stool not black"),
        "selfHarm" to ("thoughts of self-harm" to "no thoughts of self-harm"),
        "better" to ("getting better" to "not better"),
    )

    private val verb = mapOf(
        "vomiting" to "Vomited", "loose_motions" to "Loose motions", "cough" to "Coughed", "sneeze" to "Sneezed",
        "fainted" to "Fainted", "fall" to "Fell", "burp" to "Burped", "nosebleed" to "Nose bled",
    )

    /** One fact as words. */
    fun fact(key: String, f: Fact): String? {
        val v = f.value
        yesNo[key]?.let { (y, n) -> return if (v == true) y else if (v == false) n else null }
        return when (key) {
            "count" -> null // folded into the headline
            "severity" -> (v as? Number)?.let { "${severityWord(it.toInt())}, ${it} out of 10" }
            "pin", "_audio" -> null
            "temperature" -> (v as? Number)?.let { "${fmt1(it.toDouble())} °F" }
            "side" -> "$v side".replace("middle side", "middle").replace("both side", "both sides")
            "character" -> (v as? List<*>)?.joinToString(", ")
            "colour" -> "$v"
            "content" -> "$v"
            "stoolType" -> "$v"
            "onset" -> if (v == "sudden") "started suddenly" else "started slowly"
            "pattern" -> "$v"
            "radiation" -> "spreads to $v"
            "worse" -> "worse with $v"
            "better" -> "better with $v"
            "context" -> "$v"
            "duration" -> "$v"
            "weeks" -> "for $v weeks"
            "pillows" -> "needs $v pillows"
            "hours" -> "$v hours"
            "timeOnFloor" -> "$v minutes on the floor"
            "sleepHours" -> "slept $v hours"
            "reading" -> "reading $v"
            "dryWet" -> "$v"
            "impact" -> (v as? List<*>)?.let { "stopped: ${it.joinToString(", ")}" }
            "note" -> "$v"
            "burnDepth" -> "burn: $v"
            "burnSize" -> "$v"
            "depth" -> "$v"
            "site" -> "$v".lowercase()
            "started" -> "started ${"$v".lowercase()}"
            else -> "${cat.field(key)?.label ?: key}: $v"
        }
    }

    fun severityWord(n: Int) = when {
        n <= 0 -> "None"
        n <= 3 -> "A little"
        n <= 5 -> "Some"
        n <= 7 -> "Bad"
        n <= 9 -> "Very bad"
        else -> "Worst"
    }

    /** "Vomited 2 times" / "Headache" / "Chest pain" */
    fun headline(problemId: String, facts: Map<String, Fact>): String {
        val p = cat.problem(problemId)
        val count = (facts["count"]?.value as? Number)?.toInt()
        val base = if (count != null) verb[problemId] ?: p?.label ?: problemId else p?.label ?: problemId
        return if (count != null) "$base $count ${if (count == 1) "time" else "times"}" else base
    }

    /** Full line for one problem: "Vomited 2 times, after lunch, yellow, no blood". */
    fun line(problemId: String, facts: Map<String, Fact>, order: List<String>? = null): String {
        val keys = (order ?: cat.problem(problemId)?.fields ?: emptyList()).let { o -> o + facts.keys.filter { it !in o } }
        val parts = keys.mapNotNull { k -> facts[k]?.let { fact(k, it) } }.filter { it.isNotBlank() }
        return (listOf(headline(problemId, facts)) + parts).joinToString(", ")
    }

    /** The read-back sentence spoken and shown on the check screen. */
    fun readBack(parsed: Parsed): String {
        val main = parsed.main ?: return "I didn't catch a problem."
        val sb = StringBuilder(line(main.problemId, main.facts)).append('.')
        val also = parsed.others
        if (also.isNotEmpty()) sb.append(" Also: ").append(also.joinToString("; ") { line(it.problemId, it.facts) }).append('.')
        val no = parsed.negatives.filter { it.problemId != main.problemId }
        if (no.isNotEmpty()) sb.append(" No ").append(no.joinToString(", ") { cat.problem(it.problemId)?.label?.lowercase() ?: it.problemId }).append('.')
        parsed.readings.filter { it.type != "temp" }.takeIf { it.isNotEmpty() }?.let { r -> sb.append(' ').append(r.joinToString(", ") { it.label() }).append('.') }
        if (parsed.medicinesTaken.isNotEmpty()) sb.append(" Took ").append(parsed.medicinesTaken.joinToString(", ")).append('.')
        return sb.toString().replaceFirstChar { it.uppercase() }
    }

    /** Short chips shown on the check screen, so each fact can be fixed or removed on its own. */
    fun chips(m: Mention): List<Pair<String, String>> {
        val p = cat.problem(m.problemId)
        val keys = (p?.fields ?: emptyList()).let { o -> o + m.facts.keys.filter { it !in o } }
        return keys.mapNotNull { k -> m.facts[k]?.let { f -> (if (k == "count") "${(f.value as Number).toInt()} times" else fact(k, f))?.let { k to it } } }
    }
}

/** Picks the follow-up questions that matter (plan 8.4): max 2, danger first, never already answered. */
object FollowUps {
    fun pick(cat: Catalogue, problemId: String, facts: Map<String, Fact>, recentlyAsked: Set<String> = emptySet(), max: Int = 2): List<Question> {
        val p = cat.problem(problemId) ?: return emptyList()
        return p.followUps.mapNotNull { cat.questions[it] }
            .filter { q -> !facts.containsKey(q.field) && q.id !in recentlyAsked }
            .filterNot { q -> q.field == "temperature" && facts.containsKey("temperature") }
            .sortedByDescending { it.priority }
            .take(max)
    }
}
