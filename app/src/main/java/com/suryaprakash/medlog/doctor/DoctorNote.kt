package com.suryaprakash.medlog.doctor

import com.suryaprakash.medlog.data.planned
import com.suryaprakash.medlog.data.ReportIntegrity
import com.suryaprakash.medlog.clinical.Catalogue
import com.suryaprakash.medlog.clinical.Describe
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.data.Note
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
    val from: Long = 0, val to: Long = 0,
    val entries: List<Entry> = emptyList(),
) {
    data class Row(
        val n: Int, val name: String, val urgent: String, val whenText: String, val where: String, val nature: String, val notes: String,
        val problemId: String = "", val total: Int = 1, val daysWith: Int = 1, val sevLow: Int? = null, val sevHigh: Int? = null, val sevLast: Int? = null,
        /** occurrences on each day of the period, oldest first */
        val daily: List<Int> = emptyList(), val trend: String? = null, val began: String? = null,
        val reportCount: Int = 0, val reports24h: Int = 0, val quoteDate: String? = null, val lastNoted: String? = null,
        val places: List<String> = emptyList(), val feels: List<String> = emptyList(), val flags: List<String> = emptyList(), val quote: String? = null,
    )
    data class Entry(val symptomNumber: Int, val name: String, val id: Long, val at: Long, val date: String,
        val noted: Boolean, val pins: List<String>, val site: String?, val depth: String?,
        val facts: List<Pair<String, String>>, val remark: String?)
    data class Med(val name: String, val dose: String, val taken: String, val change: String, val done: Int = 0, val due: Int = 0, val asNeeded: Boolean = false)
    data class Reading(val name: String, val latest: String, val unit: String, val range: String?, val count: Int, val date: String, val off: Boolean)
}

