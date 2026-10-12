package com.suryaprakash.medlog.data

import com.suryaprakash.medlog.nlu.factsFromJson
import java.util.Locale

/** Reporting never turns a check-in into an episode, an old alert into a current finding,
 * or an old medicine-list entry into a claim that treatment is currently stopped. */
object ReportIntegrity {
    fun notes(notes: List<Note>, from: Long, to: Long, now: Long): List<Note> =
        notes.withIndex().groupBy { (i, n) ->
            if (n.uid.isNotBlank()) "uid:${n.uid}" else if (n.id != 0L) "id:${n.id}" else "unsaved:$i"
        }.values.map { copies -> copies.maxBy { it.value.updatedAt }.value }
            .filter { it.deletedAt == null && it.occurredAt >= from && it.occurredAt < to && it.occurredAt <= now }

    fun doses(doses: List<Dose>, medicines: List<Medicine>, from: Long, to: Long, now: Long): List<Dose> {
        val byId = medicines.associateBy { it.id }
        return doses.withIndex().groupBy { (i, d) ->
            if (d.uid.isNotBlank()) "uid:${d.uid}" else if (d.id != 0L) "id:${d.id}" else "unsaved:$i"
        }.values.map { it.maxBy { copy -> copy.value.updatedAt }.value }.planned()
            .filter { it.scheduledAt >= from && it.scheduledAt < minOf(to, now) &&
                byId[it.medicineId]?.let { m -> !MedicineSchedule.obsolete(m, it) } != false }
            .distinctBy { it.medicineId to it.scheduledAt }
    }

    fun reports(notes: List<Note>): List<Note> = notes.filter { n ->
        Occurrences.isOccurrence(n)
    }

    fun countWords(notes: List<Note>): String {
        val n = reports(notes).size
        return "$n symptom report${if (n == 1) "" else "s"}"
    }

    /** Old versions stored cumulative frequency alerts even when later edits corrected the count.
     * Preserve that an alert was recorded, but never republish its unverified numeric claim. */
    fun reasons(n: Note): List<String> = n.triageReasons.lines().filter { it.isNotBlank() }.map { reason ->
        if (Regex("^(Vomiting|Loose motions) \\d+ times in 24 hours").containsMatchIn(reason))
            "Frequency alert recorded; original count needs review"
        else reason
    }.distinct()

    fun medicineKey(m: Medicine): String = listOf(m.name, m.strength, m.form, m.amount)
        .joinToString("|") { it.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") }

    fun continued(m: Medicine, medicines: List<Medicine>): Boolean = !m.active &&
        medicines.any { it.active && medicineKey(it) == medicineKey(m) }
}
