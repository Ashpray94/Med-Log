package com.suryaprakash.medlog.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Share
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.data.Appointment
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.doctor.Pdf
import com.suryaprakash.medlog.doctor.Summary
import com.suryaprakash.medlog.doctor.SummaryBuilder
import com.suryaprakash.medlog.integration.CalendarSync
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Chip
import com.suryaprakash.medlog.ui.FlowRowOf
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.LocalSettings
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Perms
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.RowActions
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Title
import com.suryaprakash.medlog.ui.Toggle
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.Announce
import com.suryaprakash.medlog.ui.savedFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

suspend fun buildNote(ctx: android.content.Context, days: Int): com.suryaprakash.medlog.doctor.DoctorNote {
    val app = ctx.medlog
    val zone = ZoneId.systemDefault()
    val to = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val from = to - days * DAY
    return com.suryaprakash.medlog.doctor.DoctorNoteBuilder(app.catalogue, app.describe).build(
        app.repo.profile(), from, to, app.db.notes().between(from, to), app.db.medicines().all(), app.db.doses().between(from, to),
        translit = { com.suryaprakash.medlog.speech.Translit.toLatin(it) })
}

/** After the visit (the appointment-log form you shared): 30 seconds of voice fills it in. */
@Composable
fun VisitScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var f by remember { mutableStateOf(VisitForm()) }
    LaunchedEffect(Unit) { app.repo.profile().doctorName.takeIf { it.isNotBlank() }?.let { f = f.copy(doctor = it) } }
    Screen("After the visit", "What did the doctor say? Fill in what you remember, or tap Speak and tell me: who you saw, what they did, any new medicine, and when to go back.", onHome = { nav.home() }, onBack = { nav.back() }) {
        com.suryaprakash.medlog.ui.rememberDictation("What did the doctor say?") { f = VisitParser.fill(f, it) }?.let { speak ->
            BigButton("Speak", tone = Tone.QUIET, icon = Icons.Rounded.Mic, sub = "Say who you saw, any new medicine, when to go back", onClick = speak)
        }
        BigField("Doctor", f.doctor, { f = f.copy(doctor = it) })
        BigField("Did anyone come with you?", f.accompanied, { f = f.copy(accompanied = it) })
        BigField("Reason for visit", f.reason, { f = f.copy(reason = it) })
        BigField("What happened (tests, injections)", f.happened, { f = f.copy(happened = it) }, lines = 2)
        Toggle("Referred to a specialist or for tests", f.referral) { f = f.copy(referral = it) }
        if (f.referral) {
            BigField("To whom", f.referTo, { f = f.copy(referTo = it) })
            BigField("For what", f.referFor, { f = f.copy(referFor = it) })
            BigField("Referral number", f.referNumber, { f = f.copy(referNumber = it) })
        }
        BigField("New prescription", f.newMedicine, { f = f.copy(newMedicine = it) }, hint = "Name and dose")
        BigField("What is it for?", f.medicineFor, { f = f.copy(medicineFor = it) })
        BigField("Next appointment", f.nextText, { f = f.copy(nextText = it) }, hint = f.nextAt?.let { "Understood as ${dayLabel(it)} ${timeLabel(it)}" } ?: "For example: in 2 weeks")
        BigField("Notes or questions for next time", f.notes, { f = f.copy(notes = it) }, lines = 2)
        BigButton("Save", tone = Tone.OK, onClick = {
            scope.launch {
                val o = JSONObject().put("doctor", f.doctor).put("with", f.accompanied).put("reason", f.reason).put("happened", f.happened)
                    .put("referral", f.referral).put("referTo", f.referTo).put("referFor", f.referFor).put("referNumber", f.referNumber)
                    .put("newMedicine", f.newMedicine).put("for", f.medicineFor).put("next", f.nextText).put("notes", f.notes)
                app.repo.addEvent(Kind.VISIT, "Saw ${f.doctor.ifBlank { "the doctor" }}" + (f.reason.takeIf { it.isNotBlank() }?.let { " for $it" } ?: "") + (f.happened.takeIf { it.isNotBlank() }?.let { ". $it" } ?: ""), o.toString())
                if (f.notes.isNotBlank()) app.repo.addQuestion(f.notes)
                f.nextAt?.let { at ->
                    val a = Appointment(at = at, doctor = f.doctor, purpose = "Follow-up")
                    val id = app.db.appointments().insert(a)
                    CalendarSync.addAppointment(ctx, a.copy(id = id))?.let { ev -> app.db.appointments().update(a.copy(id = id, calendarEventId = ev)) }
                    com.suryaprakash.medlog.meds.Scheduler.reschedule(ctx)
                }
                savedFeedback(ctx)
                if (f.newMedicine.isNotBlank()) nav.replace(Route.MedEdit(null)) else nav.back()
            }
        })
        if (f.newMedicine.isNotBlank()) Hint("After saving, you'll add the new medicine so MedLog can remind you.")
    }
}