class DoctorNoteBuilder(private val cat: Catalogue, private val describe: Describe) {
    private val zone = ZoneId.systemDefault()
    private val dfmt = SimpleDateFormat("d MMMM", Locale.ENGLISH)
    private fun d(t: Long) = dfmt.format(Date(t))
    private fun day(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()

    fun build(profile: Profile, from: Long, to: Long, notes: List<Note>, meds: List<Medicine>, doses: List<Dose>, translit: (String) -> String = { it }, now: Long = System.currentTimeMillis()): DoctorNote {
        val notes = ReportIntegrity.notes(notes, from, to, now)
        val doses = ReportIntegrity.doses(doses, meds, from, to, now)
        val symptoms = notes.filter { it.kind == Kind.SYMPTOM && it.problemId != null }
        val facts = symptoms.associateWith { factsFromJson(it.details) }
        val byProblem = symptoms.groupBy { it.problemId!! }.filterValues { com.suryaprakash.medlog.data.Occurrences.total(it) > 0 }
            .entries.sortedWith(compareByDescending<Map.Entry<String, List<Note>>> { e -> e.value.maxOf { rank(it.triage) } }.thenByDescending { e -> com.suryaprakash.medlog.data.Occurrences.total(e.value) })

        // ── patient ──
        val age = com.suryaprakash.medlog.data.Repo.ageFromDob(profile.dob)
        val patient = listOfNotNull(profile.name.ifBlank { null }, age?.let { "$it y" }, when (profile.sex) { "F" -> "female"; "M" -> "male"; else -> null },
            profile.hospitalId.ifBlank { null }?.let { "ID $it" }).joinToString(", ")
        val active = meds.filter { it.active }
        val currentMeds = active.joinToString("; ") { m -> "${m.name} ${m.strength}".trim() + " " + freq(m) }

        // ── symptom rows ──
        val rows = ArrayList<DoctorNote.Row>()
        val pins = ArrayList<Pair<Int, String>>()
        byProblem.forEachIndexed { i, (pid, list) ->
            val n = i + 1
            val occurrences = list.filter(com.suryaprakash.medlog.data.Occurrences::isOccurrence)
            val fs = occurrences.sortedBy { it.occurredAt }.map { facts.getValue(it) }
            val latestReport = occurrences.maxByOrNull { it.occurredAt }
            val latestBetter = list.filter { facts.getValue(it)["better"]?.value == true }.maxOfOrNull { it.occurredAt } ?: Long.MIN_VALUE
            val betterNow = latestBetter > (latestReport?.occurredAt ?: Long.MIN_VALUE)
            val quoteNote = occurrences.sortedByDescending { it.occurredAt }.firstOrNull { facts.getValue(it)["note"]?.value is String }
            val total = com.suryaprakash.medlog.data.Occurrences.total(list)
            val first = occurrences.minOf { it.occurredAt }; val last = occurrences.maxOf { it.occurredAt }
            val days = com.suryaprakash.medlog.data.Occurrences.perDayOf(list, zone).size.coerceAtLeast(1)
            val startNote = occurrences.sortedBy { it.occurredAt }.firstOrNull { facts.getValue(it)["started"]?.value is String }
            val started = startNote?.let { facts.getValue(it)["started"]?.value as? String }
            val whenText = buildString {
                append(notedWords(ReportIntegrity.reports(list).size))
                append(" (${d(first)}${if (day(first) != day(last)) " to ${d(last)}" else ""})")
                if (started != null) append("; started ${com.suryaprakash.medlog.ui.screens.softStart(com.suryaprakash.medlog.ui.screens.startedWords(started, first, alwaysDate = true))}")
                if (betterNow) append("; patient reported improvement on ${d(latestBetter)}")
            }
            val sites = fs.mapNotNull { it["site"]?.value?.toString()?.lowercase() }.distinct()
            val depths = fs.mapNotNull { it["depth"]?.value?.toString() }.distinct()
            val where = sites.joinToString(", ").ifBlank { fs.firstNotNullOfOrNull { it["side"]?.value?.toString() }?.let { "$it side" } ?: "" }
            val sev = fs.mapNotNull { (it["severity"]?.value as? Number)?.toInt() }
            val chars = fs.flatMap { (it["character"]?.value as? List<*>)?.map { c -> c.toString() } ?: emptyList() }.distinct()
            val nature = listOfNotNull(
                chars.takeIf { it.isNotEmpty() }?.joinToString(", "),
                sev.takeIf { it.isNotEmpty() }?.let { s -> if (s.min() == s.max()) "${s.max()}/10" else "${s.min()}–${s.max()}/10" },
                fs.firstNotNullOfOrNull { it["pattern"]?.value?.toString() },
                fs.firstNotNullOfOrNull { f -> (f["colour"]?.value as? String)?.let { "colour $it" } },
                fs.firstNotNullOfOrNull { f -> f["often"]?.let { describe.fact("often", it) } },
                fs.firstNotNullOfOrNull { f -> f["diagnosed"]?.let { describe.fact("diagnosed", it) } },
            ).joinToString("; ")
            val key = LinkedHashSet<String>()
            val latest = latestReport?.let { facts.getValue(it) }.orEmpty()   // newest answer per question
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
            quoteNote?.let { facts.getValue(it)["note"]?.value?.toString()?.let { text -> key += "Additional note (${d(it.occurredAt)}): “${translit(text).take(80)}”" } }
            val urgent = list.maxByOrNull { rank(it.triage) }?.triage ?: "GREEN"
            val periodDays = generateSequence(day(from)) { it.plusDays(1) }.takeWhile { !it.isAfter(day(to - 1)) }.toList()
            val perDay = ReportIntegrity.reports(list).groupingBy { day(it.occurredAt) }.eachCount()
            val flags = LinkedHashSet<String>()
            for ((k, v) in latest) {
                val field = cat.field(k) ?: continue
                if (field.type.name == "YESNO" && v.value == true && (field.danger || k == "bloodThinner")) describe.fact(k, v)?.let { flags += it }
            }
            latest["burnDepth"]?.let { flags += "Burn: ${it.value}" }
            latest["burnSize"]?.let { flags += "Size: ${it.value}" }
            val sevAll = fs.mapNotNull { (it["severity"]?.value as? Number)?.toInt() }
            rows += DoctorNote.Row(n, cat.problem(pid)?.label ?: pid, urgent, whenText, where, nature, key.take(4).joinToString("; "),
                problemId = pid, total = total, daysWith = days, sevLow = sevAll.minOrNull(), sevHigh = sevAll.maxOrNull(), sevLast = sevAll.lastOrNull(),
                daily = periodDays.map { perDay[it] ?: 0 }, trend = if (betterNow) "better" else null,
                reportCount = ReportIntegrity.reports(list).size, reports24h = ReportIntegrity.reports(list).count { it.occurredAt in (now - DAY)..now },
                quoteDate = quoteNote?.let { d(it.occurredAt) }, lastNoted = dateTime(last),
                began = started?.let { com.suryaprakash.medlog.ui.screens.startedWords(it, startNote?.occurredAt ?: first, alwaysDate = true) },
                places = sites.map { it.replaceFirstChar(Char::uppercase) },
                feels = (chars + listOfNotNull(fs.firstNotNullOfOrNull { it["pattern"]?.value?.toString() })).map { it.replaceFirstChar(Char::uppercase) },
                flags = flags.toList(), quote = quoteNote?.let { facts.getValue(it)["note"]?.value?.toString() }?.let { translit(it).take(120) })
            fs.mapNotNull { it["pin"]?.value?.toString() }.distinct().forEach { pins += n to it }
        }

        // Simple, selected-range counts. Saved alerts belong to their dated entry, not the headline.
        val concerns = rows.take(3).map { "${it.name}: ${notedWords(it.reportCount)} in this period" }
        val levels = rows.take(3).map { "GREEN" }

        // ── medicines ──
        // medicines only (feeds are reported with food), so doses taken counts what the doctor prescribed
        val medRows = meds.filter { m -> m.form != "feed" && (m.active || doses.any { it.medicineId == m.id }) }.map { m ->
            val md = doses.filter { it.medicineId == m.id && it.scheduledAt in from until minOf(to, now) }
            val taken = md.count { it.status == DoseStatus.TAKEN }
            val prn = notes.count { it.kind == Kind.MED_TAKEN && runCatching { JSONObject(it.details).optString("name") }.getOrNull() == m.name }
            val skipped = md.filter { it.status == DoseStatus.SKIPPED }.mapNotNull { com.suryaprakash.medlog.data.reasonWords(it.reason)?.takeIf { r -> !r.equals("Not given", true) } }.groupingBy { it }.eachCount().entries.joinToString { "${it.key.lowercase()} ×${it.value}" }
            DoctorNote.Med(
                m.name, "${m.strength} ${freq(m)}".trim(),
                when { m.asNeeded -> if (prn > 0) "used ${prn}×" else ""; md.isEmpty() -> ""; else -> "$taken/${md.size}" },
                listOfNotNull(
                    if (!m.active) "${if (m.changeNote == "removed") "Removed from list" else "Stopped"} ${d(m.changedAt)}" else null,
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

        val allFacts = symptoms.associate { it.id to factsFromJson(it.details) }
        val links = emptyList<String>()
        val questions = notes.filter { it.kind == Kind.QUESTION }.mapNotNull { it.transcript?.let(translit) }

        return DoctorNote(
            period = "${d(from)} – ${d(to - 1)} ${day(to - 1).year}",
            patient = patient.ifBlank { "Patient" },
            allergies = profile.allergies,
            conditions = listOfNotNull(profile.conditions.ifBlank { null }, if (profile.onBloodThinner || active.any { it.bloodThinner }) "on a blood thinner" else null).joinToString("; "),
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
            days = java.time.temporal.ChronoUnit.DAYS.between(day(from), day(to - 1)).toInt() + 1, from = from, to = to,
            entries = rows.flatMap { row -> symptoms.filter { it.problemId == row.problemId }.sortedBy { it.occurredAt }.map { note ->
                val f = factsFromJson(note.details)
                val pin = f["pin"]?.value?.toString()?.takeIf { it.isNotBlank() }
                val fields = f.entries.filter { it.key !in setOf("pin", "depth", "note", "site", "count") && !it.key.startsWith("_") }
                    .mapNotNull { (key, value) -> when {
                        key == "better" && value.value == true -> "Update" to "Getting better"
                        key == "started" && value.value is String -> "Started" to com.suryaprakash.medlog.ui.screens.startedWords(value.value as String, note.occurredAt, alwaysDate = true)
                        else -> describe.fact(key, value)?.let { (cat.field(key)?.label ?: key.replaceFirstChar(Char::uppercase)) to it }
                    } }
                DoctorNote.Entry(row.n, row.name, note.id, note.occurredAt, dateTime(note.occurredAt),
                    com.suryaprakash.medlog.data.Occurrences.isOccurrence(note), listOfNotNull(pin),
                    f["site"]?.value?.toString(), f["depth"]?.value?.toString(), fields,
                    f["note"]?.value?.toString()?.let(translit))
            } },
            footer = "Reported by the patient, recorded with MedLog on their phone. Printed ${SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(now))}.",
        ).also(ReportValidation::requireAccurate)
    }

    private fun dateTime(at: Long) = SimpleDateFormat("d MMMM yyyy, h:mm a", Locale.ENGLISH).format(Date(at))

    private fun rank(l: String) = when (l) { "RED" -> 2; "AMBER" -> 1; else -> 0 }

    private fun freq(m: Medicine): String {
        if (m.asNeeded) return "as needed"
        return when (val n = m.times.split(",").count { it.isNotBlank() }) { 0 -> ""; 1 -> "once a day"; 2 -> "twice a day"; else -> "$n times a day" }
    }

    @Suppress("unused") private val keep = DAY
}

fun notedWords(n: Int) = if (n == 1) "Noted once" else "Noted $n times"
