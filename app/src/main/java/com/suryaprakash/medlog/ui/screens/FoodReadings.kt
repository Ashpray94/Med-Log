package com.suryaprakash.medlog.ui.screens
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Close
import com.suryaprakash.medlog.ui.cardTitle
import com.suryaprakash.medlog.ui.steady
import com.suryaprakash.medlog.ui.Route
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.MonitorHeart

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Mic
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.Triage
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.help.Alerts
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.nlu.Reading
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
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Title
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.UndoHost
import com.suryaprakash.medlog.ui.savedFeedback
import kotlinx.coroutines.launch
import java.io.File

/** Which view Food & water was on, kept while you step into a page and back. */
private object FoodTab { val tab = mutableStateOf(0) }

/** Food & water: three views, one at a time. Water fills a glass; food is read into calories and protein; feeds are scheduled like medicines. */
@Composable
fun FoodScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    var water by remember { mutableStateOf(0) }
    var food by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<File?>(null) }
    var pending by remember { mutableStateOf<File?>(null) }
    var tab by FoodTab.tab
    var goalSheet by remember { mutableStateOf(false) }
    var feedSheet by remember { mutableStateOf(false) }
    var feedMenu by remember { mutableStateOf<com.suryaprakash.medlog.data.Medicine?>(null) }
    val (start, end) = remember { com.suryaprakash.medlog.meds.Scheduler.today() }
    val todayFood by app.db.notes().kindSinceFlow(Kind.FOOD, start).collectAsState(emptyList())
    val allMeds by app.db.medicines().activeFlow().collectAsState(emptyList())
    val feeds = allMeds.filter { it.form == "feed" }
    val doses by app.db.doses().betweenFlow(start, end).collectAsState(emptyList())
    LaunchedEffect(Unit) { water = app.repo.waterToday() }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) photo = pending }

    Screen("Food & water", "Water, food and feeds, one at a time. Tap Water, Food or Feeds at the top.", onHome = { nav.home() }, onBack = { nav.back() },
        subtitle = "What goes in today") {
        com.suryaprakash.medlog.ui.Segmented(listOf("Water", "Food", "Feeds"), tab) { tab = it }
        when (tab) {
            0 -> {
                com.suryaprakash.medlog.ui.SectionHeader("Water", "Goal: ${s.waterGoal} glasses a day", "Change goal") { goalSheet = true }
                val wsh = RoundedCornerShape(sc.radius)
                Row(Modifier.fillMaxWidth().clip(wsh).background(p.card).border(1.dp, p.line, wsh).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    WaterGlass(water, s.waterGoal, Modifier.width(120.dp).height(170.dp))
                    Spacer(Modifier.width(22.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column {
                            Text("$water of ${s.waterGoal}", fontSize = sc.title * 1.2f, fontWeight = FontWeight.Bold, color = p.ink)
                            Text(if (water >= s.waterGoal) "Goal reached today" else "glasses today", fontSize = sc.body, color = if (water >= s.waterGoal) p.ok else p.inkSoft)
                        }
                        BigButton("Add a glass", tone = Tone.TINT, icon = Icons.Rounded.Add, height = 52.dp, onClick = {
                            scope.launch {
                                val id = app.repo.addWater(1); water = app.repo.waterToday(); savedFeedback(ctx)
                                UndoHost.show("Added a glass.") { scope.launch { app.repo.remove(listOf(id)); water = app.repo.waterToday() } }
                            }
                        })
                        if (water > 0) BigButton("Remove one", tone = Tone.OUTLINE, height = 48.dp, onClick = {
                            scope.launch {
                                val last = app.db.notes().kindSince(Kind.WATER, start).maxByOrNull { it.occurredAt } ?: return@launch
                                app.repo.remove(listOf(last.id)); water = app.repo.waterToday()
                                UndoHost.show("Removed a glass.") { scope.launch { app.repo.restore(listOf(last.id)); water = app.repo.waterToday() } }
                            }
                        })
                    }
                }
            }
            1 -> {
                val kcalToday = todayFood.sumOf { n -> runCatching { org.json.JSONObject(n.details ?: "").optDouble("kcal") }.getOrNull()?.takeIf { !it.isNaN() } ?: 0.0 }
                val proteinToday = todayFood.sumOf { n -> runCatching { org.json.JSONObject(n.details ?: "").optDouble("protein") }.getOrNull()?.takeIf { !it.isNaN() } ?: 0.0 }
                com.suryaprakash.medlog.ui.SectionHeader("Today", if (todayFood.isEmpty()) "Nothing yet" else "${kcalToday.toInt()} kcal · ${proteinToday.toInt()} g protein", "My health") { nav.go(Route.Reports) }
                BigButton("Add food", icon = Icons.Rounded.Add, onClick = { nav.go(Route.FoodPick()) })
                todayFood.forEach { n -> MealCard(n, onChange = { nav.go(Route.FoodPick(n.id)) }, onDelete = {
                    scope.launch { app.repo.remove(listOf(n.id)) }
                    UndoHost.show("Meal deleted.") { scope.launch { app.repo.restore(listOf(n.id)) } }
                }) }
            }
            else -> {
                val feedDoses = doses.filter { d -> feeds.any { it.id == d.medicineId } }
                com.suryaprakash.medlog.ui.SectionHeader("Today's feeds",
                    if (feedDoses.isEmpty()) "None set up" else "${feedDoses.count { it.status == com.suryaprakash.medlog.data.DoseStatus.TAKEN }} of ${feedDoses.size} given",
                    if (feeds.isNotEmpty()) "Add feed" else null, Icons.Rounded.Add) { feedSheet = true }
                if (feeds.isEmpty()) com.suryaprakash.medlog.ui.DashedAddCard("Set up a feed") { feedSheet = true }
                feedDoses.forEach { d ->
                    val m = feeds.firstOrNull { it.id == d.medicineId } ?: return@forEach
                    DoseCard(d, m, onOpen = { feedMenu = m },
                        onTaken = { scope.launch { com.suryaprakash.medlog.meds.Scheduler.take(ctx, d.id, db = app.db); savedFeedback(ctx) } },
                        onUndo = { scope.launch { com.suryaprakash.medlog.meds.Scheduler.untake(ctx, d.id, db = app.db) } },
                        onNotGiven = { scope.launch { com.suryaprakash.medlog.meds.Scheduler.skip(ctx, d.id, "Not given", db = app.db) } })
                }
            }
        }
    }
    if (goalSheet) WaterGoalSheet(s.waterGoal, onDone = { g -> app.settings.update { it.copy(waterGoal = g) }; goalSheet = false }, onDismiss = { goalSheet = false })
    feedMenu?.let { m ->
        FeedMenu(m.name, onDelete = {
            feedMenu = null
            scope.launch {
                app.db.medicines().update(m.copy(active = false, changedAt = System.currentTimeMillis(), changeNote = "stopped"))
                app.db.doses().dropFuture(m.id, System.currentTimeMillis())
                com.suryaprakash.medlog.meds.Scheduler.stopMedicine(ctx, m.copy(active = false), app.db)
            }
        }, onDismiss = { feedMenu = null })
    }
    LaunchedEffect(feedSheet) { if (feedSheet) { feedSheet = false; nav.go(Route.FeedNew) } }
}