data class VisitForm(
    val doctor: String = "", val accompanied: String = "", val reason: String = "", val happened: String = "",
    val referral: Boolean = false, val referTo: String = "", val referFor: String = "", val referNumber: String = "",
    val newMedicine: String = "", val medicineFor: String = "", val nextText: String = "", val nextAt: Long? = null, val notes: String = "",
)

/** Fills the visit form from what the person said. Keeps their words; only obvious pieces are pulled out. */
object VisitParser {
    fun fill(f: VisitForm, raw: String): VisitForm {
        val t = com.suryaprakash.medlog.nlu.Normalize.text(raw)
        var out = f
        Regex("\\b(?:dr|doctor)\\.?\\s+([a-z]+(?:\\s+[a-z]+)?)").find(t)?.let { out = out.copy(doctor = "Dr " + it.groupValues[1].split(" ").joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }) }
        Regex("\\b(?:came with|went with|accompanied by)\\s+(?:my\\s+)?([a-z ]{2,20}?)(?=$|\\s(?:and|the|doctor)\\b)").find(t)?.let { out = out.copy(accompanied = it.groupValues[1].trim()) }
        Regex("\\bfor (?:my\\s+)?([a-z ]{3,30}?)(?=$|\\s(?:and|the doctor|he|she|they)\\b)").find(t)?.let { if (out.reason.isBlank()) out = out.copy(reason = it.groupValues[1].trim()) }
        val tests = Regex("\\b(blood test|urine test|x ray|xray|scan|ct scan|mri|ultrasound|ecg|echo|sugar test|injection|vaccine|flu shot|dressing|endoscopy|biopsy)s?\\b").findAll(t).map { it.value }.distinct().toList()
        if (tests.isNotEmpty()) out = out.copy(happened = (listOf(out.happened).filter { it.isNotBlank() } + tests).joinToString(", "))
        Regex("\\b(?:referred|refer|sent)\\s+(?:me\\s+)?to\\s+(?:a\\s+|the\\s+)?([a-z ]{3,30}?)(?=$|\\s(?:for|and)\\b)").find(t)?.let { out = out.copy(referral = true, referTo = it.groupValues[1].trim()) }
        Regex("\\b(?:gave|prescribed|started|new)\\s+(?:me\\s+)?(?:a\\s+)?(?:new\\s+)?(?:tablet|medicine|syrup|capsule)?\\s*(?:called\\s+)?([a-z]+(?:\\s+\\d+\\s*(?:mg|ml))?)").find(t)?.let {
            val m = it.groupValues[1].trim(); if (m.length >= 3 && m !in setOf("the", "tablet", "medicine", "some")) out = out.copy(newMedicine = m.replaceFirstChar(Char::uppercase))
        }
        next(t)?.let { (text, at) -> out = out.copy(nextText = text, nextAt = at) }
        if (out.notes.isBlank()) out = out.copy(notes = "")
        return out
    }

    /** "come back after 2 weeks", "next visit on 5 october", "see you in a month" */
    fun next(t: String, now: LocalDateTime = LocalDateTime.now()): Pair<String, Long>? {
        fun at(d: LocalDateTime) = d.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        Regex("\\b(?:after|in)\\s+(\\d{1,2}|a|an|one)\\s+(day|week|month)s?\\b").find(t)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: 1
            val d = when (it.groupValues[2]) { "day" -> now.plusDays(n.toLong()); "week" -> now.plusWeeks(n.toLong()); else -> now.plusMonths(n.toLong()) }
            return it.value to at(d.withHour(10).withMinute(0))
        }
        Regex("\\bnext (week|month)\\b").find(t)?.let { return it.value to at((if (it.groupValues[1] == "week") now.plusWeeks(1) else now.plusMonths(1)).withHour(10).withMinute(0)) }
        Regex("\\bon\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s+([a-z]{3,9})\\b").find(t)?.let { m ->
            val month = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").indexOf(m.groupValues[2].take(3)) + 1
            if (month > 0) {
                var d = runCatching { LocalDate.of(now.year, month, m.groupValues[1].toInt()) }.getOrNull() ?: return null
                if (d.isBefore(now.toLocalDate())) d = d.plusYears(1)
                return m.value to at(d.atTime(10, 0))
            }
        }
        return null
    }
}

