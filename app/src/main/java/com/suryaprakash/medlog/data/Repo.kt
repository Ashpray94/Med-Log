package com.suryaprakash.medlog.data

import com.suryaprakash.medlog.clinical.Catalogue
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Describe
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.PersonContext
import com.suryaprakash.medlog.clinical.RecentNote
import com.suryaprakash.medlog.clinical.Told
import com.suryaprakash.medlog.clinical.Triage
import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Mention
import com.suryaprakash.medlog.nlu.Reading
import com.suryaprakash.medlog.nlu.Source
import com.suryaprakash.medlog.nlu.factsFromJson
import com.suryaprakash.medlog.nlu.factsToJson
import org.json.JSONObject
import java.time.LocalDate
import java.time.Period
import kotlin.math.exp

const val HOUR = 3600_000L
const val DAY = 24 * HOUR

/** A "Yes, better" tap: the fact better is `true` (a Boolean). The "what makes it better" answer is a List and is not this. */
fun isBetterFacts(facts: Map<String, Fact>): Boolean = facts["better"]?.value == true

fun Note.isBetterNote(): Boolean = kind == Kind.SYMPTOM && runCatching { isBetterFacts(factsFromJson(details)) }.getOrDefault(false)

/** The notes that are real occurrences of a problem: everything except "better" taps. Use this before any count. */
fun List<Note>.occurrences(): List<Note> = filter { !it.isBetterNote() }

/** Notes as the danger rules see them: without the note being evaluated ([excludeId]) and without "better" taps. */
fun rulesNotes(notes: List<Note>, excludeId: Long?): List<RecentNote> =
    notes.filter { it.id != excludeId && !it.isBetterNote() }.map { RecentNote(it.problemId, it.occurredAt, factsFromJson(it.details), it.count) }

/** Everything the screens do to data goes through here. */
class Repo(val db: MedDb, private val cat: Catalogue, private val describe: Describe) {

    // ───────── person ─────────

    suspend fun profile(): Profile = db.profile().get() ?: Profile()

    suspend fun person(): PersonContext {
        val p = profile()
        val thinner = p.onBloodThinner || db.medicines().active().any { it.bloodThinner }
        val plan = CarePlan.parse(p.plan)
        return PersonContext(ageYears(p.dob), thinner, p.conditions, plan.limits, DangerRules.cancerCareOf(p.conditions, plan.treatments))
    }

    /** The cancer doctor to call first on the RED page, when the person is on cancer treatment and that doctor has a phone (B58). */
    suspend fun cancerDoctor(): CarePlan.Doctor? {
        val p = profile()
        val plan = CarePlan.parse(p.plan)
        if (!DangerRules.cancerCareOf(p.conditions, plan.treatments)) return null
        return plan.doctorFor(null, true)?.takeIf { it.speciality == "Cancer" && it.phone.isNotBlank() }
    }

    fun ageYears(dob: String): Int? = runCatching { Period.between(LocalDate.parse(dob), LocalDate.now()).years }.getOrNull()

    // ───────── symptoms ─────────

    /**
     * Notes of the last [days] days for the danger rules. The note being evaluated ([excludeId]) is left out, because
     * the rules add its own count themselves (B06, B07). "Better" taps are not occurrences and are left out too.
     */
    suspend fun recentForRules(days: Int = 3, excludeId: Long? = null): List<RecentNote> =
        rulesNotes(db.notes().symptomsSince(System.currentTimeMillis() - days * DAY), excludeId)

