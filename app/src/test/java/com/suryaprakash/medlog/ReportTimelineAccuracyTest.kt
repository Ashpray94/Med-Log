package com.suryaprakash.medlog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suryaprakash.medlog.data.*
import com.suryaprakash.medlog.doctor.*
import com.suryaprakash.medlog.nlu.*
import com.suryaprakash.medlog.nutrition.Nutrition
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
    @Test fun glanceReportOmitsNormalRedFlagAnswers() {
        assertFalse(Pdf.includeGlanceFact("Blood", "no blood"))
        assertFalse(Pdf.includeGlanceFact("Looks like coffee grounds", "not like coffee grounds"))
        assertFalse(Pdf.includeGlanceFact("Forceful vomiting", "not forceful"))
        assertTrue(Pdf.includeGlanceFact("Blood", "blood present"))
        assertTrue(Pdf.includeGlanceFact("Colour", "green"))
    }
    @Test fun reportUsesTheAppsLocalizedBodyView() {
        assertEquals(com.suryaprakash.medlog.pictogram.viewFor("stomach"), Pdf.localizedView(listOf(1 to "front:50,66")))
        assertEquals(com.suryaprakash.medlog.pictogram.viewFor("back"), Pdf.localizedView(listOf(1 to "back:44,72")))
        assertEquals(listOf("Upper stomach"), Pdf.locationLabels(listOf(1 to "front:50,66")))
        assertEquals(listOf("Right lower back"), Pdf.locationLabels(listOf(1 to "back:56,72")))
    }
    @Test fun reportUsesTheExistingDepthPictures() {
        assertEquals("depth_skin", Pdf.depthSprite("On the skin"))
        assertEquals("depth_under", Pdf.depthSprite("Just under the skin"))
        assertEquals("depth_muscle", Pdf.depthSprite("In the muscle"))
        assertEquals("depth_deep", Pdf.depthSprite("Deep inside"))
        assertEquals("depth_bone", Pdf.depthSprite("In the bone or joint"))
        assertNull(Pdf.reportDepth("bloating", "In the bone or joint"))
        assertEquals("In the muscle", Pdf.reportDepth("back_pain", "In the muscle"))
        assertFalse(com.suryaprakash.medlog.clinical.Interview.deepable(app.catalogue.problem("bloating")!!))
        assertTrue(com.suryaprakash.medlog.clinical.Interview.deepable(app.catalogue.problem("back_pain")!!))
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
    @Test fun recurringHistoryIncludesRecentAilmentsOutsideSelectedWeekAndDropsInactiveOnes() {
        val selected = build("cough", listOf("2026-10-02T22:17" to false))
        val recent = build("vomiting", listOf("2026-09-28T07:45" to false, "2026-09-30T09:01" to false))
        val inactive = build("back_pain", listOf("2026-09-21T07:45" to false, "2026-09-22T09:01" to false))
        val inactiveNumber = 2
        val history = recent.copy(
            symptoms = recent.symptoms + inactive.symptoms.map { it.copy(n = inactiveNumber) },
            entries = recent.entries + inactive.entries.map { it.copy(symptomNumber = inactiveNumber) },
        )
        val merged = selected.withRecurringHistory(history, at("2026-09-23", "00:00"))
        assertEquals(listOf("vomiting"), merged.recurringSymptoms.map { it.problemId })
        assertEquals(2, merged.recurringEntries.size)
        assertTrue(merged.symptoms.single().problemId == "cough")
        assertEquals(1, merged.symptoms.single().reportCount)
    }
    @Test fun oneOffBackPainEntryIsPrintedWithItsDateAndDetails() {
        val report = build("back_pain", listOf("2026-10-02T22:17" to false))
        val enriched = report.copy(entries = report.entries.map { it.copy(site = "Left lower back", depth = "In the muscle", remark = "Worse while walking") })
        val rows = Pdf.selectedEntryRows(enriched)
        assertEquals(1, rows.size)
        assertEquals("Back pain", rows.single()[1])
        assertTrue(rows.single()[0].contains("2 October 2026"))
        assertTrue(rows.single()[2].contains("Left lower back"))
        assertTrue(rows.single()[2].contains("In the muscle"))
        assertTrue(rows.single()[2].contains("Worse while walking"))
    }
    @Test
    @Config(shadows = [RecordingPdfDocument::class])
    fun pdfLayoutContainsReconciledEntries() {
        RecordingPdfDocument.pages.clear()
        val report = build("vomiting", listOf("2026-09-28T07:45" to false, "2026-09-28T08:43" to false,
            "2026-09-29T18:06" to true, "2026-09-30T09:01" to false, "2026-10-02T22:19" to false))
        val sample = report.copy(patient = "Example patient", pins = listOf(1 to "front:40,96", 1 to "front:51,92", 1 to "front:53,105"),
            entries = report.entries.mapIndexed { i, e -> if (!e.noted) e else e.copy(pins = listOf("front:${40+i*3},96"), site = "Upper stomach", depth = if (i == 0) "Deep inside" else null, remark = if (i == 0) "Example remark supplied with this entry." else null) })
        val file = Pdf.write(app, sample)
        assertTrue(file.exists())
        assertEquals(2, RecordingPdfDocument.pages.size)
    }

    @Test
    @Config(shadows = [RecordingPdfDocument::class])
    fun oneOffSymptomDoesNotCreateBlankRecurringPages() {
        RecordingPdfDocument.pages.clear()
        val report = build("cough", listOf("2026-10-02T22:17" to false))
        val file = Pdf.write(app, report)
        assertTrue(file.exists())
        assertEquals(1, RecordingPdfDocument.pages.size)
    }

    @Test
    @Config(shadows = [RecordingPdfDocument::class])
    fun nutritionFollowsOverviewWithoutBlankRecurringPages() {
        RecordingPdfDocument.pages.clear()
        val report = build("cough", listOf("2026-10-02T22:17" to false))
        val nutrition = Nutrition.Report(
            days = listOf(Nutrition.Day(java.time.LocalDate.of(2026, 10, 2), 900.0, 35.0, 4, listOf("Food"), true)),
            kcalTarget = 1800.0, proteinTarget = 60.0, targetsFromDoctor = false,
            avgKcal = 900.0, avgProtein = 35.0, weights = emptyList(), weightChange = null, weightDays = 0,
            headline = Nutrition.Finding("Logged nutrition needs review", "AMBER"),
            findings = listOf(Nutrition.Finding("Logged nutrition needs review", "AMBER")),
            missed = emptyList(), changes = emptyList(), observed = emptyList(), feeds = emptyList(), loggedDays = 1,
        )
        val file = Pdf.write(app, report, nutrition)
        assertTrue(file.exists())
        assertEquals(2, RecordingPdfDocument.pages.size)
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