/** A food MedLog doesn't know yet: one serving's calories and protein, kept for next time. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CustomFoodSheet(name: String, onDone: (com.suryaprakash.medlog.nutrition.Foods.Food) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(0) }
    val units = listOf("katori", "piece", "glass", "plate")
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.paper,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Add ${name.replaceFirstChar(Char::uppercase)}", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            Hint("For one serving. The packet, or a dietitian, can tell you.")
            com.suryaprakash.medlog.ui.Segmented(listOf("Katori", "Piece", "Glass", "Plate"), unit) { unit = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigField("Calories", kcal, { kcal = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
                BigField("Protein (g)", protein, { protein = it.filter { c -> c.isDigit() || c == '.' }.take(4) }, Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Decimal)
            }
            BigButton("Save ${name.replaceFirstChar(Char::uppercase)}", enabled = kcal.isNotBlank(), onClick = {
                onDone(com.suryaprakash.medlog.nutrition.Foods.Food(listOf(name.trim()), units[unit], 150, kcal.toDouble(), protein.toDoubleOrNull() ?: 0.0, custom = true))
            })
        }
    }
}

/** One meal logged: the main dish with its picture and amount, each side with its amount, and the energy in its own box. Tap to change or delete. */
@Composable
private fun MealCard(n: com.suryaprakash.medlog.data.Note, onChange: () -> Unit, onDelete: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var menu by remember { mutableStateOf(false) }
    val o = runCatching { org.json.JSONObject(n.details ?: "") }.getOrNull()
    val items = o?.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
    fun isSide(x: org.json.JSONObject) = com.suryaprakash.medlog.nutrition.Foods.all.firstOrNull { it.name == x.optString("name") }?.side == true
    val main = items.firstOrNull { !isSide(it) } ?: items.firstOrNull()
    val title = main?.optString("name")?.replaceFirstChar(Char::uppercase) ?: (n.transcript?.ifBlank { null } ?: "Photo of a meal")
    val sides = items.filter { it !== main }
    val kcal = o?.optInt("kcal", -1) ?: -1
    val protein = o?.optDouble("protein")?.takeIf { !it.isNaN() }
    val sh = RoundedCornerShape(sc.radius)
    Column(Modifier.fillMaxWidth().clip(sh).background(p.card).border(1.dp, p.line, sh).steady("$title. Tap to change or delete") { menu = true }.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FoodPicture(main?.optString("name")?.let { nm -> com.suryaprakash.medlog.nutrition.Foods.all.firstOrNull { it.name == nm } }, 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = sc.cardTitle, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(listOfNotNull(main?.optString("amount")?.ifBlank { null }, timeLabel(n.occurredAt)).joinToString(" · "), fontSize = sc.small, color = p.inkSoft)
            }
            if (kcal >= 0) {
                Spacer(Modifier.width(12.dp))
                Column(Modifier.clip(RoundedCornerShape(14.dp)).background(p.fill).padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$kcal", fontSize = sc.cardTitle, fontWeight = FontWeight.Bold, color = p.ink)
                    Text("kcal", fontSize = sc.small * 0.88f, color = p.inkSoft)
                    protein?.let { Text("${it.toInt()} g protein", fontSize = sc.small * 0.8f, color = p.inkSoft) }
                }
            }
        }
        if (sides.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            sides.forEach { x ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(x.optString("name").replaceFirstChar(Char::uppercase), fontSize = sc.body, color = p.ink, modifier = Modifier.weight(1f))
                    Text(x.optString("amount"), fontSize = sc.small, color = p.inkSoft)
                }
            }
        }
    }
    if (menu) MealMenu(title, onChange = { menu = false; onChange() }, onDelete = { menu = false; onDelete() }, onDismiss = { menu = false })
}

