package com.suryaprakash.medlog

import com.suryaprakash.medlog.ui.screens.EntryType
import com.suryaprakash.medlog.ui.screens.Segment
import com.suryaprakash.medlog.ui.screens.TimelineLogic
import com.suryaprakash.medlog.ui.screens.TlEntry
import com.suryaprakash.medlog.ui.screens.TlFilter
import com.suryaprakash.medlog.ui.screens.TlStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimelineTest {
    private val z = ZoneId.of("UTC")
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 9, 30, h, m).atZone(z).toInstant().toEpochMilli()
    private fun e(h: Int, t: EntryType = EntryType.FOOD, s: TlStatus = TlStatus.NONE, by: String = "You") =
        TlEntry("$t$h", t, at(h), "x", "", by, status = s)

    @Test fun hoursLandInTheRightSegment() {
        assertEquals(Segment.LATE, Segment.of(0)); assertEquals(Segment.LATE, Segment.of(3))
        assertEquals(Segment.EARLY, Segment.of(4)); assertEquals(Segment.MORNING, Segment.of(7))
        assertEquals(Segment.NOON, Segment.of(12)); assertEquals(Segment.AFTERNOON, Segment.of(13))
        assertEquals(Segment.EVENING, Segment.of(19)); assertEquals(Segment.NIGHT, Segment.of(23))
    }

    @Test fun bucketKeepsAllSevenAndSorts() {
        val b = TimelineLogic.bucket(listOf(e(9), e(8), e(23)), emptySet(), z)
        assertEquals(7, b.size)
        assertEquals(listOf(at(8), at(9)), b[Segment.MORNING]!!.map { it.at })
        assertEquals(1, b[Segment.NIGHT]!!.size)
        assertEquals(0, b[Segment.LATE]!!.size)
    }

    @Test fun filtersAreAUnion() {
        val list = listOf(e(8, EntryType.MEDICINE), e(9, EntryType.FOOD), e(10, EntryType.READING, by = "Asha"))
        fun n(f: Set<TlFilter>) = TimelineLogic.bucket(list, f, z).values.sumOf { it.size }
        assertEquals(3, n(emptySet())); assertEquals(3, n(setOf(TlFilter.ALL)))
        assertEquals(1, n(setOf(TlFilter.FOOD))); assertEquals(2, n(setOf(TlFilter.FOOD, TlFilter.MEDICINES)))
        assertEquals(1, n(setOf(TlFilter.BY_HELPER)))
    }

    @Test fun statusMissedBeatsDueBeatsDone() {
        val m = EntryType.MEDICINE
        assertEquals(TlStatus.NONE, TimelineLogic.segmentStatus(listOf(e(8))))
        assertEquals(TlStatus.DONE, TimelineLogic.segmentStatus(listOf(e(8, m, TlStatus.DONE))))
        assertEquals(TlStatus.DUE, TimelineLogic.segmentStatus(listOf(e(8, m, TlStatus.DONE), e(9, m, TlStatus.DUE))))
        assertEquals(TlStatus.MISSED, TimelineLogic.segmentStatus(listOf(e(8, m, TlStatus.MISSED), e(9, m, TlStatus.DUE))))
    }

    @Test fun doseStatusAndDays() {
        assertEquals(TlStatus.DUE, TimelineLogic.doseStatus("DUE", at(8), at(9)))
        assertEquals(TlStatus.MISSED, TimelineLogic.doseStatus("DUE", at(8), at(11)))
        assertEquals(TlStatus.DONE, TimelineLogic.doseStatus("TAKEN", at(8), at(23)))
        val (s, end) = TimelineLogic.dayBounds(-1, at(10), z)
        assertEquals(24 * 3600_000L, end - s); assertEquals(at(0) - 24 * 3600_000L, s)
        assertEquals("a b c d", TimelineLogic.words("a b c d e f"))
        assertEquals("Asha", TimelineLogic.byOf("""{"by":"Asha"}""")); assertEquals("You", TimelineLogic.byOf("{}"))
    }
}
