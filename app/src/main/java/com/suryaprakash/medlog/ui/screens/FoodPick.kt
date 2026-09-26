package com.suryaprakash.medlog.ui.screens

import com.suryaprakash.medlog.ui.savedFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.nutrition.Foods
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.SectionHeader
import com.suryaprakash.medlog.ui.Text
import com.suryaprakash.medlog.ui.steady
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalTime
import kotlin.math.roundToInt


/**
 * Adding what was eaten, like a food app: search or filter by meal and cuisine, ADD a dish, set how many,
 * pick its usual sides, then Done. Past favourites sit on top for one-tap repeats.
 */
@Composable
fun FoodPickScreen(nav: Nav, noteId: Long? = null) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val scope = rememberCoroutineScope()
    var custom by remember { mutableStateOf(Foods.customFrom(app.settings.getString("custom_foods"))) }
    val basket = remember { mutableStateMapOf<String, Double>() }
    val sizes = remember { mutableStateMapOf<String, String>() }
    var query by remember { mutableStateOf("") }
    val hour = LocalTime.now().hour
    var meal by remember { mutableStateOf(when (hour) { in 5..10 -> "Breakfast"; in 11..15 -> "Lunch"; in 16..18 -> "Snacks"; else -> "Lunch" }) }
    var cuisine by remember { mutableStateOf<String?>(null) }
    var cuisineSheet by remember { mutableStateOf(false) }
    var sidesOf by remember { mutableStateOf<Foods.Food?>(null) }
    var adding by remember { mutableStateOf(false) }
    var usual by remember { mutableStateOf<List<Foods.Food>>(emptyList()) }
    val foods = custom + Foods.all
    fun find(name: String) = foods.firstOrNull { it.name == name }

    // changing a meal: start from what was logged
    LaunchedEffect(noteId) {
        if (noteId == null) return@LaunchedEffect
        val n = app.db.notes().get(noteId) ?: return@LaunchedEffect
        runCatching { JSONObject(n.details ?: "").getJSONArray("items") }.getOrNull()?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val f = find(o.optString("name")) ?: continue
                val words = o.optString("amount").trim().split(" ")
                val num = words.firstOrNull()?.replace("½", "0.5")?.toDoubleOrNull() ?: f.start
                basket[f.name] = if (f.measure == Foods.Measure.PIECE || words.getOrNull(1) in listOf("g", "ml")) num else f.start
                words.getOrNull(1)?.takeIf { s -> Foods.SIZES.any { it.first == s } }?.let { sizes[f.name] = it }
            }
        }
    }

    // what they eat most, from the last month
    LaunchedEffect(Unit) {
        val counts = mutableMapOf<String, Int>()
        app.db.notes().kindSince(Kind.FOOD, System.currentTimeMillis() - 30L * 86_400_000).forEach { n ->
            runCatching { JSONObject(n.details ?: "").getJSONArray("items") }.getOrNull()?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).optString("name").takeIf { it.isNotBlank() }?.let { counts[it] = (counts[it] ?: 0) + 1 }
            }
        }
        usual = counts.entries.sortedByDescending { it.value }.mapNotNull { find(it.key) }.filter { !it.side }.take(4)
    }

    fun add(f: Foods.Food) {
        val first = (basket[f.name] ?: 0.0) == 0.0
        basket[f.name] = if (first) f.start else (basket[f.name] ?: 0.0) + f.step
        if (first && Foods.sidesFor(f).isNotEmpty()) sidesOf = f
    }
    fun remove(f: Foods.Food) { val v = (basket[f.name] ?: 0.0) - f.step; if (v <= 0.0) basket.remove(f.name) else basket[f.name] = v }

    val chosen = basket.entries.mapNotNull { (n, q) -> find(n)?.let { Foods.Portion(it, q, sizes[n] ?: "medium") } }.sortedBy { it.food.side }
    val kcal = chosen.sumOf { it.kcal }
    val list = when {
        query.isNotBlank() -> foods.filter { f -> !f.side && f.names.any { it.contains(query.trim(), ignoreCase = true) } }.take(40)
        else -> foods.filter { !it.side } .filter { (it.meal == meal || (meal == "Lunch" && it.meal == "Lunch")) && (cuisine == null || it.cuisine == cuisine) }
    }

    val basketBar: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${chosen.size} item${if (chosen.size == 1) "" else "s"}", fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink)
                Text("${kcal.roundToInt()} kcal", fontSize = sc.small, color = p.inkSoft)
            }
            BigButton("Done", Modifier.width(160.dp), onClick = {
                val portions = chosen
                val words = portions.joinToString(", ") { "${it.food.name} ${it.words}" }
                scope.launch {
                    val id = noteId ?: app.repo.addFood(words, null)
                    app.db.notes().get(id)?.let { n -> app.db.notes().update(n.copy(transcript = words, text = "Ate: $words", details = Foods.portionsJson(portions))) }
                    savedFeedback(ctx); app.speaker.say("Saved.")
                    nav.back()
                }
            })
        }
    }
    Screen(if (noteId != null) "Change meal" else "What did you eat?", "Tap ADD on each dish, set how many, then Done.", onHome = { nav.home() }, onBack = { nav.back() }, actions = if (chosen.isEmpty()) null else basketBar) {
        com.suryaprakash.medlog.ui.SearchBox(query, { query = it }, "Search dishes")
        if (query.isBlank()) {
            // when, then where from
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(cuisine ?: "Cuisine", cuisine != null, trailing = true) { cuisineSheet = true }
                Foods.MEALS.forEach { m -> FilterChip(if (m == "Lunch") "Lunch & dinner" else m, meal == m) { meal = m } }
            }
            if (usual.isNotEmpty()) {
                SectionHeader("Eat again", "Your usual dishes", null)
                usual.forEach { f -> DishRow(f, basket[f.name] ?: 0.0, sizes[f.name] ?: "medium", { sizes[f.name] = it }, { add(f) }, { remove(f) }) }
            }
            SectionHeader(if (meal == "Lunch") "Lunch & dinner" else meal, "${list.size} dishes" + (cuisine?.let { " · $it" } ?: ""), null)
        }
        list.forEach { f -> DishRow(f, basket[f.name] ?: 0.0, sizes[f.name] ?: "medium", { sizes[f.name] = it }, { add(f) }, { remove(f) }) }
        com.suryaprakash.medlog.ui.DashedAddCard("Something else") { adding = true }
        Spacer(Modifier.height(8.dp))
    }

    sidesOf?.let { f ->
        SidesSheet(f, basket, sizes, { add(it) }, { remove(it) }) { sidesOf = null }
    }
    if (cuisineSheet) CuisineSheet(cuisine, { cuisine = it; cuisineSheet = false }) { cuisineSheet = false }
    if (adding) NewFoodSheet(query, meal, onDone = { f ->
        val json = Foods.customWith(app.settings.getString("custom_foods"), f)
        app.settings.putString("custom_foods", json); custom = Foods.customFrom(json)
        basket[f.name] = f.start; adding = false; query = ""
    }, onDismiss = { adding = false })
}