/** Change or delete a logged meal. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MealMenu(title: String, onChange: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.paper,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            com.suryaprakash.medlog.ui.SectionHeader(title, "Change it, or delete it", null)
            BigButton("Change meal", icon = Icons.Rounded.Edit, onClick = onChange)
            BigButton("Delete meal", tone = Tone.OUTLINE, icon = Icons.Rounded.Delete, onClick = onDelete)
        }
    }
}

/** Stop a feed: its reminders end; what was given stays in the record. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FeedMenu(name: String, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.paper,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            com.suryaprakash.medlog.ui.SectionHeader(name, "Past feeds stay in the record", null)
            BigButton("Stop this feed", tone = Tone.OUTLINE, icon = Icons.Rounded.Delete, onClick = onDelete)
        }
    }
}

/**
 * A new feed, defined once, on its own page with room to breathe: its name, what goes in (one card per item, added one
 * at a time), how much and how often, and how it's given. Saved, it rings like a medicine.
 */
@Composable
fun FeedNewScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    val parts = remember { androidx.compose.runtime.mutableStateListOf<com.suryaprakash.medlog.nutrition.Feeds.Part>() }
    var ml by remember { mutableStateOf(200) }
    var perDay by remember { mutableStateOf(4) }
    var tube by remember { mutableStateOf(0) }
    var addingPart by remember { mutableStateOf(false) }
    val times = (0 until perDay).map { i -> val h = if (perDay == 1) 8 else 7 + (14 * i) / (perDay - 1); "%02d:00".format(h) }
    val ok = name.isNotBlank() && parts.isNotEmpty()
    Screen("New feed", "Name the feed, add what goes in, then how much and how often.", onHome = { nav.home() }, onBack = { nav.back() },
        subtitle = "Each feed: ${parts.sumOf { it.kcal }.toInt()} kcal · ${parts.sumOf { it.protein }.toInt()} g protein",
        actions = {
            BigButton("Save feed", enabled = ok, onClick = {
                scope.launch {
                    val id = app.db.medicines().insert(com.suryaprakash.medlog.data.Medicine(name = name.trim(), form = "feed", amount = "$ml ml", times = times.joinToString(","),
                        purpose = if (tube == 1) "Feed by tube" else "Feed by mouth", critical = tube == 1))
                    app.settings.putString("feed_info", com.suryaprakash.medlog.nutrition.Feeds.infoWith(app.settings.getString("feed_info"), id,
                        com.suryaprakash.medlog.nutrition.Feeds.Info(parts.toList(), tube == 1)))
                    com.suryaprakash.medlog.meds.Scheduler.reschedule(ctx)
                    nav.back()
                }
            })
        }) {
        com.suryaprakash.medlog.ui.SectionHeader("How it's given", if (tube == 1) "Missed feeds alert helpers" else "Taken by mouth", null)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple("By mouth", Icons.Rounded.LocalDrink, p.tintBlue), Triple("By tube", Icons.Rounded.Medication, p.tintPurple)).forEachIndexed { i, (label, icon, tint) ->
                val on = tube == i
                val sh = RoundedCornerShape(sc.radius)
                Column(Modifier.weight(1f).clip(sh).background(if (on) tint.copy(alpha = 0.12f) else p.card).border(if (on) 2.5.dp else 1.dp, if (on) tint else p.line, sh)
                    .steady(label + if (on) ", chosen" else "") { tube = i }.padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    com.suryaprakash.medlog.ui.OptionIcon(icon, tint, 56.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(label, fontSize = sc.body, fontWeight = FontWeight.Bold, color = if (on) tint else p.ink)
                }
            }
        }

        com.suryaprakash.medlog.ui.SectionHeader("Name", "What you call this feed", null)
        BigField("Feed name", name, { name = it }, hint = "For example: Morning feed")

        com.suryaprakash.medlog.ui.SectionHeader("What goes in", if (parts.isEmpty()) "Add each item" else "${parts.size} item${if (parts.size == 1) "" else "s"}",
            if (parts.isNotEmpty()) "Add" else null, Icons.Rounded.Add) { addingPart = true }
        parts.forEachIndexed { i, part ->
            val sh = RoundedCornerShape(sc.radius)
            Row(Modifier.fillMaxWidth().clip(sh).background(p.card).border(1.dp, p.line, sh).padding(start = 18.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(part.name.replaceFirstChar(Char::uppercase), fontSize = sc.cardTitle, fontWeight = FontWeight.Bold, color = p.ink)
                    Text(listOf(part.amount, "${part.kcal.toInt()} kcal", "${com.suryaprakash.medlog.nlu.fmt1(part.protein)} g protein").filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = sc.small, color = p.inkSoft)
                }
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).steady("Remove ${part.name}") { parts.removeAt(i) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, null, tint = p.inkSoft, modifier = Modifier.size(22.dp))
                }
            }
        }
        if (parts.isEmpty()) com.suryaprakash.medlog.ui.DashedAddCard("Add an item") { addingPart = true }

        com.suryaprakash.medlog.ui.SectionHeader("How much, how often", times.joinToString(", ") { timeLabelOf(it) }, null)
        com.suryaprakash.medlog.ui.Group {
            Box(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) { com.suryaprakash.medlog.ui.CounterLine("Each feed", "Millilitres", "$ml ml", ml > 50, ml < 600, { ml -= 50 }, { ml += 50 }) }
            com.suryaprakash.medlog.ui.GroupLine()
            Box(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) { com.suryaprakash.medlog.ui.CounterLine("Feeds a day", "A reminder each time", "$perDay", perDay > 1, perDay < 8, { perDay-- }, { perDay++ }) }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (addingPart) FeedPartSheet(onDone = { parts.add(it); addingPart = false }, onDismiss = { addingPart = false })
}

