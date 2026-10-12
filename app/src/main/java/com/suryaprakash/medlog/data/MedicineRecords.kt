package com.suryaprakash.medlog.data

import android.content.Context
import androidx.room.withTransaction
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.meds.Scheduler
import com.suryaprakash.medlog.integration.CalendarSync

object MedicineRecords {
    fun revision(old: Medicine?, edited: Medicine, now: Long): Medicine {
        val scheduleChanged = old != null && MedicineSchedule.changed(old, edited)
        return edited.copy(id = old?.id ?: edited.id, uid = old?.uid ?: edited.uid,
            updatedAt = old?.updatedAt ?: edited.updatedAt,
            changedAt = if (old == null || scheduleChanged || old.strength != edited.strength) now else old.changedAt,
            changeNote = when {
                old == null -> "started"
                scheduleChanged -> "times changed"
                old.strength != edited.strength -> "dose changed"
                else -> old.changeNote
            })
    }
    suspend fun save(ctx: Context, old: Medicine?, edited: Medicine): Long {
        val app = ctx.medlog; val db = app.viewDb
        val saved = revision(old, edited, System.currentTimeMillis())
        val openIds = if (!saved.active && old != null) db.doses().open().filter { it.medicineId == old.id }.map { it.id } else emptyList()
        val id = db.withTransaction {
            if (old != null) db.doses().everything().filter { it.medicineId == old.id }.forEach { d ->
                val pending = d.status in setOf(DoseStatus.DUE, DoseStatus.SNOOZED) && d.actedAt == null && d.scheduledAt >= System.currentTimeMillis()
                if (d.snapshot.isBlank() || pending) db.doses().update(d.copy(snapshot = DoseSnapshot.encode(if (pending) saved else old)))
            }
            if (old == null) db.medicines().insert(saved) else { db.medicines().update(saved); old.id }
        }
        if (!saved.active) {
            db.doses().cancelForMedicine(id, "Stopped")
            if (db === app.db) openIds.forEach { com.suryaprakash.medlog.meds.DoseAlert.cancel(ctx, it) }
        }
        Scheduler.reconcileSchedules(ctx, db)
        if (db === app.db) Scheduler.reschedule(ctx)
        db.medicines().get(id)?.let { CalendarSync.syncMedicine(ctx, it) }
        app.refreshWidgets()
        return id
    }
    /** Removal is from the current list. Past doses remain linked and syncable. */
    suspend fun remove(ctx: Context, id: Long, stopped: Boolean = false) {
        val app = ctx.medlog; val db = app.viewDb
        val old = db.medicines().get(id) ?: return
        val openIds = db.doses().open().filter { it.medicineId == id }.map { it.id }
        val m = old.copy(active = false, changedAt = System.currentTimeMillis(), changeNote = if (stopped) "stopped" else "removed")
        db.withTransaction {
            db.medicines().update(m)
            db.doses().cancelForMedicine(id, if (stopped) "Stopped" else "Removed from list")
        }
        if (db === app.db) openIds.forEach { com.suryaprakash.medlog.meds.DoseAlert.cancel(ctx, it) }
        CalendarSync.removeMedicine(ctx, m)
        if (db === app.db) Scheduler.reschedule(ctx)
        app.refreshWidgets()
    }
}
