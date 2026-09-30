package com.suryaprakash.medlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.meds.Scheduler
import com.suryaprakash.medlog.ui.Announce
import com.suryaprakash.medlog.ui.Hs
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalSettings
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.RowActions
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Senior
import com.suryaprakash.medlog.ui.Text
import com.suryaprakash.medlog.ui.steady
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Timeline tab: today's medicines, doses and what happened, in time order. */
@Composable
fun TimelineScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis()

    var dayOffset by remember { mutableStateOf(0) }
    var filters by remember { mutableStateOf(setOf(TlFilter.ALL)) }
    val openSegments = remember { mutableStateMapOf<Segment, Boolean>() }
    val isHelper = s.role == "helper"

    val (from, to) = TimelineLogic.dayBounds(if (isHelper) dayOffset else 0, now)

    val notesFlow by app.repo.db.notes().betweenFlow(from, to).collectAsState(initial = emptyList())
    val dosesFlow by app.repo.db.doses().betweenFlow(from, to).collectAsState(initial = emptyList())
    val medsFlow by app.repo.db.medicines().activeFlow().collectAsState(initial = emptyList())
    val appointmentsFlow by app.repo.db.appointments().upcomingFlow(from).collectAsState(initial = emptyList())

    val medsById = medsFlow.associateBy { it.id }
    val dosesById = dosesFlow.associateBy { it.medicineId }

    // Build TlEntry list
    val entries = mutableListOf<TlEntry>()

    // Add doses as MEDICINE entries
    dosesFlow.forEach { d ->
        val med = medsById[d.medicineId] ?: return@forEach
        val status = TimelineLogic.doseStatus(d.status, d.scheduledAt, now)
        entries.add(TlEntry(
            key = "dose_${d.id}",
            type = EntryType.MEDICINE,
            at = d.scheduledAt,
            title = med.name,
            line = "${med.strength} ${med.form} - ${med.amount}",
            by = "You",
            status = status,
            ref = d.id,
            kind = "DOSE"
        ))
    }

    // Add notes by kind
    notesFlow.forEach { note ->
        val type = when (note.kind) {
            "FOOD" -> EntryType.FOOD
            "READING" -> EntryType.READING
            "SYMPTOM" -> EntryType.SYMPTOM
            else -> return@forEach
        }
        val by = TimelineLogic.byOf(note.details)
        entries.add(TlEntry(
            key = "note_${note.id}",
            type = type,
            at = note.occurredAt,
            title = TimelineLogic.words(note.text),
            line = "",
            by = by,
            alert = note.severity != null && note.severity!! >= 2,
            ref = note.id,
            kind = note.kind
        ))
    }

    // Add appointments
    appointmentsFlow.forEach { apt ->
        if (apt.at in from until to) {
            entries.add(TlEntry(
                key = "appt_${apt.id}",
                type = EntryType.APPOINTMENT,
                at = apt.at,
                title = apt.doctor.ifBlank { "Appointment" },
                line = apt.place,
                by = "You",
                ref = apt.id,
                kind = "APPT"
            ))
        }
    }

    val buckets = TimelineLogic.bucket(entries, filters)
    val currentSeg = TimelineLogic.currentSegment(now)

    // Initialize open segments on first composition
    val segmentsToShow = remember {
        openSegments.clear()
        openSegments[currentSeg] = true
        openSegments
    }

    Screen("Timeline", "Today and what happened.", onHome = { nav.home() }) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Day selector: show only for helpers
            if (isHelper) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(Hs.Radius)).steady("Yesterday") { dayOffset-- }.padding(8.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.ChevronLeft, null, tint = Hs.Ink, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (dayOffset == 0) "Today" else if (dayOffset == -1) "Yesterday" else if (dayOffset == 1) "Tomorrow" else "Day $dayOffset",
                            fontSize = Hs.Body, fontWeight = FontWeight.Bold, color = Hs.Ink)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(Hs.Radius)).steady("Tomorrow") { dayOffset++ }.padding(8.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.ChevronRight, null, tint = Hs.Ink, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            // Filter chips: show only for helpers
            if (isHelper) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TlFilter.values().forEach { f ->
                            val selected = filters.contains(f)
                            val bg = if (selected) Hs.Blue else Hs.Paper
                            val fg = if (selected) Color.White else Hs.Ink
                            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(bg).border(1.dp, if (selected) Hs.Blue else Hs.Ink, RoundedCornerShape(20.dp))
                                .steady(f.label) {
                                    filters = if (f == TlFilter.ALL) {
                                        setOf(TlFilter.ALL)
                                    } else if (selected && filters.size == 1) {
                                        setOf(TlFilter.ALL)
                                    } else if (f == TlFilter.ALL && filters.contains(TlFilter.ALL)) {
                                        TlFilter.values().filterNot { it == TlFilter.ALL }.toSet()
                                    } else {
                                        val newFilters = filters - TlFilter.ALL
                                        (if (selected) newFilters - f else newFilters + f).ifEmpty { setOf(TlFilter.ALL) }
                                    }
                                }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text(f.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = fg)
                            }
                        }
                    }
                }
            }

            // Segments
            buckets.entries.forEachIndexed { idx, (seg, segs) ->
                item {
                    val isOpen = segmentsToShow[seg] ?: false
                    val status = TimelineLogic.segmentStatus(segs)
                    val statusColor = when (status) {
                        TlStatus.DONE -> Hs.Green
                        TlStatus.DUE -> Hs.Amber
                        TlStatus.MISSED -> Hs.Red
                        else -> Hs.Paper
                    }

                    // Map segment names to icons
                    val segmentIcon = when (seg.label) {
                        "Early morning" -> Icons.Rounded.WbTwilight
                        "Morning" -> Icons.Rounded.WbSunny
                        "Noon" -> Icons.Rounded.LightMode
                        "Afternoon" -> Icons.Rounded.WbCloudy
                        "Evening" -> Icons.Rounded.WbTwilight
                        "Night" -> Icons.Rounded.NightsStay
                        "Late night" -> Icons.Rounded.Bedtime
                        else -> Icons.Rounded.Schedule
                    }

                    Column {
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(Hs.Radius)).background(Hs.Paper)
                            .border(1.dp, Hs.Ink.copy(alpha = 0.1f), RoundedCornerShape(Hs.Radius))
                            .steady(seg.label) { segmentsToShow[seg] = !isOpen }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(segmentIcon, null, tint = Hs.Ink, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(seg.label, fontSize = Hs.Body, fontWeight = FontWeight.Bold, color = Hs.Ink)
                                Text(seg.range, fontSize = 13.sp, color = Hs.Ink.copy(alpha = 0.6f))
                            }
                            if (status != TlStatus.NONE) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(statusColor))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("${segs.size}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Hs.Ink.copy(alpha = 0.7f))
                            Spacer(Modifier.width(8.dp))
                            Icon(if (isOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = Hs.Ink, modifier = Modifier.size(24.dp))
                        }

                        if (isOpen) {
                            if (segs.isEmpty()) {
                                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Text("Nothing", fontSize = 14.sp, color = Hs.Ink.copy(alpha = 0.5f))
                                }
                            } else {
                                segs.forEach { entry ->
                                    val dose = dosesFlow.firstOrNull { it.id == entry.ref }
                                    val med = dose?.medicineId?.let { medsById[it] }
                                    TimelineRow(entry, medsById, dose, med,
                                        { med?.let { nav.go(Route.MedEdit(med.id)) } },
                                        { when (entry.kind) {
                                            "FOOD" -> nav.go(Route.FoodPick(entry.ref))
                                            "READING" -> nav.go(Route.Readings)
                                            "SYMPTOM" -> nav.go(Route.Tell(noteId = entry.ref))
                                            "APPT" -> nav.go(Route.Appointments)
                                            else -> {}
                                        }},
                                        { scope.launch { app.repo.remove(listOf(entry.ref)); Announce.done(ctx, null, "removed ${entry.title}", "delete", entry.ref) } },
                                        entry.type == EntryType.MEDICINE,
                                        { doseId -> scope.launch { Scheduler.take(ctx, doseId); Announce.done(ctx, null, "took ${entry.title}", "take", doseId) } },
                                        { doseId -> scope.launch { Scheduler.untake(ctx, doseId) } },
                                        { doseId -> scope.launch { Scheduler.skip(ctx, doseId, "Missed") } }
                                    )
                                }
                            }
                        }
                    }
                }
                if (idx < buckets.size - 1) item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    entry: TlEntry,
    medsById: Map<Long, Medicine>,
    dose: Dose?,
    med: Medicine?,
    onEditMed: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit,
    isMedicine: Boolean,
    onTaken: (Long) -> Unit,
    onUndo: (Long) -> Unit,
    onSkip: (Long) -> Unit,
) {
    val p = LocalPalette.current
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(entry.at))
    val icon = when (entry.type) {
        EntryType.MEDICINE -> Icons.Rounded.Medication
        EntryType.FOOD -> Icons.Rounded.Restaurant
        EntryType.READING -> Icons.Rounded.MonitorHeart
        EntryType.SYMPTOM -> Icons.Rounded.Warning
        EntryType.APPOINTMENT -> Icons.Rounded.Event
        else -> Icons.Rounded.Event
    }
    val iconTint = when (entry.type) {
        EntryType.MEDICINE -> Hs.Blue
        EntryType.FOOD -> Hs.Green
        EntryType.READING -> Hs.Amber
        EntryType.SYMPTOM -> Hs.Red
        EntryType.APPOINTMENT -> Hs.Blue
        else -> Hs.Ink
    }

    if (isMedicine && dose != null && med != null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            if (entry.alert) Box(Modifier.fillMaxWidth().height(3.dp).background(Hs.Red))
            DoseCard(dose, med,
                onOpen = onEditMed,
                onTaken = { onTaken(dose.id) },
                onUndo = { onUndo(dose.id) })
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            if (entry.alert) Box(Modifier.fillMaxWidth().height(3.dp).background(Hs.Red))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                Text(time, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Hs.Ink.copy(alpha = 0.7f), modifier = Modifier.width(60.dp))
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(36.dp).padding(top = 2.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Hs.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (entry.line.isNotBlank()) {
                        Text(entry.line, fontSize = 14.sp, color = Hs.Ink.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (entry.by != "You") {
                        Text("by ${entry.by}", fontSize = 12.sp, color = Hs.Ink.copy(alpha = 0.5f))
                    }
                }
            }
            RowActions(entry.title, onEditNote, onDelete)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hs.Ink.copy(alpha = 0.05f)))
        }
    }
}