    /**
     * Saves what the person confirmed: the main problem and anything told with it, linked as one group.
     * Returns the ids of the saved notes (for Undo).
     */
    suspend fun saveTold(
        mentions: List<Mention>,
        transcript: String?,
        occurredAt: Long,
        triage: Triage,
        readings: List<Reading>,
        medicinesTaken: List<String>,
        audioPath: String?,
    ): List<Long> {
        val ids = ArrayList<Long>()
        var group: Long? = null
        for ((i, m) in mentions.filter { !it.negated }.withIndex()) {
            val facts = m.facts
            val note = Note(
                kind = Kind.SYMPTOM,
                problemId = m.problemId,
                occurredAt = occurredAt,
                transcript = if (i == 0) transcript else null,
                details = factsToJson(facts),
                severity = (facts["severity"]?.value as? Number)?.toInt(),
                count = (facts["count"]?.value as? Number)?.toInt(),
                triage = if (i == 0) triage.level.name else Level.GREEN.name,
                triageReasons = if (i == 0) triage.reasons.joinToString("\n") else "",
                audioPath = if (i == 0) audioPath else null,
                groupId = group,
                text = describe.line(m.problemId, facts),
            )
            val id = db.notes().insert(note)
            if (group == null) { group = id; db.notes().update(note.copy(id = id, groupId = id)) }
            ids += id
        }
        for (r in readings) ids += addReading(r, transcript = null, at = occurredAt, group = group)
        for (med in medicinesTaken) ids += addMedicineTaken(med, occurredAt, group)
        return ids
    }

    suspend fun updateFacts(id: Long, facts: Map<String, Fact>) {
        val n = db.notes().get(id) ?: return
        // hidden markers (keys starting with "_", e.g. _toldLevel) belong to the database copy: the screen never edits them
        val markers = factsFromJson(n.details).filterKeys { it.startsWith("_") }
        val all = facts.filterKeys { !it.startsWith("_") } + markers
        db.notes().update(n.copy(
            details = factsToJson(all),
            severity = (facts["severity"]?.value as? Number)?.toInt(),
            count = (facts["count"]?.value as? Number)?.toInt(),
            text = n.problemId?.let { describe.line(it, facts) } ?: n.text,
        ))
    }

    /**
     * Records that helpers are being told about note [id] at [level]. Returns true only when the level is higher than
     * what was told before, so a note texts its helpers at most once per level (B57, B59).
     */
    suspend fun markTold(id: Long, level: Level): Boolean {
        val n = db.notes().get(id) ?: return false
        val facts = factsFromJson(n.details)
        if (!Told.shouldTell(facts, level)) return false
        db.notes().update(n.copy(details = factsToJson(Told.mark(facts, level))))
        return true
    }

    /** Deletes note [id] when nothing was answered (the person opened a problem and backed out) (B62). True when deleted. */
    suspend fun removeIfUnanswered(id: Long): Boolean {
        val n = db.notes().get(id) ?: return false
        if (n.kind != Kind.SYMPTOM || factsFromJson(n.details).keys.any { !it.startsWith("_") }) return false
        remove(listOf(id)); return true
    }

    suspend fun updateTriage(id: Long, t: Triage) {
        val n = db.notes().get(id) ?: return
        db.notes().update(n.copy(triage = t.level.name, triageReasons = t.reasons.joinToString("\n")))
    }

    suspend fun changeProblem(id: Long, problemId: String) {
        val n = db.notes().get(id) ?: return
        val facts = factsFromJson(n.details)
        db.notes().update(n.copy(problemId = problemId, text = describe.line(problemId, facts)))
    }

    suspend fun setOccurred(id: Long, at: Long) {
        val n = db.notes().get(id) ?: return
        db.notes().update(n.copy(occurredAt = at))
    }

    suspend fun markBetter(problemId: String) {
        // count = 0 and better = true: it is a tap, not another occurrence, and every count skips it (B19)
        db.notes().insert(Note(kind = Kind.SYMPTOM, problemId = problemId, occurredAt = System.currentTimeMillis(), count = 0,
            details = factsToJson(mapOf("better" to Fact(true, Source.TAPPED))), text = "${cat.problem(problemId)?.label}: better now"))
    }

    // ───────── other notes ─────────

    suspend fun addWater(glasses: Int = 1, at: Long = System.currentTimeMillis()): Long =
        db.notes().insert(Note(kind = Kind.WATER, occurredAt = at, count = glasses, text = "Water: $glasses ${if (glasses == 1) "glass" else "glasses"}"))

