package com.suryaprakash.medlog.doctor

import com.suryaprakash.medlog.clinical.Catalogue
import com.suryaprakash.medlog.clinical.Describe
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.data.Note
import com.suryaprakash.medlog.data.occurrences
import com.suryaprakash.medlog.data.Profile
import com.suryaprakash.medlog.nlu.Fact
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
 * What the doctor sees: observations, not the app. Written to be read in under five minutes:
 * three main concerns, one table of symptoms, medicines, readings, and the patient's questions.
 */
data class DoctorNote(
    val period: String,
    val patient: String,
    val allergies: String,
    val conditions: String,
    val currentMeds: String,
    val concerns: List<String>,
    val symptoms: List<Row>,
    val pins: List<Pair<Int, String>>,          // row number, "front:x,y"
    val medicines: List<Med>,
    val readings: List<String>,
    val links: List<String>,
    val questions: List<String>,
    val footer: String,
    /** Structured versions of the above, for the visual page. */
    val concernLevels: List<String> = emptyList(),   // RED / AMBER / GREEN per concern
    val tiles: List<Reading> = emptyList(),
    val days: Int = 14,
) {
    data class Row(
        val n: Int, val name: String, val urgent: String, val whenText: String, val where: String, val nature: String, val notes: String,
        val problemId: String = "", val total: Int = 1, val daysWith: Int = 1, val sevLow: Int? = null, val sevHigh: Int? = null, val sevLast: Int? = null,
        /** occurrences on each day of the period, oldest first */
        val daily: List<Int> = emptyList(), val trend: String? = null, val began: String? = null,
        val places: List<String> = emptyList(), val feels: List<String> = emptyList(), val flags: List<String> = emptyList(), val quote: String? = null,
    )
    data class Med(val name: String, val dose: String, val taken: String, val change: String, val done: Int = 0, val due: Int = 0, val asNeeded: Boolean = false)
    data class Reading(val name: String, val latest: String, val unit: String, val range: String?, val count: Int, val date: String, val off: Boolean)
}

