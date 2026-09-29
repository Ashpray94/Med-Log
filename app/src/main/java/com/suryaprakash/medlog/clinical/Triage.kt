package com.suryaprakash.medlog.clinical

import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Reading
import kotlin.math.nextDown
import kotlin.math.nextUp

enum class Level { GREEN, AMBER, RED }

/**
 * What the app tells the person after a note. [reasons] are plain words for the doctor page;
 * [say] is what the person sees and hears.
 */
data class Triage(
    val level: Level,
    val say: String,
    val reasons: List<String>,
    /** Calm mental-health screen instead of an emergency screen. */
    val mentalHealth: Boolean = false,
    /** Specific first aid, e.g. "Take sugar now". */
    val firstAid: String? = null,
    val careTips: List<String> = emptyList(),
    /**
     * "bp" or "spo2": the person is over 55 and their helper has not set the limits yet, so the numbers alone
     * were not judged. Shown as a quiet grey line, never as a warning, and never sent to helpers.
     */
    val needsLimit: String? = null,
) {
    companion object { val OK = Triage(Level.GREEN, "Saved.", emptyList()) }
}

/** A past note, reduced to what the rules need. */
data class RecentNote(val problemId: String?, val at: Long, val facts: Map<String, Fact>, val count: Int?)

data class PersonContext(
    val ageYears: Int?,
    val onBloodThinner: Boolean,
    val conditions: String = "",
    /** the helper's own lines for this person (see Limits.kt) */
    val limits: Limits = Limits(),
    /** cancer, and on chemotherapy or radiotherapy: the doctor's numbers are stricter */
    val cancerCare: Boolean = false,
)

/**
 * Danger-sign rules (plan section 10). Deterministic, not AI.
 * Recall first: when in doubt, the rule raises the level. Emergency screens never ask questions first.
 *
 * VERSION must change with every rule change; the doctor page prints it.
 * NOT YET CLINICIAN-REVIEWED — see catalogue "reviewNote".
 */
object DangerRules {
    const val VERSION = "rules-0.2.0-unreviewed"

    /** Cancer AND chemotherapy or radiotherapy (conditions is the comma list from the profile). */
    fun cancerCareOf(conditions: String, treatments: List<String>): Boolean =
        conditions.contains("Cancer", ignoreCase = true) && treatments.any { it == "Chemotherapy" || it == "Radiotherapy" }

    /** Fahrenheit for a temperature typed in °C (34–43); anything else is returned unchanged. One decimal. */
    fun toFahrenheit(v: Double): Double = if (v in 34.0..43.0) Math.round((v * 9 / 5 + 32) * 10) / 10.0 else v

    private fun yes(f: Map<String, Fact>, k: String) = f[k]?.value == true
    private fun no(f: Map<String, Fact>, k: String) = f[k]?.value == false
    private fun num(f: Map<String, Fact>, k: String): Double? = when (val v = f[k]?.value) {
        is Number -> v.toDouble(); is String -> v.toDoubleOrNull(); else -> null
    }