/** One thing that goes into a feed, one box per line. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FeedPartSheet(onDone: (com.suryaprakash.medlog.nutrition.Feeds.Part) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.paper,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            com.suryaprakash.medlog.ui.SectionHeader("Add an item", "Per feed", null)
            BigField("Item", name, { name = it }, hint = "For example: Ensure, milk, protein powder")
            BigField("Amount", amount, { amount = it }, hint = "For example: 2 scoops, 150 ml")
            BigField("Calories (kcal)", kcal, { kcal = it.filter(Char::isDigit).take(4) }, keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
            BigField("Protein (g)", protein, { protein = it.filter { c -> c.isDigit() || c == '.' }.take(4) }, keyboard = androidx.compose.ui.text.input.KeyboardType.Decimal)
            BigButton("Add item", enabled = name.isNotBlank() && kcal.isNotBlank(), onClick = {
                onDone(com.suryaprakash.medlog.nutrition.Feeds.Part(name.trim(), amount.trim(), kcal.toDouble(), protein.toDoubleOrNull() ?: 0.0))
            })
        }
    }
}

private fun timeLabelOf(t: String): String { val h = t.substringBefore(":").toInt(); return "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "am" else "pm"}" }

/** A tall glass split into one segment per glass in the day's goal, filled from the bottom as they're drunk. */
@Composable
private fun WaterGlass(drunk: Int, goal: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val water = p.tintBlue
    androidx.compose.foundation.Canvas(modifier.semantics { contentDescription = "$drunk of $goal glasses" }) {
        val w = size.width; val h = size.height
        val inset = w * 0.12f                      // the glass narrows towards the bottom
        fun left(y: Float) = inset * (y / h)
        fun right(y: Float) = w - inset * (y / h)
        val glass = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w - inset, h); lineTo(inset, h); close()
        }
        val n = goal.coerceAtLeast(1)
        val seg = h / n
        val gap = 3.dp.toPx()
        clipPath(glass) {
            // empty glass
            drawRect(water.copy(alpha = 0.08f))
            // one segment per glass, the lowest first
            for (i in 0 until n) {
                val bottom = h - i * seg
                val top = bottom - seg + gap
                val seg4 = androidx.compose.ui.graphics.Path().apply {
                    moveTo(left(top), top); lineTo(right(top), top); lineTo(right(bottom), bottom); lineTo(left(bottom), bottom); close()
                }
                drawPath(seg4, if (i < drunk) water else water.copy(alpha = 0.14f))
            }
        }
        drawPath(glass, p.inkSoft.copy(alpha = 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(),
            join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** The daily water goal, on a counter. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WaterGoalSheet(start: Int, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var g by remember { mutableStateOf(start) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.paper,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Glasses of water a day", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            com.suryaprakash.medlog.ui.CounterLine("Daily goal", "Ask your doctor if you should drink less", "$g", g > 1, g < 16, { g-- }, { g++ })
            BigButton("Done", onClick = { onDone(g) })
        }
    }
}

/** BP, sugar, oxygen, temperature, weight, pulse: said or typed, checked against danger signs. */
@Composable
fun ReadingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf<String?>(null) }
    var v1 by remember { mutableStateOf("") }
    var v2 by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Triage?>(null) }
    var listening by remember { mutableStateOf(false) }
    val recent by app.db.notes().kindSinceFlow(Kind.READING, System.currentTimeMillis() - 14 * 24 * 3600_000L).collectAsState(emptyList())

    result?.let { t ->
        if (t.level == Level.RED) { DangerScreen(nav, t) { result = null }; return }
    }

    fun save(r: Reading) {
        scope.launch {
            app.repo.addReading(r, null)
            // a plain reading has no problem: naming one (e.g. low BP) made the reason say "with dizziness" that nobody said (B21)
            val problem = if (r.type == "temp" && r.v1 >= 100.4) "fever" else null
            val t = DangerRules.evaluate(problem, emptyMap(), listOf(r), emptyList(), app.repo.person())
            savedFeedback(ctx)
            if (t.level == Level.RED && !app.viewing.active) Alerts.dangerToHelpers(ctx, r.label(), t)
            result = t
            app.speaker.say("Saved. ${r.label()}. " + if (t.level == Level.GREEN) "" else t.say + " " + t.reasons.joinToString(". "))
            v1 = ""; v2 = ""; type = null
        }
    }

    val kinds = listOf(
        Triple("bp", "Blood pressure", Icons.Rounded.MonitorHeart to p.tintPink), Triple("sugar", "Sugar", Icons.Rounded.Bloodtype to p.tintOrange),
        Triple("weight", "Weight", Icons.Rounded.MonitorWeight to p.tintPurple), Triple("pulse", "Pulse", Icons.Rounded.Favorite to p.red),
        Triple("spo2", "Oxygen", Icons.Rounded.Air to p.tintBlue), Triple("temp", "Temperature", Icons.Rounded.Thermostat to p.tintOrange),
    )
    // the latest of each, for the tiles
    val latest = recent.mapNotNull { n -> runCatching { org.json.JSONObject(n.details) }.getOrNull()?.let { it.optString("type") to n } }
        .groupBy({ it.first }, { it.second }).mapValues { e -> e.value.maxBy { it.occurredAt } }
    Screen("BP, sugar & more", "Tap what you measured. Type the number. Weight comes from your scale.", onHome = { nav.home() }, onBack = { nav.back() },
        subtitle = "Tap what you measured") {
        result?.takeIf { it.level == Level.AMBER }?.let { t -> Card(border = p.amber) { Text("▲ " + t.say, color = p.amber, fontWeight = FontWeight.Bold, fontSize = sc.body); t.firstAid?.let { Body(it, bold = true) } }; DoctorCallButton() }
        result?.let { NeedsLimitLine(nav, it.needsLimit) }
        com.suryaprakash.medlog.ui.SectionHeader("Readings", if (recent.isEmpty()) "None in 2 weeks" else "${recent.size} in 2 weeks", "Trends") { nav.go(Route.Reports) }
        com.suryaprakash.medlog.ui.TileGrid(kinds, 2, aspect = 1.25f) { (k, label, look), mod ->
            val last = latest[k]
            val value = last?.text?.substringAfter(" ")?.ifBlank { null }
            val sh = RoundedCornerShape(sc.radius)
            Column(mod.clip(sh).background(p.card).border(1.dp, p.line, sh).steady("$label. ${value ?: "No reading"}. Tap to add") { type = k; result = null; v1 = ""; v2 = "" }.padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween) {
                com.suryaprakash.medlog.ui.OptionIcon(look.first, look.second, 40.dp)
                Column {
                    Text(value ?: "–", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = if (value == null) p.inkSoft else p.ink, maxLines = 1)
                    Text(label, fontSize = sc.small, color = p.inkSoft, maxLines = 1)
                    Text(last?.let { dayLabel(it.occurredAt) } ?: "No reading", fontSize = sc.small * 0.88f, color = p.inkSoft, maxLines = 1)
                }
            }
        }
    }
    type?.let { k -> ReadingSheet(k, kinds.first { it.first == k }.second, latest[k], onSave = { save(it) }, onDelete = { n ->
        scope.launch { app.repo.remove(listOf(n.id)) }; type = null
        UndoHost.show("Reading deleted.") { scope.launch { app.repo.restore(listOf(n.id)) } }
    }, onDismiss = { type = null }) }
}