/** A filter pill; the chosen one is teal. */
@Composable
private fun FilterChip(text: String, on: Boolean, trailing: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(50)
    Row(Modifier.heightIn(min = 48.dp).clip(sh).background(if (on) p.brandSoft else p.card).border(if (on) 2.dp else 1.dp, if (on) p.brand else p.line, sh)
        .steady(text, onClick = onClick).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = if (on) p.brand else p.ink, maxLines = 1)
        if (trailing) Icon(Icons.Rounded.ExpandMore, null, tint = if (on) p.brand else p.inkSoft, modifier = Modifier.padding(start = 4.dp).size(20.dp))
    }
}

/** A dish's picture. Empty until the food pictures arrive. */
@Composable
fun FoodPicture(@Suppress("UNUSED_PARAMETER") f: Foods.Food?, size: Dp) {
    val p = LocalPalette.current
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.2f)).background(p.fill), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Restaurant, null, tint = p.inkSoft.copy(alpha = 0.25f), modifier = Modifier.size(size * 0.36f))
    }
}

/** One dish, food-app style: name and numbers on the left, the picture on the right with ADD (or − n +) sitting on it. */
@Composable
private fun DishRow(f: Foods.Food, qty: Double, size: String, onSize: (String) -> Unit, onAdd: () -> Unit, onRemove: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(sc.radius)
    val now = if (qty > 0) Foods.Portion(f, qty, size) else Foods.Portion(f, f.start)
    Column(Modifier.fillMaxWidth().clip(sh).background(p.card).border(if (qty > 0) 2.dp else 1.dp, if (qty > 0) p.brand else p.line, sh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(f.name.replaceFirstChar(Char::uppercase), fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${now.words} · ${now.kcal.roundToInt()} kcal", fontSize = sc.small, color = if (qty > 0) p.brand else p.inkSoft, fontWeight = if (qty > 0) FontWeight.SemiBold else null)
                Text("${now.protein.roundToInt()} g protein", fontSize = sc.small * 0.88f, color = p.inkSoft)
            }
            Spacer(Modifier.width(12.dp))
            Box(contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.padding(bottom = 18.dp)) { FoodPicture(f, 112.dp) }
                AddButton(qty, f, onAdd, onRemove)
            }
        }
        // counted foods: how big each one was
        if (qty > 0 && f.measure == Foods.Measure.PIECE) com.suryaprakash.medlog.ui.Segmented(Foods.SIZES.map { it.first.replaceFirstChar(Char::uppercase) },
            Foods.SIZES.indexOfFirst { it.first == size }) { onSize(Foods.SIZES[it].first) }
    }
}

