package com.suryaprakash.medlog.data

import com.suryaprakash.medlog.clinical.Catalogue
import com.suryaprakash.medlog.clinical.Describe
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.PersonContext
import com.suryaprakash.medlog.clinical.RecentNote
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

/** Everything the screens do to data goes through here. */
class Repo(val db: MedDb, private val cat: Catalogue, private val describe: Describe) {

    // ───────── person ─────────

    suspend fun profile(): Profile = db.profile().get() ?: Profile()

    suspend fun person(): PersonContext {
        val p = profile()
        val thinner = p.onBloodThinner || db.medicines().active().any { it.bloodThinner }
        return PersonContext(ageYears(p.dob), thinner, p.conditions)
    }

    fun ageYears(dob: String): Int? = runCatching { Period.between(LocalDate.parse(dob), LocalDate.now()).years }.getOrNull()

    // ───────── symptoms ─────────

    suspend fun recentForRules(days: Int = 3): List<RecentNote> =
        db.notes().symptomsSince(System.currentTimeMillis() - days * DAY).map {
            RecentNote(it.problemId, it.occurredAt, factsFromJson(it.details), it.count)
        }

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
            Sync.local("note", "insert", id)
        }
        for (r in readings) ids += addReading(r, transcript = null, at = occurredAt, group = group)
        for (med in medicinesTaken) ids += addMedicineTaken(med, occurredAt, group)
        return ids
    }

    suspend fun updateFacts(id: Long, facts: Map<String, Fact>) {
        val n = db.notes().get(id) ?: return
        db.notes().update(n.copy(
            details = factsToJson(facts),
            severity = (facts["severity"]?.value as? Number)?.toInt(),
            count = (facts["count"]?.value as? Number)?.toInt(),
            text = n.problemId?.let { describe.line(it, facts) } ?: n.text,
        ))
        Sync.local("note", "update", id)
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
        db.notes().insert(Note(kind = Kind.SYMPTOM, problemId = problemId, occurredAt = System.currentTimeMillis(),
            details = factsToJson(mapOf("better" to Fact(true, Source.TAPPED))), text = "${cat.problem(problemId)?.label}: better now"))
    }

    // ───────── other notes ─────────

    suspend fun addWater(glasses: Int = 1, at: Long = System.currentTimeMillis()): Long {
        val id = db.notes().insert(Note(kind = Kind.WATER, occurredAt = at, count = glasses, text = "Water: $glasses ${if (glasses == 1) "glass" else "glasses"}"))
        Sync.local("note", "insert", id)
        return id
    }

    suspend fun waterToday(): Int {
        val start = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return db.notes().kindSince(Kind.WATER, start).sumOf { it.count ?: 1 }
    }

    suspend fun addFood(text: String, photo: String?, at: Long = System.currentTimeMillis()): Long {
        val id = db.notes().insert(Note(kind = Kind.FOOD, occurredAt = at, transcript = text, photoPath = photo, text = "Ate: $text".trim()))
        Sync.local("note", "insert", id)
        return id
    }

    suspend fun addReading(r: Reading, transcript: String?, at: Long = System.currentTimeMillis(), group: Long? = null): Long {
        val d = JSONObject().put("type", r.type).put("v1", r.v1).put("unit", r.unit)
        r.v2?.let { d.put("v2", it) }
        val id = db.notes().insert(Note(kind = Kind.READING, occurredAt = at, transcript = transcript, details = d.toString(), groupId = group, text = r.label()))
        Sync.local("note", "insert", id)
        return id
    }

    suspend fun addMedicineTaken(name: String, at: Long = System.currentTimeMillis(), group: Long? = null): Long {
        val id = db.notes().insert(Note(kind = Kind.MED_TAKEN, occurredAt = at, groupId = group, details = JSONObject().put("name", name).toString(), text = "Took $name"))
        Sync.local("note", "insert", id)
        return id
    }

    suspend fun addQuestion(text: String): Long {
        val id = db.notes().insert(Note(kind = Kind.QUESTION, occurredAt = System.currentTimeMillis(), transcript = text, text = "Question for doctor: $text"))
        Sync.local("note", "insert", id)
        return id
    }

    suspend fun addEvent(kind: String, text: String, details: String = "{}"): Long {
        val id = db.notes().insert(Note(kind = kind, occurredAt = System.currentTimeMillis(), details = details, text = text))
        Sync.local("note", "insert", id)
        return id
    }

    suspend fun remove(ids: List<Long>) = ids.forEach {
        db.notes().remove(it)
        Sync.local("note", "delete", it)
    }
    suspend fun restore(ids: List<Long>) = ids.forEach {
        db.notes().restore(it)
        Sync.local("note", "update", it)
    }
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
                Recent(pid, list.filter { it.occurredAt >= todayStart }.sumOf { it.count ?: 1 }, last, ongoing))
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