/** Entering one reading, in a panel: two boxes for blood pressure, one for the rest, the scale for weight. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReadingSheet(type: String, label: String, last: com.suryaprakash.medlog.data.Note?, onSave: (Reading) -> Unit, onDelete: (com.suryaprakash.medlog.data.Note) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var v1 by remember { mutableStateOf("") }
    var v2 by remember { mutableStateOf("") }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.paper,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            last?.let { n ->
                com.suryaprakash.medlog.ui.Group {
                    com.suryaprakash.medlog.ui.ValueRow(n.text ?: "", "Delete", sub = "Last · ${dayLabel(n.occurredAt)} ${timeLabel(n.occurredAt)}", valueColor = p.red) { onDelete(n) }
                }
            }
            when (type) {
                "bp" -> {
                    com.suryaprakash.medlog.ui.SectionHeader(label, "Top and bottom numbers", null)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BigField("Top", v1, { v1 = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
                        BigField("Bottom", v2, { v2 = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
                    }
                    val s = v1.toDoubleOrNull(); val d = v2.toDoubleOrNull()
                    val ok = s != null && d != null && s in 60.0..260.0 && d in 30.0..160.0 && s > d
                    if (v1.isNotEmpty() && v2.isNotEmpty() && !ok) Hint("Please check the numbers.")
                    BigButton(if (ok) "Save $v1/$v2" else "Save", enabled = ok, onClick = { onSave(Reading("bp", s!!, d, "mmHg")) })
                }
                "weight" -> {
                    com.suryaprakash.medlog.ui.SectionHeader(label, "Step on your scale", null)
                    ScaleCard { kg -> onSave(Reading("weight", kg, unit = "kg")) }
                }
                else -> {
                    val (unit, range) = when (type) { "sugar" -> "mg/dL" to 20.0..600.0; "spo2" -> "%" to 50.0..100.0; "temp" -> "°F or °C" to 34.0..110.0; else -> "per minute" to 30.0..220.0 }
                    com.suryaprakash.medlog.ui.SectionHeader(label, unit, null)
                    BigField(label, v1, { v1 = it.filter { c -> c.isDigit() || (type == "temp" && c == '.') }.take(5) },
                        keyboard = if (type == "temp") androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Number)
                    val v = v1.toDoubleOrNull()
                    // 34–43 is taken as °C and saved as °F, e.g. 38.5 becomes 101.3 (B22)
                    val ok = v != null && v in range && (type != "temp" || DangerRules.toFahrenheit(v) in 93.0..110.0)
                    if (v1.isNotEmpty() && !ok) Hint("Please check the number.")
                    BigButton(if (ok) "Save $v1" else "Save", enabled = ok, onClick = {
                        if (type == "temp") onSave(Reading(type, DangerRules.toFahrenheit(v!!), unit = "°F")) else onSave(Reading(type, v!!, unit = unit))
                    })
                }
            }
        }
    }
}

/**
 * Weight from a Bluetooth scale: listens while shown, the number settles in front of you, and a steady reading
 * is saved with one tap. Typing it is always there too.
 */