    suspend fun waterToday(): Int {
        val start = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return db.notes().kindSince(Kind.WATER, start).sumOf { it.count ?: 1 }
    }

    suspend fun addFood(text: String, photo: String?, at: Long = System.currentTimeMillis()): Long =
        db.notes().insert(Note(kind = Kind.FOOD, occurredAt = at, transcript = text, photoPath = photo, text = "Ate: $text".trim()))

    suspend fun addReading(r: Reading, transcript: String?, at: Long = System.currentTimeMillis(), group: Long? = null): Long {
        val d = JSONObject().put("type", r.type).put("v1", r.v1).put("unit", r.unit)
        r.v2?.let { d.put("v2", it) }
        return db.notes().insert(Note(kind = Kind.READING, occurredAt = at, transcript = transcript, details = d.toString(), groupId = group, text = r.label()))
    }

    suspend fun addMedicineTaken(name: String, at: Long = System.currentTimeMillis(), group: Long? = null): Long =
        db.notes().insert(Note(kind = Kind.MED_TAKEN, occurredAt = at, groupId = group, details = JSONObject().put("name", name).toString(), text = "Took $name"))

    suspend fun addQuestion(text: String): Long =
        db.notes().insert(Note(kind = Kind.QUESTION, occurredAt = System.currentTimeMillis(), transcript = text, text = "Question for doctor: $text"))

    suspend fun addEvent(kind: String, text: String, details: String = "{}"): Long =
        db.notes().insert(Note(kind = kind, occurredAt = System.currentTimeMillis(), details = details, text = text))

    suspend fun remove(ids: List<Long>) = ids.forEach { db.notes().remove(it) }
    suspend fun restore(ids: List<Long>) = ids.forEach { db.notes().restore(it) }
    suspend fun purgeRemoved() = db.notes().purge(System.currentTimeMillis() - 30 * DAY)

    // ───────── widget / home: which problems to show (plan 6.2) ─────────

    data class Recent(val problemId: String, val todayCount: Int, val lastAt: Long, val ongoing: Boolean)

    suspend fun recentProblems(limit: Int = 6, watch: Set<String> = emptySet()): List<Recent> {
        val now = System.currentTimeMillis()
        val notes = db.notes().symptomsSince(now - 30 * DAY).filter { it.problemId != null }
        val todayStart = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val byProblem = notes.groupBy { it.problemId!! }
        val scored = byProblem.map { (pid, list) ->
            val score = list.sumOf { exp(-(now - it.occurredAt).toDouble() / (3 * DAY)) }
            val last = list.maxOf { it.occurredAt }
            val better = list.filter { factsFromJson(it.details)["better"]?.value == true }.maxOfOrNull { it.occurredAt } ?: 0
            val ongoing = now - last < 2 * DAY && better < last
            Triple(pid, score + (if (ongoing) 100.0 else 0.0) + (if (pid in watch) 50.0 else 0.0),
                Recent(pid, list.occurrences().filter { it.occurredAt >= todayStart }.sumOf { it.count ?: 1 }, last, ongoing))
        }.sortedByDescending { it.second }.map { it.third }
        // only what the person actually noted: nothing suggested or filled in, so recents never mislead
        return scored.take(limit)
    }

    // ───────── search (retrieval only: returns what is stored, never generated) ─────────

    suspend fun search(q: String): Pair<List<Note>, List<DocLine>> {
        val t = q.trim()
        if (t.length < 2) return emptyList<Note>() to emptyList()
        return db.notes().search(t) to db.docLines().search(t)
    }

    companion object {
        /** Most common problems for older adults, shown until the person has their own history. */
        val DEFAULT_PROBLEMS = listOf("headache", "dizzy", "stomach_pain", "back_pain", "knee_pain", "cough", "fever", "tired", "breathless", "vomiting")
    }
}
