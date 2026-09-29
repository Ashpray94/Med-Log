package com.suryaprakash.medlog.data

import com.suryaprakash.medlog.nlu.factsFromJson
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * How many times something happened, counted one way everywhere (History, Home, the doctor page and PDF, charts,
 * the widget and the danger rules).
 *
 * "How many times today?" is a running total for that day, not an amount to add: two notes that each say
 * "3 times today" mean 3, not 6. So a day's count is the highest total given that day, or the number of notes that
 * day if that is more. "Better now" notes are not occurrences.
 */
object Occurrences {
    data class E(val at: Long, val count: Int?)

    fun day(at: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

    fun perDay(es: List<E>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Int> =
        es.groupBy { day(it.at, zone) }.mapValues { (_, l) -> maxOf(l.size, l.maxOf { it.count ?: 0 }) }

    fun total(es: List<E>, zone: ZoneId = ZoneId.systemDefault()): Int = perDay(es, zone).values.sum()

    /** A symptom note that says it happened (not "better now", not removed). */
    fun isOccurrence(n: Note): Boolean = n.kind == Kind.SYMPTOM && n.problemId != null && n.deletedAt == null &&
        factsFromJson(n.details)["better"]?.value != true

    fun of(notes: List<Note>): List<E> = notes.filter(::isOccurrence).map { E(it.occurredAt, it.count) }
    fun total(notes: List<Note>): Int = total(of(notes))
    fun perDayOf(notes: List<Note>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Int> = perDay(of(notes), zone)
}

/**
 * Finds copies made by mistake: same kind, problem, details, count and text, within 2 minutes of the one kept.
 * Symptoms, toilet notes and readings only: water, food and medicines taken can truly be noted twice in a row.
 */
object Duplicates {
    const val WINDOW = 2 * 60_000L
    fun find(notes: List<Note>): List<Long> {
        val out = ArrayList<Long>()
        notes.filter { it.deletedAt == null && it.kind in setOf(Kind.SYMPTOM, Kind.OUTPUT, Kind.READING) }
            .groupBy { listOf(it.kind, it.problemId, it.details, it.count, it.severity, it.text) }
            .values.forEach { same ->
                var kept: Note? = null
                for (n in same.sortedBy { it.occurredAt }) {
                    val k = kept
                    if (k != null && n.occurredAt - k.occurredAt <= WINDOW) out += n.id else kept = n
                }
            }
        return out
    }
}