@Composable
fun AppointmentsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val list by app.db.appointments().upcomingFlow(System.currentTimeMillis() - DAY).collectAsState(emptyList())
    var date by remember { mutableStateOf<LocalDateTime?>(null) }
    var doctor by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    Screen("Doctor appointments", "Your next doctor visits. MedLog reminds you the evening before and prepares your doctor page.", onHome = { nav.home() }, onBack = { nav.back() }) {
        if (list.isEmpty()) Hint("No appointments yet.")
        list.forEach { a ->
            AppointmentRow(a, onEdit = {
                editingId = a.id
                date = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(a.at), ZoneId.systemDefault())
                doctor = a.doctor; place = a.place; purpose = a.purpose
            }, onDelete = {
                scope.launch {
                    app.db.appointments().delete(a.id)
                    Announce.done(ctx, null, "removed appointment", "delete", a.id)
                }
            })
        }
        Title("Add an appointment")
        FlowRowOf {
            val now = LocalDateTime.now()
            listOf("Tomorrow" to now.plusDays(1), "In 1 week" to now.plusWeeks(1), "In 1 month" to now.plusMonths(1)).forEach { (l, d) -> Chip(l, date?.toLocalDate() == d.toLocalDate()) { date = d.withHour(10).withMinute(0) } }
            Chip("Pick a date", false) {
                val n = LocalDate.now()
                DatePickerDialog(ctx, { _, y, m, d -> TimePickerDialog(ctx, { _, h, mi -> date = LocalDateTime.of(y, m + 1, d, h, mi) }, 10, 0, false).show() }, n.year, n.monthValue - 1, n.dayOfMonth).show()
            }
        }
        date?.let { Body("${dayLabel(it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())} at ${timeLabel(it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())}", bold = true) }
        BigField("Doctor", doctor, { doctor = it })
        BigField("Place", place, { place = it })
        BigField("For what", purpose, { purpose = it })
        BigButton(if (editingId != null) "Update appointment" else "Save", tone = Tone.OK, icon = Icons.Rounded.Add, enabled = date != null, onClick = {
            scope.launch {
                val at = date!!.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (editingId != null) {
                    val a = Appointment(id = editingId!!, at = at, doctor = doctor.trim(), place = place.trim(), purpose = purpose.trim())
                    app.db.appointments().update(a)
                    Announce.done(ctx, null, "updated appointment", "edit", editingId!!)
                } else {
                    val a = Appointment(at = at, doctor = doctor.trim(), place = place.trim(), purpose = purpose.trim())
                    val id = app.db.appointments().insert(a)
                    CalendarSync.addAppointment(ctx, a.copy(id = id))?.let { ev -> app.db.appointments().update(a.copy(id = id, calendarEventId = ev)) }
                    Announce.done(ctx, null, "added appointment", "add", id)
                }
                com.suryaprakash.medlog.meds.Scheduler.reschedule(ctx)
                date = null; doctor = ""; place = ""; purpose = ""; editingId = null
                savedFeedback(ctx)
            }
        })
    }
}

@Suppress("unused") private val keepSettings = LocalSettings

/** An appointment with Edit and Delete actions. */
@Composable
fun AppointmentRow(a: Appointment, onEdit: () -> Unit, onDelete: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().clip(sh).background(p.card).border(1.dp, p.line, sh).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("${dayLabel(a.at)} · ${timeLabel(a.at)}", fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink)
            Text(listOf(a.doctor, a.place, a.purpose).filter { it.isNotBlank() }.joinToString(" · "), fontSize = sc.small, color = p.inkSoft)
        }
        RowActions(what = a.doctor.ifBlank { "Appointment" }, onEdit = onEdit, onDelete = onDelete)
    }
}