class DoctorNoteBuilder(private val cat: Catalogue, private val describe: Describe) {
    private val zone = ZoneId.systemDefault()
    private val dfmt = SimpleDateFormat("d MMMM", Locale.ENGLISH)
    private fun d(t: Long) = dfmt.format(Date(t))
    private fun day(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()

    fun build(profile: Profile, from: Long, to: Long, notes: List<Note>, meds: List<Medicine>, doses: List<Dose>, translit: (String) -> String = { it }, now: Long = System.currentTimeMillis()): DoctorNote {
        val symptoms = notes.filter { it.kind == Kind.SYMPTOM && it.problemId != null }
        val facts = symptoms.associate { it.id to factsFromJson(it.details) }
        // "Yes, better" taps stay in each group (they say "now better") but are never counted as occurrences (B19)
        val byProblem = symptoms.groupBy { it.problemId!! }
            .entries.filter { e -> e.value.occurrences().isNotEmpty() }
            .sortedWith(compareByDescending<Map.Entry<String, List<Note>>> { e -> e.value.maxOf { rank(it.triage) } }.thenByDescending { e -> e.value.occurrences().sumOf { it.count ?: 1 } })

        // ── patient ──
        val age = runCatching { Period.between(LocalDate.parse(profile.dob), LocalDate.now()).years }.getOrNull()
        val patient = listOfNotNull(profile.name.ifBlank { null }, age?.let { "$it y" }, when (profile.sex) { "F" -> "female"; "M" -> "male"; else -> null },
            profile.hospitalId.ifBlank { null }?.let { "ID $it" }).joinToString(", ")
        val active = meds.filter { it.active }
        val currentMeds = active.joinToString("; ") { m -> "${m.name} ${m.strength}".trim() + " " + freq(m) }.ifBlank { "None recorded" }

        // ── symptom rows ──
        val rows = ArrayList<DoctorNote.Row>()
        val pins = ArrayList<Pair<Int, String>>()
        byProblem.forEachIndexed { i, (pid, list) ->
            val n = i + 1
            val fs = list.sortedBy { it.occurredAt }.map { facts.getValue(it.id) }
            val real = list.occurrences()
            val total = real.sumOf { it.count ?: 1 }
            val first = real.minOf { it.occurredAt }; val last = real.maxOf { it.occurredAt }
            val days = real.map { day(it.occurredAt) }.distinct().size
            val started = fs.firstNotNullOfOrNull { it["started"]?.value as? String }
            val whenText = buildString {
                append(if (total > 1) "$total times in $days day${if (days == 1) "" else "s"}" else "Once")
                append(" (${d(first)}${if (day(first) != day(last)) " to ${d(last)}" else ""})")
                if (started != null) append("; started ${com.suryaprakash.medlog.ui.screens.softStart(com.suryaprakash.medlog.ui.screens.startedWords(started, first, alwaysDate = true))}")
                trend(real)?.let { append("; $it") }
                if (fs.any { it["better"]?.value == true }) append("; now better")
            }
            val sites = fs.mapNotNull { it["site"]?.value?.toString()?.lowercase() }.distinct()
            val depths = fs.mapNotNull { it["depth"]?.value?.toString() }.distinct()
            val where = (sites + depths).joinToString(", ").ifBlank { fs.firstNotNullOfOrNull { it["side"]?.value?.toString() }?.let { "$it side" } ?: "–" }
            val sev = fs.mapNotNull { (it["severity"]?.value as? Number)?.toInt() }
            val chars = fs.flatMap { (it["character"]?.value as? List<*>)?.map { c -> c.toString() } ?: emptyList() }.distinct()
            val nature = listOfNotNull(
                chars.takeIf { it.isNotEmpty() }?.joinToString(", "),
                sev.takeIf { it.isNotEmpty() }?.let { s -> if (s.min() == s.max()) "${s.max()}/10" else "${s.min()}–${s.max()}/10" },
                fs.firstNotNullOfOrNull { it["pattern"]?.value?.toString() },
                fs.firstNotNullOfOrNull { f -> (f["colour"]?.value as? String)?.let { "colour $it" } },
            ).joinToString("; ").ifBlank { "–" }
            val key = LinkedHashSet<String>()
            val latest = LinkedHashMap<String, Fact>().apply { fs.forEach { putAll(it) } }   // newest answer per question
            for ((k, v) in latest) {
                val field = cat.field(k) ?: continue
                // only what was there: "not the worst ever" tells a doctor nothing
                if (field.type.name == "YESNO" && v.value == true && (field.danger || k == "bloodThinner")) describe.fact(k, v)?.let { key += it }
                if (k == "keepWater" && v.value == false) key += "can't keep water down"
                if (k == "urineToday" && v.value == false) key += "no urine for 8 hours"
            }
            latest["burnDepth"]?.let { key += "burn: ${it.value}" }
            latest["burnSize"]?.let { key += "size: ${it.value}" }
            fs.firstNotNullOfOrNull { (it["worse"]?.value as? List<*>)?.joinToString("/") }?.let { key += "worse: $it" }
            fs.firstNotNullOfOrNull { (it["better"]?.value as? List<*>)?.joinToString("/") }?.let { key += "eased by: $it" }
            fs.firstNotNullOfOrNull { it["medicineTaken"]?.value?.toString() }?.let { key += "took: ${translit(it)}" }
            fs.firstNotNullOfOrNull { (it["temperature"]?.value as? Number)?.toDouble() }?.let { key += "temp ${fmt1(it)} °F" }
            fs.firstNotNullOfOrNull { it["note"]?.value?.toString() }?.let { key += "“${translit(it).take(80)}”" }
            val urgent = list.maxByOrNull { rank(it.triage) }?.triage ?: "GREEN"
            val periodDays = generateSequence(day(from)) { it.plusDays(1) }.takeWhile { !it.isAfter(day(to - 1)) }.toList()
            val perDay = real.groupBy { day(it.occurredAt) }.mapValues { (_, l) -> l.sumOf { it.count ?: 1 } }
            val flags = LinkedHashSet<String>()
            for ((k, v) in latest) {
                val field = cat.field(k) ?: continue
                if (field.type.name == "YESNO" && v.value == true && (field.danger || k == "bloodThinner")) describe.fact(k, v)?.let { flags += it }
            }
            latest["burnDepth"]?.let { flags += "Burn: ${it.value}" }
            latest["burnSize"]?.let { flags += "Size: ${it.value}" }
            val sevAll = fs.mapNotNull { (it["severity"]?.value as? Number)?.toInt() }
            rows += DoctorNote.Row(n, cat.problem(pid)?.label ?: pid, urgent, whenText, where, nature, key.take(4).joinToString("; ").ifBlank { "–" },
                problemId = pid, total = total, daysWith = days, sevLow = sevAll.minOrNull(), sevHigh = sevAll.maxOrNull(), sevLast = sevAll.lastOrNull(),
                daily = periodDays.map { perDay[it] ?: 0 }, trend = trend(real) ?: if (fs.any { it["better"]?.value == true }) "better" else null,
                began = started?.let { com.suryaprakash.medlog.ui.screens.startedWords(it, first, alwaysDate = true) },
                places = (sites + depths).map { it.replaceFirstChar(Char::uppercase) },
                feels = (chars + listOfNotNull(fs.firstNotNullOfOrNull { it["pattern"]?.value?.toString() })).map { it.replaceFirstChar(Char::uppercase) },
                flags = flags.toList(), quote = fs.firstNotNullOfOrNull { it["note"]?.value?.toString() }?.let { translit(it).take(120) })
            fs.mapNotNull { it["pin"]?.value?.toString() }.distinct().take(2).forEach { pins += n to it }
        }

        // ── main concerns: urgent findings first, then what is happening most ──
        val concerns = LinkedHashSet<String>()
        val levels = ArrayList<String>()
        for ((pid, list) in byProblem.sortedByDescending { (_, l) -> l.maxOf { rank(it.triage) } }) {
            val reasons = list.filter { rank(it.triage) > 0 }.flatMap { it.triageReasons.lines() }.filter { it.isNotBlank() }.distinct()
            if (reasons.isNotEmpty() && concerns.add("${cat.problem(pid)?.label}: ${reasons.take(2).joinToString("; ")} (${d(list.filter { rank(it.triage) > 0 }.maxOf { it.occurredAt })})"))
                levels += list.maxBy { rank(it.triage) }.triage
        }
        for (r in rows) if (concerns.size < 3 && r.urgent == "GREEN" && concerns.add("${r.name}: " + (if (r.total > 1) "${r.total} times in ${r.daysWith} day${if (r.daysWith == 1) "" else "s"}" else "once") + " (${r.whenText.substringAfter("(").substringBefore(")")})")) levels += "GREEN"
        for (m in active) {
            val md = doses.filter { it.medicineId == m.id && it.scheduledAt in from until minOf(to, now) }
            val missed = md.count { it.status == DoseStatus.MISSED || it.status == DoseStatus.SKIPPED }
            if (md.size >= 3 && missed * 4 >= md.size && concerns.size < 3 && concerns.add("Missed ${m.name}: $missed of ${md.size} doses")) levels += "AMBER"
        }

        // ── medicines ──
        val medRows = meds.filter { it.active || it.changedAt >= from }.map { m ->
            val md = doses.filter { it.medicineId == m.id && it.scheduledAt in from until minOf(to, now) }
            val taken = md.count { it.status == DoseStatus.TAKEN }
            val prn = notes.count { it.kind == Kind.MED_TAKEN && runCatching { JSONObject(it.details).optString("name") }.getOrNull() == m.name }
            val skipped = md.mapNotNull { it.reason }.groupingBy { it }.eachCount().entries.joinToString { "${it.key.lowercase()} ×${it.value}" }
            DoctorNote.Med(
                m.name, "${m.strength} ${freq(m)}".trim(),
                when { m.asNeeded -> if (prn > 0) "used ${prn}×" else "not used"; md.isEmpty() -> "–"; else -> "$taken/${md.size}" },
                listOfNotNull(
                    if (!m.active) "stopped ${d(m.changedAt)}" else null,
                    m.changeNote.takeIf { it.isNotBlank() && it != "started" && it != "stopped" && m.changedAt >= from }?.let { "$it ${d(m.changedAt)}" },
                    if (m.changeNote == "started" && m.startDate >= from) "started ${d(m.startDate)}" else null,
                    skipped.ifBlank { null }?.let { "skipped: $it" },
                ).joinToString("; "),
                done = taken, due = md.size, asNeeded = m.asNeeded,
            )
        }

        // ── readings: latest and range ──
        val tiles = ArrayList<DoctorNote.Reading>()
        val readings = notes.filter { it.kind == Kind.READING }.mapNotNull { n -> runCatching { JSONObject(n.details) }.getOrNull()?.let { n to it } }
            .groupBy { it.second.getString("type") }.map { (type, l) ->
                val s = l.sortedBy { it.first.occurredAt }
                val name = mapOf("bp" to "BP", "sugar" to "Blood sugar", "spo2" to "SpO₂", "temp" to "Temperature", "pulse" to "Pulse", "weight" to "Weight")[type] ?: type
                val v = { o: JSONObject -> if (type == "bp") "${o.getDouble("v1").toInt()}/${o.optDouble("v2").toInt()}" else fmt1(o.getDouble("v1")) }
                val unit = mapOf("bp" to " mmHg", "sugar" to " mg/dL", "spo2" to "%", "temp" to " °F", "pulse" to "/min", "weight" to " kg")[type].orEmpty()
                val lo = s.minBy { it.second.getDouble("v1") }; val hi = s.maxBy { it.second.getDouble("v1") }
                val last = s.last().second
                val x = last.getDouble("v1")
                val off = when (type) {
                    "bp" -> x >= 140 || x < 90 || last.optDouble("v2", 0.0) >= 90
                    "sugar" -> x >= 200 || x < 70
                    "spo2" -> x < 94
                    "temp" -> x >= 100.4
                    "pulse" -> x > 100 || x < 50
                    else -> false
                }
                tiles += DoctorNote.Reading(name, v(last), unit.trim(), if (s.size > 1) "${v(lo.second)}–${v(hi.second)}" else null, s.size, d(s.last().first.occurredAt), off)
                "$name: ${v(s.last().second)}$unit on ${d(s.last().first.occurredAt)}" + if (s.size > 1) " (range ${v(lo.second)}–${v(hi.second)}, ${s.size} readings)" else ""
            }

        val allFacts = facts
        val links = Patterns.find(cat, symptoms, allFacts, meds, doses, notes,
            generateSequence(day(from)) { it.plusDays(1) }.takeWhile { !it.isAfter(day(to - 1)) }.toList(), zone).take(2)
        val questions = notes.filter { it.kind == Kind.QUESTION }.mapNotNull { it.transcript?.let(translit) }

        return DoctorNote(
            period = "${d(from)} – ${d(to - 1)} ${day(to - 1).year}",
            patient = patient.ifBlank { "Patient" },
            allergies = profile.allergies.ifBlank { "None known" },
            conditions = profile.conditions.ifBlank { "None recorded" } + if (profile.onBloodThinner || active.any { it.bloodThinner }) "; on a blood thinner" else "",
            currentMeds = currentMeds,
            concerns = concerns.take(3).toList(),
            symptoms = rows,
            pins = pins,
            medicines = medRows,
            readings = readings,
            links = links,
            questions = questions,
            concernLevels = levels.take(3),
            tiles = tiles,
            days = ((to - from) / DAY).toInt(),
            footer = "Reported by the patient, recorded with MedLog on their phone. Printed ${SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(now))}.",
        )
    }

    private fun rank(l: String) = when (l) { "RED" -> 2; "AMBER" -> 1; else -> 0 }

    private fun freq(m: Medicine): String {
        if (m.asNeeded) return "as needed"
        return when (m.times.split(",").count { it.isNotBlank() }) { 1 -> "OD"; 2 -> "BD"; 3 -> "TDS"; 4 -> "QID"; else -> "" }
    }

    private fun trend(list: List<Note>): String? {
        val perDay = list.groupBy { day(it.occurredAt) }.toSortedMap().values.map { l -> l.sumOf { it.count ?: 1 } }
        if (perDay.size < 3) return null
        return when {
            perDay.last() > perDay.first() -> "increasing"
            perDay.last() < perDay.first() -> "decreasing"
            else -> null
        }
    }

    @Suppress("unused") private val keep = DAY
}
