package com.suryaprakash.medlog.doctor

import com.suryaprakash.medlog.data.planned
import com.suryaprakash.medlog.clinical.Catalogue
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Describe
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.HOUR
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.data.Note
import com.suryaprakash.medlog.data.Profile
import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Source
import com.suryaprakash.medlog.nlu.factsFromJson
import com.suryaprakash.medlog.nlu.fmt1
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * The doctor page (plan 11): most important first, exact numbers, the patient's own words,
 * negatives that matter, and anything the app worked out marked ◇.
 */
data class Summary(
    val title: String,
    val header: List<String>,
    val alertLine: String,
    val concerns: List<Concern>,
    val days: List<LocalDate>,
    val timeline: List<TimelineRow>,
    val details: List<String>,
    val medicines: List<MedRow>,
    val readings: List<String>,
    val foodWater: List<String>,
    val questions: List<String>,
    val allNotes: List<String>,
    val footer: String,
) {
    data class Concern(val level: String, val text: String, val sub: List<String>)
    data class TimelineRow(val label: String, val cells: List<Pair<Int, String>>)   // count, level
    data class MedRow(val name: String, val dose: String, val taken: String, val note: String)
}

class SummaryBuilder(private val cat: Catalogue, private val describe: Describe) {
    private val zone = ZoneId.systemDefault()
    private val dfmt = SimpleDateFormat("d MMM", Locale.getDefault())
    private val tfmt = SimpleDateFormat("d MMM h:mm a", Locale.getDefault())
    private fun day(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
    private fun d(t: Long) = dfmt.format(Date(t))

    fun build(profile: Profile, from: Long, to: Long, notes: List<Note>, meds: List<Medicine>, doses: List<Dose>, waterGoal: Int, now: Long = System.currentTimeMillis()): Summary {
        val symptoms = notes.filter { it.kind == Kind.SYMPTOM && it.problemId != null }
        val facts = symptoms.associate { it.id to factsFromJson(it.details) }
        val byProblem = symptoms.groupBy { it.problemId!! }
        val days = generateSequence(day(from)) { it.plusDays(1) }.takeWhile { !it.isAfter(day(to - 1)) }.toList().takeLast(14)

        // ── header ──
        val age = com.suryaprakash.medlog.data.Repo.ageFromDob(profile.dob)
        val header = listOfNotNull(
            listOfNotNull(profile.name.ifBlank { "Patient" }, profile.sex.takeIf { it.isNotBlank() }, age?.let { "$it y" }, profile.dob.take(4).takeIf { it.length == 4 }?.let { "Born $it" },
                profile.bloodGroup.takeIf { it.isNotBlank() }?.let { "Blood $it" }, profile.hospitalId.takeIf { it.isNotBlank() }?.let { "ID $it" }).joinToString(" · "),
            profile.conditions.takeIf { it.isNotBlank() }?.let { "Conditions: $it" },
            if (profile.onBloodThinner || meds.any { it.bloodThinner && it.active }) "On a blood thinner" else null,
        )
        val alertLine = profile.allergies.takeIf { it.isNotBlank() }?.let { "ALLERGIES: $it" }.orEmpty()

        // ── concerns, ranked ──
        val concerns = ArrayList<Pair<Int, Summary.Concern>>()
        for ((pid, list) in byProblem) {
            val label = cat.problem(pid)?.label ?: pid
            val total = list.sumOf { it.count ?: 1 }
            val level = when { list.any { it.triage == "RED" } -> "RED"; list.any { it.triage == "AMBER" } -> "AMBER"; else -> "GREEN" }
            val perDay = days.map { dd -> list.filter { day(it.occurredAt) == dd }.sumOf { it.count ?: 1 } }
            val active = perDay.filter { it > 0 }
            val trend = if (active.size >= 3 && active.last() > active.first()) ", increasing (${active.first()}→${active.last()}/day)" else if (active.size >= 3 && active.last() < active.first()) ", decreasing (${active.first()}→${active.last()}/day)" else ""
            val span = "${d(list.minOf { it.occurredAt })}–${d(list.maxOf { it.occurredAt })}"
            val keyFacts = LinkedHashSet<String>()
            list.sortedBy { it.occurredAt }.forEach { n ->
                facts[n.id]!!.forEach { (k, f) ->
                    val field = cat.field(k)
                    if (field?.danger == true || k in setOf("keepWater", "urineToday", "temperature", "severity", "colour", "bloodThinner")) {
                        describe.fact(k, f)?.let { w -> keyFacts += w + (if (f.source == Source.ASKED) " (asked ${d(n.occurredAt)})" else if (f.value == true && field?.danger == true) " (${d(n.occurredAt)})" else "") + mark(f) }
                    }
                }
            }
            val reasons = list.flatMap { it.triageReasons.lines() }.filter { it.isNotBlank() }.distinct()
            val text = "$label ${if (total > 1) "$total× " else ""}($span)$trend"
            val sub = (reasons.map { "Flag: $it" } + keyFacts.take(6)).distinct()
            val score = when (level) { "RED" -> 3000; "AMBER" -> 2000; else -> 0 } + total * 10 + (if (trend.contains("increasing")) 100 else 0)
            concerns += score to Summary.Concern(level, text, sub)
        }
        // missed medicines
        for (m in meds) {
            val md = doses.filter { it.medicineId == m.id && it.scheduledAt in from until minOf(to, now) }
            val missed = md.count { it.status == DoseStatus.MISSED || it.status == DoseStatus.SKIPPED }
            if (md.isNotEmpty() && missed > 0 && (missed * 5 >= md.size || m.critical)) {
                val why = md.mapNotNull { com.suryaprakash.medlog.data.reasonWords(it.reason) }.groupingBy { it }.eachCount().entries.joinToString { "${it.key} ×${it.value}" }
                concerns += (if (m.critical) 2500 else 1500) to Summary.Concern(if (m.critical) "AMBER" else "GREEN", "Missed ${m.name}: $missed of ${md.size} doses${if (why.isNotBlank()) " ($why)" else ""}", emptyList())
            }
        }
        // readings out of range
        val readings = notes.filter { it.kind == Kind.READING }.mapNotNull { n -> runCatching { JSONObject(n.details) }.getOrNull()?.let { n to it } }
        for ((n, r) in readings) {
            val t = DangerRules.evaluate(null, emptyMap(), listOf(com.suryaprakash.medlog.nlu.Reading(r.getString("type"), r.getDouble("v1"), r.optDouble("v2").takeIf { !it.isNaN() })), emptyList(),
                com.suryaprakash.medlog.clinical.PersonContext(age, false))
            if (t.level.name != "GREEN") concerns += (if (t.level.name == "RED") 2900 else 1900) to Summary.Concern(t.level.name, "${n.text} (${tfmt.format(Date(n.occurredAt))})", t.reasons)
        }
        // patterns ◇
        Patterns.find(cat, symptoms, facts, meds, doses, notes, days, zone).forEach { concerns += 1800 to Summary.Concern("PATTERN", "◇ $it", emptyList()) }

        // ── timeline ──
        val timeline = byProblem.entries.sortedByDescending { e -> e.value.sumOf { it.count ?: 1 } }.take(8).map { (pid, list) ->
            Summary.TimelineRow(cat.problem(pid)?.label ?: pid, days.map { dd ->
                val on = list.filter { day(it.occurredAt) == dd }
                val c = on.sumOf { it.count ?: 1 }
                val sev = on.mapNotNull { it.severity }.maxOrNull() ?: 0
                val lvl = when { on.any { it.triage == "RED" } || sev >= 8 -> "RED"; on.any { it.triage == "AMBER" } || sev >= 5 -> "AMBER"; c > 0 -> "GREEN"; else -> "" }
                c to lvl
            })
        }

        // ── details per problem ──
        val details = byProblem.entries.sortedByDescending { e -> e.value.size }.take(8).map { (pid, list) ->
            val merged = LinkedHashMap<String, MutableSet<String>>()
            list.forEach { n -> facts[n.id]!!.forEach { (k, f) -> if (k !in setOf("count")) describe.fact(k, f)?.let { merged.getOrPut(k) { LinkedHashSet() } += it + mark(f) } } }
            val context = list.mapNotNull { facts[it.id]!!["context"]?.value as? String }
            val afterMeals = context.count { Regex("after (breakfast|lunch|dinner|food|eating|meals?)").containsMatchIn(it) }
            val parts = merged.filterKeys { it != "context" }.values.map { it.joinToString(" / ") }.toMutableList()
            if (afterMeals > 0) parts += "after meals ($afterMeals/${list.size})" else if (context.isNotEmpty()) parts += context.distinct().take(3).joinToString(", ")
            val quote = list.mapNotNull { it.transcript }.filter { it.split(" ").size >= 4 }.maxByOrNull { it.length }?.let { "“${it.take(110)}”" }
            val linked = list.mapNotNull { it.groupId }.flatMap { g -> symptoms.filter { it.groupId == g && it.problemId != pid } }.mapNotNull { cat.problem(it.problemId)?.label }.distinct()
            (cat.problem(pid)?.label ?: pid) + (if (parts.isNotEmpty()) ": " + parts.joinToString("; ") else "") +
                (if (linked.isNotEmpty()) "; with ${linked.joinToString(", ")}" else "") + (quote?.let { " $it" } ?: "")
        }

        // ── medicines ──
        val medRows = meds.filter { it.active || it.changedAt >= from }.map { m ->
            val md = doses.planned().filter { it.medicineId == m.id && it.scheduledAt in from until minOf(to, now) }
            val taken = md.count { it.status == DoseStatus.TAKEN }
            val prn = notes.count { it.kind == Kind.MED_TAKEN && runCatching { JSONObject(it.details).optString("name") }.getOrNull() == m.name }
            val times = m.times.split(",").count { it.isNotBlank() }
            val freq = when { m.asNeeded -> "when needed"; times == 1 -> "once a day"; times == 2 -> "twice a day"; times == 3 -> "3 times a day"; else -> "$times times a day" }
            Summary.MedRow(
                "${m.name}${if (m.critical) " *" else ""}",
                "${m.strength} ${m.amount} $freq".trim(),
                if (m.asNeeded) (if (prn > 0) "taken $prn time${if (prn == 1) "" else "s"}" else "") else if (md.isEmpty()) "" else "$taken/${md.size} (${taken * 100 / md.size}%)",
                listOfNotNull(
                    m.changeNote.takeIf { it.isNotBlank() && it != "started" && m.changedAt >= from }?.let { "$it ${d(m.changedAt)}" },
                    if (m.changeNote == "started" && m.startDate >= from) "started ${d(m.startDate)}" else null,
                    if (!m.active) "stopped" else null,
                    md.mapNotNull { com.suryaprakash.medlog.data.reasonWords(it.reason) }.distinct().takeIf { it.isNotEmpty() }?.joinToString(prefix = "skipped: "),
                ).joinToString("; "),
            )
        } + notes.filter { it.kind == Kind.MED_TAKEN }.mapNotNull { runCatching { JSONObject(it.details).optString("name") }.getOrNull() }
            .filter { n -> meds.none { it.name == n } }.groupingBy { it }.eachCount().map { (n, c) -> Summary.MedRow(n, "self-taken", "${c}×", "not on medicine list") }

        // ── readings ──
        val readingLines = readings.groupBy { it.second.getString("type") }.map { (type, list) ->
            val sorted = list.sortedBy { it.first.occurredAt }
            val last = sorted.last()
            val avg = sorted.map { it.second.getDouble("v1") }.average()
            val lastText = last.first.text
            if (sorted.size >= 2) "$lastText (${d(last.first.occurredAt)}); average ${if (type == "bp") "${avg.toInt()}/${sorted.map { it.second.optDouble("v2", 0.0) }.average().toInt()}" else fmt1(avg)} over ${sorted.size}"
            else "$lastText (${d(last.first.occurredAt)})"
        }

        // ── food & water ──
        val water = days.map { dd -> notes.filter { it.kind == Kind.WATER && day(it.occurredAt) == dd }.sumOf { it.count ?: 1 } }
        val loggedDays = water.count { it > 0 }
        val foodWater = ArrayList<String>()
        if (loggedDays > 0) {
            val low = days.zip(water).filter { it.second in 1 until (waterGoal / 2) }
            foodWater += "Water: average ${"%.1f".format(water.filter { it > 0 }.average())} glasses/day (goal $waterGoal)" + if (low.isNotEmpty()) "; low on ${low.joinToString { d(it.first.atStartOfDay(zone).toInstant().toEpochMilli()) }}" else ""
        }
        byProblem["no_appetite"]?.let { foodWater += "Poor appetite ${it.size}× (${d(it.minOf { n -> n.occurredAt })}–${d(it.maxOf { n -> n.occurredAt })})" }
        val meals = notes.count { it.kind == Kind.FOOD }
        if (meals > 0) foodWater += "$meals meals logged"

        val questions = notes.filter { it.kind == Kind.QUESTION }.mapNotNull { it.transcript }
        val allNotes = notes.filter { it.kind != Kind.WATER }.sortedBy { it.occurredAt }.map { "${tfmt.format(Date(it.occurredAt))}  ${it.text}${if (it.triage != "GREEN") " [${it.triage}]" else ""}" }

        return Summary(
            title = "MedLog – Patient summary  ${d(from)} – ${d(to - 1)} (${days.size} days)",
            header = header,
            alertLine = alertLine,
            concerns = concerns.sortedByDescending { it.first }.map { it.second }.take(10),
            days = days,
            timeline = timeline,
            details = details,
            medicines = medRows,
            readings = readingLines,
            foodWater = foodWater,
            questions = questions,
            allNotes = allNotes,
            footer = "Patient-reported via MedLog. ◇ = worked out by the app, not said by the patient, not a diagnosis. * = important medicine. " +
                "Rules ${DangerRules.VERSION}${if (!cat.reviewed) " (not clinician-reviewed)" else ""}. Generated ${tfmt.format(Date(now))}.",
        )
    }

    private fun mark(f: Fact) = if (f.source == Source.INFERRED) " ◇" else ""
}

/** "Worth checking" patterns for the doctor only (plan 8.5). Counts, not claims. */
object Patterns {
    fun find(cat: Catalogue, symptoms: List<Note>, facts: Map<Long, Map<String, Fact>>, meds: List<Medicine>, doses: List<Dose>, notes: List<Note>, days: List<LocalDate>, zone: ZoneId): List<String> {
        val out = ArrayList<String>()
        val byProblem = symptoms.groupBy { it.problemId!! }
        fun label(pid: String) = cat.problem(pid)?.label ?: pid
        fun day(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()

        // 1. began soon after a medicine was started or changed
        for ((pid, list) in byProblem) {
            val first = list.minOf { it.occurredAt }
            for (m in meds) {
                val changed = m.changedAt
                val gapDays = (first - changed) / DAY
                if (first > changed && gapDays in 0..7 && m.changeNote.isNotBlank()) out += "${label(pid)} began $gapDays day${if (gapDays == 1L) "" else "s"} after ${m.name} was ${if (m.changeNote == "started") "started" else m.changeNote}"
            }
        }
        // 2. after meals
        for ((pid, list) in byProblem) {
            if (list.size < 3) continue
            val after = list.count { n -> (facts[n.id]?.get("context")?.value as? String)?.let { Regex("after (breakfast|lunch|dinner|food|eating|meals?)").containsMatchIn(it) } == true }
            if (after * 10 >= list.size * 6) out += "${label(pid)} came after meals $after of ${list.size} times"
        }
        // 3. dehydration risk
        val fluidLoss = (byProblem["vomiting"].orEmpty() + byProblem["loose_motions"].orEmpty() + byProblem["fever"].orEmpty())
        if (fluidLoss.isNotEmpty()) {
            val lossDays = fluidLoss.map { day(it.occurredAt) }.toSet()
            val water = lossDays.associateWith { dd -> notes.filter { it.kind == Kind.WATER && day(it.occurredAt) == dd }.sumOf { it.count ?: 1 } }
            val low = water.filter { it.value in 1..3 }
            val noUrine = fluidLoss.any { facts[it.id]?.get("urineToday")?.value == false }
            if (low.isNotEmpty() || noUrine) out += "Possible dehydration: fluid loss with ${if (low.isNotEmpty()) "low water intake (${low.values.joinToString("/")} glasses)" else ""}${if (noUrine) (if (low.isNotEmpty()) " and " else "") + "reduced urine" else ""}"
        }
        // 4. symptom within 2 h after a dose, repeatedly
        for ((pid, list) in byProblem) {
            if (list.size < 3) continue
            for (m in meds) {
                val taken = doses.filter { it.medicineId == m.id && it.status == DoseStatus.TAKEN }.mapNotNull { it.actedAt }
                val hits = list.count { n -> taken.any { t -> n.occurredAt - t in 0..(2 * HOUR) } }
                if (hits >= 3 && hits * 10 >= list.size * 6) out += "${label(pid)} within 2 hours of taking ${m.name}: $hits of ${list.size} times"
            }
        }
        // 5. fluid build-up
        val lyingFlat = byProblem["breathless"].orEmpty().any { facts[it.id]?.get("lyingFlat")?.value == true }
        if (lyingFlat && byProblem.containsKey("swollen_ankles")) out += "Breathless lying flat together with swollen ankles"
        // 6. weight change
        val weights = notes.filter { it.kind == Kind.READING }.mapNotNull { n -> runCatching { JSONObject(n.details) }.getOrNull()?.takeIf { it.getString("type") == "weight" }?.let { n.occurredAt to it.getDouble("v1") } }.sortedBy { it.first }
        if (weights.size >= 2) {
            val diff = weights.last().second - weights.first().second
            if (kotlin.math.abs(diff) >= 2.0) out += "Weight ${if (diff > 0) "up" else "down"} ${fmt1(kotlin.math.abs(diff))} kg in ${((weights.last().first - weights.first().first) / DAY).coerceAtLeast(1)} days"
        }
        // 7. doses skipped because of feeling sick
        val sickSkips = doses.count { it.reason == "Feeling sick" }
        if (sickSkips >= 2) out += "$sickSkips doses skipped because of feeling sick"
        return out.distinct()
    }
}
