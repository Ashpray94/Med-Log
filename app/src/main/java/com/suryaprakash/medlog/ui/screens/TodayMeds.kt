package com.suryaprakash.medlog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Text
import com.suryaprakash.medlog.ui.savedFeedback
import kotlinx.coroutines.launch

/** One medicine and its doses today. */
typealias MedDay = Pair<Medicine, List<Dose>>

/**
 * Which medicine needs attention first: one due now, then one missed, then the next one coming (soonest first),
 * then the ones done for the day.
 */
fun rankToday(days: List<MedDay>, now: Long = System.currentTimeMillis()): List<MedDay> = days.sortedWith(compareBy({ (_, ds) ->
    when {
        ds.any { it.status != DoseStatus.TAKEN && it.status != DoseStatus.MISSED && it.status != DoseStatus.SKIPPED && it.scheduledAt <= now + 10 * 60_000 } -> 0
        ds.any { it.status == DoseStatus.MISSED } -> 1
        ds.any { it.status == DoseStatus.DUE || it.status == DoseStatus.SNOOZED } -> 2
        else -> 3
    }
}, { (_, ds) -> ds.filter { it.status == DoseStatus.DUE }.minOfOrNull { it.scheduledAt } ?: Long.MAX_VALUE }))

/**
 * Today's medicines on a home page: one card, the one that needs attention first, and a line saying how the rest
 * stand. However many there are (3 or 150), it takes the same room; "See all" (in the section header) opens the list.
 * Nothing scrolls sideways.
 */
@Composable
fun TodayMedsPreview(days: List<MedDay>, card: @Composable (MedDay, Modifier) -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val ranked = remember(days) { rankToday(days) }
    if (ranked.isEmpty()) return
    card(ranked.first(), Modifier)
    if (ranked.size > 1) {
        val now = System.currentTimeMillis()
        val rest = ranked.drop(1)
        val missed = rest.count { (_, ds) -> ds.any { it.status == DoseStatus.MISSED } }
        val dueNow = rest.count { (_, ds) -> ds.any { it.status == DoseStatus.DUE && it.scheduledAt <= now + 10 * 60_000 } }
        val words = listOfNotNull(
            if (dueNow > 0) "$dueNow more due now" else null,
            if (missed > 0) "$missed more with a missed dose" else null,
        ).ifEmpty { listOf("${rest.size} more today") }.joinToString(" · ")
        Text(words, fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = if (missed + dueNow > 0) p.ink else p.inkSoft,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Every medicine for today, as a list that stays quick however long it is (only what's on screen is drawn), in
 * three parts: needs you now, later today, done.
 */
@Composable
fun TodayMedsScreen(nav: Nav, feeds: Boolean) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val scope = rememberCoroutineScope()
    val (start, end) = remember { com.suryaprakash.medlog.meds.Scheduler.today() }
    val db = app.viewDb
    val doses by db.doses().betweenFlow(start, end).collectAsState(emptyList())
    val meds by db.medicines().activeFlow().collectAsState(emptyList())
    val byId = meds.associateBy { it.id }
    // medicines and feeds are never mixed: this page is one or the other
    val days: List<MedDay> = doses.groupBy { it.medicineId }.mapNotNull { (id, ds) -> byId[id]?.let { it to ds } }.filter { (it.first.form == "feed") == feeds }
    val ranked = rankToday(days)
    val now = System.currentTimeMillis()
    fun part(d: MedDay) = when {
        d.second.any { it.status == DoseStatus.MISSED } || d.second.any { it.status == DoseStatus.DUE && it.scheduledAt <= now + 10 * 60_000 } -> 0
        d.second.any { it.status == DoseStatus.DUE || it.status == DoseStatus.SNOOZED } -> 1
        else -> 2
    }
    val parts = ranked.groupBy(::part)
    val taken = doses.count { it.status == DoseStatus.TAKEN && byId[it.medicineId]?.let { m -> (m.form == "feed") == feeds } == true }
    val total = days.sumOf { it.second.size }
    val viewing = com.suryaprakash.medlog.data.Viewing.pairId.value
    val who = viewing?.let { id -> com.suryaprakash.medlog.data.People.all(ctx).firstOrNull { it.pairId == id }?.name?.substringBefore(' ')?.ifBlank { null } ?: "They" }
    val title = when { feeds && viewing != null -> "Their feeds today"; feeds -> "Today's feeds"; viewing != null -> "Their medicines today"; else -> "Today's medicines" }
    val done = if (feeds) "given" else "taken"
    Screen(title, "$taken of $total $done.", onHome = { nav.home() }, onBack = { nav.back() },
        subtitle = "$taken of $total $done", scroll = false) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            listOf(0 to "Needs you now", 1 to "Later today", 2 to "Done for today").forEach { (k, title) ->
                val list = parts[k].orEmpty()
                if (list.isEmpty()) return@forEach
                item(key = "h$k") {
                    Text("$title · ${list.size}", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.padding(top = 8.dp))
                }
                items(list, key = { it.first.id }) { (m, ds) ->
                    DayCard(m, ds, onOpen = { nav.go(Route.Meds) },
                        onTaken = { d -> scope.launch { com.suryaprakash.medlog.data.Doses.take(ctx, d.id); savedFeedback(ctx) } },
                        onUndo = { d -> scope.launch { com.suryaprakash.medlog.data.Doses.untake(ctx, d.id) } },
                        onNotGiven = if (m.form == "feed") { d -> scope.launch { com.suryaprakash.medlog.data.Doses.skip(ctx, d.id, "Not given") } } else null,
                        onTakenAt = { d, at -> scope.launch { com.suryaprakash.medlog.data.Doses.take(ctx, d.id, at); savedFeedback(ctx) } }, who = who,
                        onAteInstead = { d -> scope.launch { com.suryaprakash.medlog.data.Doses.skip(ctx, d.id, com.suryaprakash.medlog.data.FOOD_INSTEAD) }; nav.go(Route.FoodPick()) })
                }
            }
            item(key = "add") {
                Column(Modifier.padding(top = 8.dp)) { if (feeds) com.suryaprakash.medlog.ui.DashedAddCard("Set up a feed") { nav.go(Route.FeedNew) } else com.suryaprakash.medlog.ui.DashedAddCard("Add a medicine") { nav.go(Route.MedEdit(null)) } }
            }
        }
    }
}
