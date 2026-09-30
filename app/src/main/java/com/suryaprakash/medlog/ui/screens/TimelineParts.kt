package com.suryaprakash.medlog.ui.screens

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Pure timeline logic (no Android, no Compose) so it can be unit tested. */

/** The seven parts of the day, in the order they are listed. [from] inclusive, [to] exclusive hour. */
enum class Segment(val label: String, val from: Int, val to: Int) {
    EARLY("Early morning", 4, 7),
    MORNING("Morning", 7, 11),
    NOON("Noon", 11, 13),
    AFTERNOON("Afternoon", 13, 17),
    EVENING("Evening", 17, 20),
    NIGHT("Night", 20, 24),
    LATE("Late night", 0, 4);

    val range: String get() = "${hourWord(from)}–${hourWord(to)}"

    companion object {
        fun of(hour: Int): Segment = entries.first { hour.mod(24) in it.from until it.to }
    }
}

private fun hourWord(h: Int): String { val x = h % 24; return "${if (x % 12 == 0) 12 else x % 12} ${if (x < 12) "am" else "pm"}" }

enum class EntryType { MEDICINE, FOOD, READING, SYMPTOM, APPOINTMENT, HELPER }

enum class TlFilter(val label: String) {
    ALL("All"), MEDICINES("Medicines"), FOOD("Food"), READINGS("Readings"), SYMPTOMS("Symptoms"), APPOINTMENTS("Appointments"), BY_HELPER("By helper")
}

/** What a segment's status icon says. */
enum class TlStatus { NONE, DONE, DUE, MISSED }

/** One row of the timeline. [ref] is the id of the dose, note, appointment or inbox item, by [type]. */
data class TlEntry(
    val key: String,
    val type: EntryType,
    val at: Long,
    val title: String,
    val line: String,
    val by: String,              // "You" or a helper's name
    val alert: Boolean = false,
    val status: TlStatus = TlStatus.NONE,
    val ref: Long = 0,
    val kind: String = "",       // the note kind, or "DOSE" / "APPT" / "INBOX"
)

object TimelineLogic {
    /** Doses still open this long after their time count as missed. */
    const val MISSED_AFTER_MS = 2 * 3600_000L

    fun doseStatus(status: String, scheduledAt: Long, now: Long): TlStatus = when (status) {
        "TAKEN", "SKIPPED" -> TlStatus.DONE
        "MISSED" -> TlStatus.MISSED
        else -> if (now - scheduledAt > MISSED_AFTER_MS) TlStatus.MISSED else TlStatus.DUE
    }

    /** True when [e] shows under the chosen chips. No chips, or All, shows everything; several chips show the union. */
    fun matches(e: TlEntry, filters: Set<TlFilter>): Boolean {
        val f = filters - TlFilter.ALL
        if (f.isEmpty()) return true
        return f.any {
            when (it) {
                TlFilter.MEDICINES -> e.type == EntryType.MEDICINE
                TlFilter.FOOD -> e.type == EntryType.FOOD
                TlFilter.READINGS -> e.type == EntryType.READING
                TlFilter.SYMPTOMS -> e.type == EntryType.SYMPTOM
                TlFilter.APPOINTMENTS -> e.type == EntryType.APPOINTMENT
                TlFilter.BY_HELPER -> e.type == EntryType.HELPER || e.by != "You"
                TlFilter.ALL -> true
            }
        }
    }

    /** Every segment (even empty ones) with its filtered entries in time order. */
    fun bucket(entries: List<TlEntry>, filters: Set<TlFilter>, zone: ZoneId = ZoneId.systemDefault()): Map<Segment, List<TlEntry>> {
        val shown = entries.filter { matches(it, filters) }.sortedBy { it.at }
        val by = shown.groupBy { Segment.of(Instant.ofEpochMilli(it.at).atZone(zone).hour) }
        return Segment.values().associateWith { by[it].orEmpty() }
    }

    /** Missed beats due beats done. A segment with no medicines has no status. */
    fun segmentStatus(list: List<TlEntry>): TlStatus {
        val meds = list.filter { it.type == EntryType.MEDICINE && it.status != TlStatus.NONE }
        return when {
            meds.isEmpty() -> TlStatus.NONE
            meds.any { it.status == TlStatus.MISSED } -> TlStatus.MISSED
            meds.any { it.status == TlStatus.DUE } -> TlStatus.DUE
            else -> TlStatus.DONE
        }
    }

    fun currentSegment(now: Long, zone: ZoneId = ZoneId.systemDefault()): Segment = Segment.of(Instant.ofEpochMilli(now).atZone(zone).hour)

    /** Start (inclusive) and end (exclusive) of the day [offset] days from today (0 = today, -1 = yesterday). */
    fun dayBounds(offset: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val d: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(offset.toLong())
        return d.atStartOfDay(zone).toInstant().toEpochMilli() to d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** At most [n] words. */
    fun words(s: String, n: Int = 4): String = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(n).joinToString(" ")

    /** Who added a note: "by" in its details JSON, else You. */
    fun byOf(detailsJson: String): String =
        Regex("\"by\"\\s*:\\s*\"([^\"]+)\"").find(detailsJson)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: "You"
}