@Composable
private fun ScaleCard(onSave: (Double) -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val sc = LocalScale.current
    val live by com.suryaprakash.medlog.help.Scale.live.collectAsState()
    val listening by com.suryaprakash.medlog.help.Scale.listening.collectAsState()
    var typing by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }
    val perms = if (android.os.Build.VERSION.SDK_INT >= 31) arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
    val ask = com.suryaprakash.medlog.ui.rememberPermissionAsker { ok -> if (ok) com.suryaprakash.medlog.help.Scale.start(ctx); tried = true }
    DisposableEffect(Unit) {
        if (com.suryaprakash.medlog.ui.Perms.has(ctx, *perms)) { com.suryaprakash.medlog.help.Scale.start(ctx); tried = true } else ask(perms)
        onDispose { com.suryaprakash.medlog.help.Scale.stop(ctx) }
    }
    val sh = RoundedCornerShape(sc.radius)
    Column(Modifier.fillMaxWidth().clip(sh).background(p.card).border(if (live?.steady == true) 2.dp else 1.dp, if (live?.steady == true) p.ok else p.line, sh).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.MonitorWeight, p.tintPurple, 56.dp)
        val w = live
        when {
            w != null -> {
                Text("%.1f kg".format(w.kg), fontSize = sc.title * 1.6f, fontWeight = FontWeight.Bold, color = p.ink)
                Text(if (w.steady) "Steady" else "Settling… stay still", fontSize = sc.body, color = if (w.steady) p.ok else p.inkSoft)
                if (w.steady) BigButton("Save %.1f kg".format(w.kg), onClick = { onSave(Math.round(w.kg * 10) / 10.0); com.suryaprakash.medlog.help.Scale.live.value = null })
            }
            listening -> {
                Text("Step on your scale", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
                Text("MedLog is listening for it now", fontSize = sc.body, color = p.inkSoft)
            }
            tried -> {
                Text("Turn on Bluetooth", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
                Text("Then come back here and step on the scale", fontSize = sc.body, color = p.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
    if (!typing) BigButton("Type it instead", tone = Tone.TINT, height = 52.dp, onClick = { typing = true })
    else NumberPad("kg", allowDecimal = true, range = 20.0..250.0) { v -> onSave(v) }
}
