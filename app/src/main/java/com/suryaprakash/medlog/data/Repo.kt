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

    fun ageYears(dob: String): Int? = ageFromDob(dob)

    // ───────── symptoms ─────────

    /** The last few days' symptoms for the danger rules; [exclude] is the note being written, which the rules count from its own answers. */
    suspend fun recentForRules(days: Int = 3, exclude: Long? = null): List<RecentNote> =
        db.notes().symptomsSince(System.currentTimeMillis() - days * DAY).filter { it.id != exclude && Occurrences.isOccurrence(it) }.map {
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
            // from the saved row, which now has its shared id and time: the unsaved copy has neither, and writing it back
            // blanked them (the note then never reached the other phone properly)
            if (group == null) { group = id; db.notes().get(id)?.let { db.notes().update(it.copy(groupId = id)) } }
            ids += id
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

    /**
     * Copies made by mistake (a link acted on twice, a double tap): the same kind of note, the same problem, exactly
     * the same details, within 2 minutes of each other. The first stays; the others go to Removed, where they can be
     * put back. Returns how many were moved.
     */
    /**
     * The same entry held more than once under different ids (sent back by another phone before ids were kept; see
     * DATA_RULES.md): same kind, problem, time it happened, time it was made and words. One stays, with the smallest id,
     * so every phone keeps the same one; the others go to Removed. The one kept is sent again so the other phones match.
     * Safe to run any time: two different entries never share the moment they were made. Returns how many were merged.
     */
    suspend fun mergeCopies(): Int {
        var merged = 0
        db.notes().between(0, Long.MAX_VALUE).groupBy { listOf(it.kind, it.problemId.orEmpty(), it.occurredAt, it.createdAt, it.text) }.values
            .filter { it.size > 1 }.forEach { same ->
                val keep = same.minBy { it.uid }
                same.filter { it.id != keep.id }.forEach { db.notes().remove(it.id); merged++ }
                db.notes().get(keep.id)?.let { db.notes().update(it) }   // a new time, so it's sent again after the removals
            }
        return merged
    }

    suspend fun removeDuplicates(days: Int = 120): Int = Duplicates.find(db.notes().between(System.currentTimeMillis() - days * DAY, Long.MAX_VALUE))
        .onEach { db.notes().remove(it) }.size

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
                Recent(pid, com.suryaprakash.medlog.data.Occurrences.total(list.filter { it.occurredAt >= todayStart }), last, ongoing))
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

        /** Age from the year of birth only: setup asks for the year, so the day and month are never assumed. */
        fun ageFromDob(dob: String): Int? = dob.take(4).toIntOrNull()?.takeIf { it in 1900..LocalDate.now().year }?.let { LocalDate.now().year - it }
    }
}
