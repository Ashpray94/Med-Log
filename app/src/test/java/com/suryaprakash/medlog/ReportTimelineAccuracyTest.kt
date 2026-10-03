package com.suryaprakash.medlog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suryaprakash.medlog.data.*
import com.suryaprakash.medlog.doctor.*
import com.suryaprakash.medlog.nlu.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ReportTimelineAccuracyTest {
    private val app = ApplicationProvider.getApplicationContext<Context>() as MedLogApp
    private fun at(day: String, time: String) = LocalDateTime.parse("${day}T$time").atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun build(pid: String, events: List<Pair<String, Boolean>>): DoctorNote {
        val notes = events.mapIndexed { i, (stamp, better) ->
            Note(id = i.toLong() + 1, kind = Kind.SYMPTOM, problemId = pid,
                occurredAt = LocalDateTime.parse(stamp).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                count = if (pid == "vomiting" && !better) 1 else null,
                details = if (better) factsToJson(mapOf("better" to Fact(true, Source.TAPPED))) else "{}")
        }
        return DoctorNoteBuilder(app.catalogue, app.describe).build(Profile(), at("2026-09-20", "00:00"), at("2026-10-04", "00:00"), notes, emptyList(), emptyList(), now = at("2026-10-03", "08:00"))
    }
    @Test fun vomitingSummaryMatchesEveryDatedEntry() {
        val report = build("vomiting", listOf("2026-09-28T07:45" to false, "2026-09-28T08:43" to false,
            "2026-09-29T18:06" to true, "2026-09-30T09:01" to false, "2026-10-02T22:19" to false))
        val row = report.symptoms.single()
        assertEquals(4, row.reportCount)
        assertEquals(listOf(2, 1, 1), row.daily.filter { it > 0 })
        assertEquals(4, row.daily.sum())
        assertEquals("2 October 2026, 10:19 PM", row.lastNoted)
        assertEquals(5, report.entries.size)
        assertEquals(4, report.entries.count { it.noted })
        assertEquals(1, row.reports24h)
        assertTrue(ReportValidation.errors(report).isEmpty())
        assertFalse(row.whenText.contains("daily count"))
        assertTrue(ReportValidation.errors(report.copy(symptoms = listOf(row.copy(reportCount = 6)))).isNotEmpty())
        assertTrue(ReportValidation.errors(report.copy(symptoms = listOf(row.copy(lastNoted = "wrong")))).isNotEmpty())
        assertTrue(ReportValidation.errors(report.copy(symptoms = listOf(row.copy(daily = List(14) { 4 })))).isNotEmpty())
    }
    @Test fun coughSummaryCountsNotesAndExcludesImprovementUpdates() {
        val report = build("cough", listOf("2026-09-27T07:57", "2026-09-27T08:09", "2026-09-27T09:16",
            "2026-09-27T09:38", "2026-09-27T14:19", "2026-09-27T22:40", "2026-09-27T22:44",
            "2026-09-28T07:50", "2026-09-29T22:34", "2026-10-02T22:17").map { it to false } +
            listOf("2026-09-29T18:06" to true, "2026-09-30T19:51" to true))
        val row = report.symptoms.single()
        assertEquals(10, row.reportCount)
        assertEquals(listOf(7, 1, 1, 1), row.daily.filter { it > 0 })
        assertEquals("2 October 2026, 10:17 PM", row.lastNoted)
        assertEquals(12, report.entries.size)
        assertEquals(10, report.entries.count { it.noted })
        assertTrue(ReportValidation.errors(report).isEmpty())
    }
    @Test
    @Config(shadows = [RecordingPdfDocument::class])
    fun pdfLayoutContainsReconciledEntries() {
        val report = build("vomiting", listOf("2026-09-28T07:45" to false, "2026-09-28T08:43" to false,
            "2026-09-29T18:06" to true, "2026-09-30T09:01" to false, "2026-10-02T22:19" to false))
        val sample = report.copy(patient = "Example patient", pins = listOf(1 to "front:40,96", 1 to "front:51,92", 1 to "front:53,105"),
            entries = report.entries.mapIndexed { i, e -> if (!e.noted) e else e.copy(pins = listOf("front:${40+i*3},96"), site = "Upper stomach", depth = if (i == 0) "Deep inside" else null, remark = if (i == 0) "Example remark supplied with this entry." else null) })
        val file = Pdf.write(app, sample)
        assertTrue(file.exists())
        assertTrue(RecordingPdfDocument.pages.size >= 2)
    }

    @Test fun crowdedLabelsKeepRecordedAnchorsWithoutOverlap() {
        val pins = listOf(3 to "front:40,96", 3 to "front:51,92", 5 to "front:53,105", 5 to "front:43,104")
        val marks = BodyMarkers.layout(pins + pins.first())
        assertEquals(4, marks.size)
        assertEquals(40f, marks.first().x, 0f)
        assertEquals(96f, marks.first().y, 0f)
        for (i in marks.indices) for (j in i+1 until marks.size)
            assertTrue(kotlin.math.hypot(marks[i].labelX-marks[j].labelX, marks[i].labelY-marks[j].labelY) >= 21f)
    }

    /** Robolectric lacks PdfDocument native IO. Record the app's actual Canvas pages for visual QA. */
    @org.robolectric.annotation.Implements(android.graphics.pdf.PdfDocument::class)
    class RecordingPdfDocument {
        private val bitmaps = mutableMapOf<android.graphics.pdf.PdfDocument.Page, android.graphics.Bitmap>()
        @org.robolectric.annotation.Implementation
        fun startPage(info: android.graphics.pdf.PdfDocument.PageInfo): android.graphics.pdf.PdfDocument.Page {
            val bitmap = android.graphics.Bitmap.createBitmap(info.pageWidth * 2, info.pageHeight * 2, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.scale(2f, 2f)
            val page = org.robolectric.util.ReflectionHelpers.callConstructor(android.graphics.pdf.PdfDocument.Page::class.java,
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.graphics.Canvas::class.java, canvas),
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.graphics.pdf.PdfDocument.PageInfo::class.java, info))
            bitmaps[page] = bitmap
            return page
        }
        @org.robolectric.annotation.Implementation
        fun finishPage(page: android.graphics.pdf.PdfDocument.Page) {
            val bitmap = bitmaps.remove(page)!!
            val out = java.io.File("build/qa/report-page-${page.info.pageNumber}.png")
            out.parentFile?.mkdirs()
            out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            pages.add(out)
        }
        @org.robolectric.annotation.Implementation fun writeTo(stream: java.io.OutputStream) { stream.write("Layout QA only".toByteArray()) }
        @org.robolectric.annotation.Implementation fun close() {}
        companion object { val pages = mutableListOf<java.io.File>() }
    }

}