/** ADD, then a counter: − 2 +. */
@Composable
private fun AddButton(qty: Double, f: Foods.Food, onAdd: () -> Unit, onRemove: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(12.dp)
    if (qty <= 0.0) {
        Box(Modifier.width(112.dp).height(44.dp).clip(sh).background(p.card).border(1.5.dp, p.brand, sh).steady("Add ${f.name}", onClick = onAdd), contentAlignment = Alignment.Center) {
            Text("ADD", fontSize = sc.small, fontWeight = FontWeight.ExtraBold, color = p.brand)
        }
    } else {
        Row(Modifier.width(112.dp).height(44.dp).offset(0.dp, 0.dp).clip(sh).background(p.brand), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(34.dp).height(44.dp).steady("One less ${f.name}", onClick = onRemove), contentAlignment = Alignment.Center) {
                Text("−", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.onBrand)
            }
            Text(when (f.measure) { Foods.Measure.PIECE -> Foods.fmtQty(qty); Foods.Measure.GRAMS -> "${qty.toInt()} g"; Foods.Measure.ML -> "${qty.toInt()} ml" },
                fontSize = sc.small, fontWeight = FontWeight.Bold, color = p.onBrand, maxLines = 1)
            Box(Modifier.width(34.dp).height(44.dp).steady("One more ${f.name}", onClick = onAdd), contentAlignment = Alignment.Center) {
                Text("+", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.onBrand)
            }
        }
    }
}

/** What usually comes with the dish just added. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SidesSheet(f: Foods.Food, basket: Map<String, Double>, sizes: MutableMap<String, String>, onAdd: (Foods.Food) -> Unit, onRemove: (Foods.Food) -> Unit, onDone: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDone, containerColor = p.paper,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("With your ${f.name}?", "Add what you had with it", null)
            Foods.sidesFor(f).forEach { s -> DishRow(s, basket[s.name] ?: 0.0, sizes[s.name] ?: "medium", { sizes[s.name] = it }, { onAdd(s) }, { onRemove(s) }) }
            BigButton("Done", onClick = onDone)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CuisineSheet(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.paper,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("Cuisine", "Pick one", null)
            com.suryaprakash.medlog.ui.Choice("All cuisines", current == null) { onPick(null) }
            Foods.CUISINES.forEach { c -> com.suryaprakash.medlog.ui.Choice(c, current == c) { onPick(c) } }
        }
    }
}

/** A dish that isn't in the list: its name, one serving's calories and protein. Kept for next time. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NewFoodSheet(start: String, meal: String, onDone: (Foods.Food) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var name by remember { mutableStateOf(start) }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(0) }
    val units = listOf("katori", "piece", "glass")
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.paper,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeader("Something else", "One serving", null)
            BigField("Dish", name, { name = it })
            com.suryaprakash.medlog.ui.Segmented(listOf("By weight", "By piece", "Liquid"), unit) { unit = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigField(when (unit) { 0 -> "kcal per 100 g"; 1 -> "kcal per piece"; else -> "kcal per 100 ml" }, kcal, { kcal = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
                BigField("Protein (g)", protein, { protein = it.filter { c -> c.isDigit() || c == '.' }.take(4) }, Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Decimal)
            }
            BigButton("Add it", enabled = name.isNotBlank() && kcal.isNotBlank(), onClick = {
                onDone(Foods.Food(listOf(name.trim().lowercase()), units[unit], 100, kcal.toDouble(), protein.toDoubleOrNull() ?: 0.0, custom = true, meal = meal, cuisine = "My foods"))
            })
        }
    }
}