    fun evaluate(
        problemId: String?,
        facts: Map<String, Fact>,
        readings: List<Reading>,
        recent: List<RecentNote>,
        person: PersonContext,
        now: Long = System.currentTimeMillis(),
    ): Triage {
        val red = ArrayList<String>()
        val amber = ArrayList<String>()
        var firstAid: String? = null
        val elderly = (person.ageYears ?: 0) >= 65
        val over55 = (person.ageYears ?: 0) > 55
        val thinner = person.onBloodThinner || yes(facts, "bloodThinner")
        val lim = person.limits
        var needsLimit: String? = null

        // ── mental health: calm, caring, never an alarm ──
        if (problemId == "self_harm" || yes(facts, "selfHarm")) {
            return Triage(Level.RED, "You matter. Please talk to someone now.", listOf("Thoughts of self-harm"), mentalHealth = true)
        }

        // ── problems that are danger signs by themselves ──
        when (problemId) {
            "fainted" -> red += "Fainted / passed out"
            "fits" -> red += "Fit / seizure"
            "face_droop", "speech_trouble", "one_side_weak" -> red += "Possible stroke sign (face, arm or speech)"
            "vision_loss" -> red += "Sudden loss of vision"
            "vomit_blood" -> red += "Vomiting blood"
            "black_stool" -> red += "Black, tarry stool"
            "cough_blood" -> red += "Coughing blood"
            "choking" -> red += "Choking"
            "broken_bone" -> red += "Possible broken bone"
            "confusion" -> red += "New confusion"
        }

        // ── stroke (FAST) ──
        if (yes(facts, "faceDroop") || yes(facts, "armWeak") || yes(facts, "speech")) red += "Possible stroke sign (face, arm or speech)"

        // ── heart ──
        if (problemId in setOf("chest_pain", "chest_tight")) {
            val sev = num(facts, "severity") ?: 0.0
            when {
                yes(facts, "armJaw") || yes(facts, "sweating") || yes(facts, "breathless") -> red += "Chest pain with spreading pain, sweating or breathlessness"
                sev >= 7 -> red += "Severe chest pain"
                else -> amber += "Chest pain: must be checked by a doctor today"
            }
        }

        // ── breathing ──
        if (yes(facts, "breathless") && yes(facts, "atRest")) red += "Breathless even at rest"
        if (problemId == "breathless" && yes(facts, "atRest")) red += "Breathless even at rest"
        if (yes(facts, "lipSwelling")) red += "Swelling of lips, face or tongue (possible severe allergy)"
        if (problemId == "allergic_reaction" && yes(facts, "breathless")) red += "Allergic reaction with breathing trouble"
        if (yes(facts, "choking")) red += "Choking"

        // ── bleeding ──
        if (yes(facts, "blood") && problemId in setOf("vomiting", "nausea")) red += "Blood in vomit"
        if (yes(facts, "blood") && problemId == "cough") red += "Coughing blood"
        if (yes(facts, "coffeeGround")) red += "Vomit looks like coffee grounds"
        if (yes(facts, "blackStool")) red += "Black, tarry stool"
        if (no(facts, "bleedingStops")) red += "Bleeding that does not stop with pressure"

        // ── head ──
        if (yes(facts, "worstEver")) red += "Sudden, worst-ever headache"
        if (problemId in setOf("headache", "migraine") && yes(facts, "visionChange")) red += "Headache with change in vision"
        if (yes(facts, "stiffNeck") && (num(facts, "temperature") ?: 0.0) >= 100.4) red += "Fever with stiff neck"
        if (yes(facts, "fits")) red += "Fit / seizure"
        if (yes(facts, "lostConsciousness")) red += "Passed out"

        // ── falls ──
        if (problemId in setOf("fall", "head_injury")) {
            if (yes(facts, "hitHead") && thinner) red += "Hit head while on a blood thinner"
            if (problemId == "head_injury" && thinner) red += "Head injury while on a blood thinner"
            if (no(facts, "couldGetUp")) red += "Could not get up after a fall"
            if ((num(facts, "timeOnFloor") ?: 0.0) >= 60) red += "On the floor for an hour or more"
            if (red.isEmpty()) amber += "A fall: tell the doctor today" + if (yes(facts, "hitHead")) " (hit head)" else ""
        }

        // ── temperature ──
        val temp = num(facts, "temperature") ?: readings.firstOrNull { it.type == "temp" }?.v1
        if (temp != null) {
            val th = lim.band("temp")
            // the helper's lines replace the general ones one by one; 104 °F is a red line nobody can raise
            val tb = effective(th, Band(amberHigh = if (elderly) 100.4 else 102.0, redHigh = if (person.cancerCare) 100.0 else 104.0))
            val redAt = minOf(tb.redHigh ?: 104.0, 104.0)
            val amberAt = tb.amberHigh ?: Double.MAX_VALUE
            when {
                // always, whatever the helper set
                temp <= 95.0 -> red += "Very low body temperature (${fmt(temp)} °F)"
                temp >= 104.0 -> red += "Very high fever (${fmt(temp)} °F)"
                temp >= 100.4 && yes(facts, "confusion") -> red += "Fever with confusion"
                temp >= redAt -> red += if (th?.redHigh != null) "Fever of ${fmt(temp)} °F (above the limit set for you)" else "Fever during cancer treatment (${fmt(temp)} °F)"
                temp >= amberAt -> amber += when {
                    th?.amberHigh != null -> "Fever of ${fmt(temp)} °F (above the limit set for you)"
                    elderly && temp < 102.0 -> "Fever of ${fmt(temp)} °F in an older person"
                    else -> "High fever (${fmt(temp)} °F)"
                }
            }
        }
        // no thermometer, or the question was skipped: for a person on cancer treatment a fever or shivering is not left as "Saved"
        if (temp == null && person.cancerCare && problemId in setOf("chills", "fever"))
            amber += "Check your temperature. If you can't, call your cancer doctor today."
        if (yes(facts, "confusion") && problemId != "confusion" && "Fever with confusion" !in red) red += "Confusion"

        // ── readings ──
        for (r in readings) when (r.type) {
            "sugar" -> {
                // helper lines first, the general ones for any line the helper left unset ("> 300" is 300.0.nextUp() with ">=")
                val b = effective(lim.band("sugar"), Band(amberLow = 70.0.nextDown(), redLow = 54.0.nextDown(), amberHigh = 300.0.nextUp(), redHigh = 400.0))
                val v = r.v1
                when {
                    b.redLow != null && v <= b.redLow -> { red += "Very low sugar (${v.toInt()})"; firstAid = SUGAR_AID }
                    b.amberLow != null && v <= b.amberLow && (yes(facts, "confusion") || yes(facts, "sweating")) -> { red += "Low sugar (${v.toInt()}) with sweating or confusion"; firstAid = SUGAR_AID }
                    b.amberLow != null && v <= b.amberLow -> { amber += "Low sugar (${v.toInt()})"; firstAid = SUGAR_AID }
                    b.redHigh != null && v >= b.redHigh -> red += "Very high sugar (${v.toInt()})"
                    b.amberHigh != null && v >= b.amberHigh -> amber += "High sugar (${v.toInt()})"
                }
            }
            "spo2" -> {
                val h = lim.band("spo2")
                // over 55 the person may live with a lower baseline: 90 is only AMBER and red comes only from the helper
                val b = effective(h, if (over55) Band(amberLow = 90.0.nextDown()) else Band(amberLow = 94.0.nextDown(), redLow = 90.0.nextDown()))
                if (over55 && h?.redLow == null) needsLimit = needsLimit ?: "spo2"
                when {
                    b.redLow != null && r.v1 <= b.redLow -> red += "Low oxygen (${r.v1.toInt()}%)"
                    b.amberLow != null && r.v1 <= b.amberLow -> amber += if (over55) "Low oxygen (${r.v1.toInt()}%): please call your doctor" else "Oxygen a little low (${r.v1.toInt()}%)"
                }
            }
            "bp" -> {
                val s = r.v1; val d = r.v2 ?: 0.0
                val symptoms = problemId in setOf("headache", "chest_pain", "chest_tight", "breathless", "blurred_vision", "confusion", "one_side_weak", "face_droop") ||
                    yes(facts, "visionChange") || yes(facts, "breathless") || yes(facts, "confusion")
                val hs = lim.band("bpSys"); val hd = lim.band("bpDia")
                // over 55, never assume: only the helper's lines count (an unset line is no alarm). 55 or younger: an unset line uses today's number
                val bs = effective(hs, if (over55) Band() else Band(amberHigh = 180.0, amberLow = 90.0.nextDown()))
                val bd = effective(hd, if (over55) Band() else Band(amberHigh = 110.0))
                if (over55 && hs?.amberHigh == null && hs?.redHigh == null && hd?.amberHigh == null && hd?.redHigh == null) needsLimit = needsLimit ?: "bp"
                val text = "${s.toInt()}/${d.toInt()}"
                when {
                    // high BP with these symptoms is about the symptoms, so it stays for everyone
                    (s >= 180 || d >= 120) && symptoms -> red += "Very high BP ($text) with symptoms"
                    (bs.redHigh != null && s >= bs.redHigh) || (bd.redHigh != null && d >= bd.redHigh) -> red += "Very high BP ($text)"
                    (bs.redLow != null && s <= bs.redLow) || (bd.redLow != null && d <= bd.redLow) -> red += "Very low BP ($text)"
                    (bs.amberHigh != null && s >= bs.amberHigh) || (bd.amberHigh != null && d >= bd.amberHigh) -> amber += "High BP ($text)"
                    (bs.amberLow != null && s <= bs.amberLow) || (bd.amberLow != null && d <= bd.amberLow) ->
                        amber += if (problemId in setOf("dizzy", "fainted", "low_bp") && hs?.amberLow == null) "Low BP ($text) with dizziness" else "Low BP ($text)"
                }
            }
            "pulse" -> {
                val b = effective(lim.band("pulse"), Band(amberLow = 40.0.nextDown(), amberHigh = 130.0))
                when {
                    (b.redLow != null && r.v1 <= b.redLow) || (b.redHigh != null && r.v1 >= b.redHigh) -> red += "Pulse ${r.v1.toInt()}"
                    (b.amberLow != null && r.v1 <= b.amberLow) || (b.amberHigh != null && r.v1 >= b.amberHigh) -> amber += "Pulse ${r.v1.toInt()}"
                }
            }
        }

        // ── fluids: vomiting / loose motions ──
        val day = 24 * 3600_000L
        if (problemId in setOf("vomiting", "loose_motions")) {
            // [recent] never holds the note being evaluated (Repo.recentForRules leaves it out), so its count is added once, here
            // an earlier note with no count and no answers (an abandoned tap) is not one vomit
            val last24 = recent.filter { it.problemId == problemId && now - it.at <= day && !isEmptyNote(it.count, it.facts) }.sumOf { it.count ?: 1 } +
                (num(facts, "count") ?: 1.0).toInt()
            val what = if (problemId == "vomiting") "Vomiting" else "Loose motions"
            val fh = lim.band(if (problemId == "vomiting") "vomit" else "loose")
            // helper lines first; an unset line uses the general number (more than 5, or more than 4 in cancer care)
            val fb = effective(fh, Band(amberHigh = if (person.cancerCare) 4.0 else 5.0))
            when {
                fb.redHigh != null && last24 > fb.redHigh -> red += "$what $last24 times in 24 hours"
                fb.amberHigh != null && last24 > fb.amberHigh ->
                    amber += if (fh?.amberHigh == null && person.cancerCare) "$what $last24 times in 24 hours during cancer treatment: call your doctor" else "$what $last24 times in 24 hours"
            }
            if (no(facts, "keepWater")) amber += "Cannot keep water down"
            if (no(facts, "urineToday")) amber += "No urine for 8 hours or more"
            if (problemId == "loose_motions" && yes(facts, "blood")) amber += "Blood in stool"
            if (no(facts, "keepWater") && no(facts, "urineToday") && elderly) red += "Cannot keep water down and no urine (dehydration risk)"
        }
        if (problemId == "no_urine" || yes(facts, "cannotPass")) amber += "Cannot pass urine: needs a doctor soon"

        // ── constipation: days without a motion ──
        if (problemId == "constipation") {
            val days = num(facts, "daysNoMotion")
            val ch = lim.band("constipationDays")
            val cb = effective(ch, Band(amberHigh = if (person.cancerCare) 2.0 else null))
            if (days != null) when {
                cb.redHigh != null && days > cb.redHigh -> red += "No motion for ${fmt(days)} days"
                cb.amberHigh != null && days > cb.amberHigh ->
                    amber += if (ch?.amberHigh == null) "No motion for ${fmt(days)} days during cancer treatment: call your doctor" else "No motion for ${fmt(days)} days"
            }
        }

        // ── fever lasting ──
        if (problemId == "fever") {
            val feverNotes = recent.filter { it.problemId == "fever" }
            val first = feverNotes.minOfOrNull { it.at }
            if (first != null && now - first >= 2 * day) amber += "Fever for more than 2 days"
        }

        // ── other "see a doctor soon" signs ──
        when (problemId) {
            "swollen_ankles", "leg_pain", "swelling" -> if (yes(facts, "oneSide") && (yes(facts, "painful") || yes(facts, "redHot") || problemId != "swelling")) amber += "Swelling of one leg (needs checking for a clot)"
            "cough" -> if ((num(facts, "weeks") ?: 0.0) >= 3) amber += "Cough for 3 weeks or more"
            "blood_urine" -> amber += "Blood in urine"
            "blood_stool" -> amber += "Blood in stool"
            "postmeno_bleeding" -> amber += "Bleeding after menopause"
            "breast_lump", "lump" -> amber += "A new lump"
            "jaundice" -> amber += "Yellow eyes or skin"
            "swallowing" -> if (no(facts, "swallowWater")) red += "Cannot swallow water" else amber += "Difficulty swallowing"
            "weight_loss" -> amber += "Losing weight"
            "rash", "hives" -> if (yes(facts, "newMedicine")) amber += "Rash after a new medicine"
            "wound_not_healing" -> if ((num(facts, "temperature") ?: 0.0) >= 100.4 || yes(facts, "redHot")) amber += "Wound with fever or redness"
            "hallucination" -> amber += "Seeing or hearing things"
            "heavy_bleeding" -> amber += "Heavy bleeding"
            "near_fall" -> amber += "Nearly fell"
            "bite" -> amber += "Animal bite: needs a doctor today (injections may be needed)"
            "burn", "sunburn" -> {
                val depth = facts["burnDepth"]?.value as? String
                val size = facts["burnSize"]?.value as? String
                val site = (facts["site"]?.value as? String).orEmpty().lowercase()
                when {
                    depth == "white, brown or black" -> red += "Deep burn (white, brown or black skin)"
                    size == "bigger than a palm" && elderly -> red += "Large burn in an older person"
                    size == "bigger than a palm" -> amber += "Burn bigger than a palm"
                    depth == "blisters" || yes(facts, "blisters") -> amber += "Burn with blisters"
                }
                if (listOf("face", "eye", "mouth", "hand", "foot", "throat", "neck").any { site.contains(it) }) amber += "Burn on the face, hands, feet or neck"
            }
            "bleeding", "nosebleed", "cut" -> if (thinner) amber += "Bleeding while on a blood thinner"
        }
        if (problemId == "dizzy") {
            // [recent] leaves out the note being evaluated: it is the "+ 1"
            val n = recent.count { it.problemId == "dizzy" && now - it.at <= 2 * day } + 1
            if (n >= 3) amber += "Dizzy $n times in 2 days"
        }
        if (problemId == "chest_pain" && person.conditions.contains("heart", true) && red.isEmpty()) red += "Chest pain in a person with heart disease"

        val tips = careTips(problemId, facts)
        return when {
            red.isNotEmpty() -> Triage(Level.RED, if (firstAid != null) "Your sugar is too low. Take sugar now." else "This could be serious. Get help now.", red.distinct(), firstAid = firstAid, careTips = tips, needsLimit = needsLimit)
            amber.isNotEmpty() -> Triage(Level.AMBER, "Please call your doctor today.", amber.distinct(), firstAid = firstAid, careTips = tips, needsLimit = needsLimit)
            else -> Triage(Level.GREEN, "Saved.", emptyList(), careTips = tips, needsLimit = needsLimit)
        }
    }

