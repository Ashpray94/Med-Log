package com.suryaprakash.medlog.data

import com.suryaprakash.medlog.meds.Scheduler
import java.time.Instant
import java.time.ZoneId

object MedicineSchedule {
    const val REPLACED = "Schedule replaced"

    fun changed(old: Medicine, new: Medicine): Boolean =
        old.times != new.times || old.days != new.days || old.asNeeded != new.asNeeded ||
            old.startDate != new.startDate || old.endDate != new.endDate || old.active != new.active

    /** Cancel only unconfirmed slots affected by an explicit schedule edit. Earlier history and
     * actual taken/skipped actions are evidence and must be kept. Same-day moved slots are replaced. */
    fun obsolete(m: Medicine, d: Dose, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (d.medicineId != m.id || d.actedAt != null || d.status !in setOf(DoseStatus.DUE, DoseStatus.SNOOZED, DoseStatus.MISSED)) return false
        if (m.changeNote != "times changed") return false
        val date = Instant.ofEpochMilli(d.scheduledAt).atZone(zone).toLocalDate()
        if (date.isBefore(Instant.ofEpochMilli(m.changedAt).atZone(zone).toLocalDate())) return false
        return d.scheduledAt !in Scheduler.times(m, d.scheduledAt, d.scheduledAt + 1, zone)
    }
}
