package com.suryaprakash.medlog.integration

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import com.suryaprakash.medlog.data.Appointment
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.Perms
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * Medicine times and doctor visits in Google Calendar (plan 12.4).
 *
 * MedLog has no internet permission, so it writes to the phone's own calendar store; Android's calendar sync
 * (not MedLog) sends it to the person's Google account. Titles are neutral ("Medicine time") unless the person
 * chooses otherwise; medicine names stay on the phone.
 *
 * Every event carries a marker line in its description. Meeting Timer (same developer, same signing key) reads
 * it to show a medicine card with "I took it" instead of "Join", and tells MedLog through [BridgeReceiver].
 */
object CalendarSync {
    const val MARKER = "medlog:"
    private const val TAG = "MedLogCalendar"

    data class Cal(val id: Long, val name: String, val account: String, val google: Boolean)

    fun calendars(ctx: Context): List<Cal> {
        if (!Perms.has(ctx, *Perms.CALENDAR)) return emptyList()
        val out = ArrayList<Cal>()
        runCatching {
            ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
                null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(4) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                    out += Cal(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) == "com.google")
                }
            }
        }
        return out.sortedByDescending { it.google }
    }

    private fun calId(ctx: Context): Long? = ctx.medlog.settings.value.calendarId.takeIf { it > 0 && Perms.has(ctx, *Perms.CALENDAR) }

    /** Creates or updates one daily recurring event per medicine time. */
    suspend fun syncMedicine(ctx: Context, m: Medicine) {
        val cal = calId(ctx) ?: return
        val app = ctx.medlog
        removeMedicine(ctx, m)
        if (!m.active || m.asNeeded || m.times.isBlank()) return
        val neutral = app.settings.value.calendarNeutralTitles
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(m.startDate).atZone(zone).toLocalDate().let { if (it.isBefore(LocalDate.now())) LocalDate.now() else it }
        val ids = ArrayList<Long>()
        for (t in m.times.split(",").mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }) {
            val dt = start.atTime(t).atZone(zone).toInstant().toEpochMilli()
            val byDay = m.days.split(",").mapNotNull { it.trim().toIntOrNull() }.takeIf { it.isNotEmpty() }
                ?.joinToString(",", prefix = ";BYDAY=") { listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")[it - 1] } ?: ""
            val until = m.endDate?.let { ";UNTIL=" + java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(java.util.Date(it + DAY)) } ?: ""
            val v = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, cal)
                put(CalendarContract.Events.TITLE, if (neutral) "Medicine time" else "Medicine: ${m.name} ${m.strength}".trim())
                put(CalendarContract.Events.DESCRIPTION, "Open MedLog to confirm.\n$MARKER dose med=${m.id} time=$t")
                put(CalendarContract.Events.DTSTART, dt)
                put(CalendarContract.Events.DURATION, "PT15M")
                put(CalendarContract.Events.RRULE, "FREQ=${if (byDay.isEmpty()) "DAILY" else "WEEKLY"}$byDay$until")
                put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
                put(CalendarContract.Events.HAS_ALARM, 0)
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
            }
            runCatching { ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)?.lastPathSegment?.toLongOrNull()?.let { ids += it } }
                .onFailure { Log.w(TAG, "insert", it) }
        }
        app.settings.putString("cal_med_${m.id}", ids.joinToString(","))
    }

    fun removeMedicine(ctx: Context, m: Medicine) {
        if (!Perms.has(ctx, *Perms.CALENDAR)) return
        val ids = ctx.medlog.settings.getString("cal_med_${m.id}")?.split(",")?.mapNotNull { it.toLongOrNull() } ?: return
        for (id in ids) runCatching { ctx.contentResolver.delete(android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) }
        ctx.medlog.settings.putString("cal_med_${m.id}", null)
    }

    suspend fun syncAll(ctx: Context) { for (m in ctx.medlog.ownDb.medicines().all()) syncMedicine(ctx, m) }

    /** A doctor visit: normal event, so Meeting Timer reminds like any appointment. */
    fun addAppointment(ctx: Context, a: Appointment): Long? {
        val cal = calId(ctx) ?: return null
        val v = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, cal)
            put(CalendarContract.Events.TITLE, if (a.doctor.isNotBlank()) "Doctor: ${a.doctor}" else "Doctor visit")
            put(CalendarContract.Events.EVENT_LOCATION, a.place)
            put(CalendarContract.Events.DESCRIPTION, (a.purpose.takeIf { it.isNotBlank() }?.let { "$it\n" } ?: "") + "$MARKER visit id=${a.id}")
            put(CalendarContract.Events.DTSTART, a.at)
            put(CalendarContract.Events.DTEND, a.at + 3600_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
        }
        return runCatching { ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)?.lastPathSegment?.toLongOrNull() }.getOrNull()
    }

    fun meetingTimerInstalled(ctx: Context) = runCatching { ctx.packageManager.getPackageInfo(MEETING_TIMER, 0) }.isSuccess
    fun openMeetingTimer(ctx: Context) { ctx.packageManager.getLaunchIntentForPackage(MEETING_TIMER)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    const val MEETING_TIMER = "com.suryaprakash.meetingtimer"
}