    /** No count and nothing answered (only "when did it start", or nothing): the note was opened and left. */
    fun isEmptyNote(count: Int?, facts: Map<String, Fact>): Boolean =
        count == null && facts.keys.none { it != "started" && !it.startsWith("_") }

    const val SUGAR_AID = "Take 3 teaspoons of sugar in water, or half a glass of juice. Check again in 15 minutes."

    private fun careTips(problemId: String?, facts: Map<String, Fact>): List<String> = when (problemId) {
        "vomiting", "loose_motions" -> listOf("Take small sips of water or ORS often.", "Call your doctor if you can't keep water down.")
        "fever" -> listOf("Drink plenty of water.", "Check your temperature again in 4 hours.")
        "dizzy" -> listOf("Sit or lie down until it passes.", "Stand up slowly.")
        "cough", "sore_throat", "runny_nose" -> listOf("Warm drinks can help.")
        "fall", "near_fall" -> listOf("Rest. Tell someone you fell.")
        "cant_sleep" -> listOf("Try to go to bed at the same time each night.")
        "constipation" -> listOf("Drink water and eat fruit or vegetables.")
        "acidity" -> listOf("Avoid lying down soon after eating.")
        else -> emptyList()
    }

    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(java.util.Locale.US, "%.1f", d)
}
