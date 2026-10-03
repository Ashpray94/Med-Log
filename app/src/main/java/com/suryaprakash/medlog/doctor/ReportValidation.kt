package com.suryaprakash.medlog.doctor

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportValidation {
    fun errors(note: DoctorNote, zone: ZoneId = ZoneId.systemDefault()): List<String> = buildList {
        val first = Instant.ofEpochMilli(note.from).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli(note.to - 1).atZone(zone).toLocalDate()
        val span = ChronoUnit.DAYS.between(first,end).toInt() + 1
        if (span != note.days) add("Range length differs from the selected dates")
        for (row in note.symptoms) {
            val entries = note.entries.filter { it.symptomNumber == row.n && it.noted }
            if (entries.size != row.reportCount) add("${row.name}: count differs from entries")
            val byDay = entries.groupingBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.eachCount()
            val daily = (0 until span).map { byDay[first.plusDays(it.toLong())] ?: 0 }
            if (daily != row.daily || row.daily.sum() != row.reportCount) add("${row.name}: graph differs from dated entries")
            val last = entries.maxOfOrNull { it.at }?.let { SimpleDateFormat("d MMMM yyyy, h:mm a", Locale.ENGLISH).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.format(Date(it)) }
            if (last != row.lastNoted) add("${row.name}: last-noted time differs from entries")
            if (entries.any { it.at < note.from || it.at >= note.to }) add("${row.name}: entry outside selected range")
        }
    }
    fun requireAccurate(note: DoctorNote) { require(errors(note).isEmpty()) { errors(note).joinToString("; ") } }
}
