package com.suryaprakash.medlog.nutrition

import android.content.Context
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.medlog
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What went in against what the body needs, next to weight: built for a doctor to read at a glance.
 * The top is one line and three numbers; everything after it is the detail behind them.
 */
object Nutrition {
    /** Starting targets for older adults who are unwell (about 30 kcal and 1 g protein per kg a day), until a doctor sets their own. */
    const val KCAL_PER_KG = 30.0
    const val PROTEIN_PER_KG = 1.0

    data class Day(val date: LocalDate, val kcal: Double, val protein: Double, val water: Int, val items: List<String>, val logged: Boolean)
    data class Finding(val text: String, val level: String)   // RED, AMBER, GREEN
    data class Missed(val at: Long, val feed: String, val ml: Double)
    data class Report(
        val days: List<Day>,
        val kcalTarget: Double?, val proteinTarget: Double?, val targetsFromDoctor: Boolean,
        val avgKcal: Double, val avgProtein: Double,
        val weights: List<Pair<Long, Double>>, val weightChange: Double?, val weightDays: Int,
        val headline: Finding, val findings: List<Finding>,
        val missed: List<Missed>, val changes: List<String>, val observed: List<String>,
        val feeds: List<String>, val loggedDays: Int,
    ) {
        val kcalPct get() = kcalTarget?.let { (100 * avgKcal / it).roundToInt() }
        val proteinPct get() = proteinTarget?.let { (100 * avgProtein / it).roundToInt() }
    }

    /**
     * One time, on the person's phone: a feed's contents used to be kept in the phone's settings under the LOCAL medicine id. They move to
     * the medicine row (Medicine.feedInfo), which is shared with the family. A helper phone only forgets the old key (its entries were
     * keyed by ids of replicas and can't be told apart from its own).
     */
    suspend fun migrateFeedInfo(app: com.suryaprakash.medlog.MedLogApp) {
        val json = app.settings.getString("feed_info") ?: return
        val all = runCatching { JSONObject(json) }.getOrNull()
        if (all != null && app.settings.value.role != "helper") for (m in app.ownDb.medicines().all()) {
            val o = all.optJSONObject("${m.id}") ?: continue
            if (m.feedInfo.isEmpty() && Feeds.infoOf(o.toString()) != null) app.ownRepo.updateMedicine(m.id) { it.copy(feedInfo = o.toString()) }
        }
        app.settings.putString("feed_info", null)
    }

    /** The doctor's targets of the person whose data is shown (they are in the shared profile). */
    suspend fun targetKcal(ctx: Context) = ctx.medlog.repo.profile().kcalTarget
    suspend fun targetProtein(ctx: Context) = ctx.medlog.repo.profile().proteinTarget

    private val dfmt = SimpleDateFormat("d MMMM", Locale.getDefault())
    private fun d(t: Long) = dfmt.format(Date(t))

    suspend fun build(ctx: Context, days: Int, now: Long = System.currentTimeMillis()): Report {
        val app = ctx.medlog
        val zone = ZoneId.systemDefault()
        fun day(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val start = today.minusDays(days - 1L)
        val from = start.atStartOfDay(zone).toInstant().toEpochMilli()

        // what was eaten
        val foods = app.db.notes().kindSince(Kind.FOOD, from)
        val perDay = mutableMapOf<LocalDate, Triple<Double, Double, MutableList<String>>>()
        fun add(dd: LocalDate, k: Double, pr: Double, what: String) {
            val cur = perDay[dd] ?: Triple(0.0, 0.0, mutableListOf())
            cur.third.add(what)
            perDay[dd] = Triple(cur.first + k, cur.second + pr, cur.third)
        }
        foods.forEach { n ->
            val o = runCatching { JSONObject(n.details ?: "") }.getOrNull()
            add(day(n.occurredAt), o?.optDouble("kcal")?.takeIf { !it.isNaN() } ?: 0.0, o?.optDouble("protein")?.takeIf { !it.isNaN() } ?: 0.0,
                n.transcript?.ifBlank { null } ?: "Photo of a meal")
        }

        // feeds: scheduled like medicines, counted when given
        val meds = app.db.medicines().all().filter { it.form == "feed" }.associateBy { it.id }
        val doses = app.db.doses().between(from, now).filter { it.medicineId in meds }
        val missed = mutableListOf<Missed>()
        doses.forEach { dz ->
            val m = meds[dz.medicineId] ?: return@forEach
            val ml = Feeds.ml(m.amount)
            val info = Feeds.infoOf(m.feedInfo)
            when {
                dz.status == DoseStatus.TAKEN -> add(day(dz.actedAt ?: dz.scheduledAt), info?.kcal ?: 0.0, info?.protein ?: 0.0, "${m.name} ${ml.roundToInt()} ml")
                dz.status == DoseStatus.MISSED || dz.status == DoseStatus.SKIPPED || dz.scheduledAt < now - 2 * 3600_000L -> missed.add(Missed(dz.scheduledAt, m.name, ml))
            }
        }
        val water = app.db.notes().kindSince(Kind.WATER, from).groupBy { day(it.occurredAt) }.mapValues { e -> e.value.sumOf { it.count ?: 1 } }

        val dayList = (0 until days).map { k ->
            val dd = start.plusDays(k.toLong())
            val v = perDay[dd]
            Day(dd, v?.first ?: 0.0, v?.second ?: 0.0, water[dd] ?: 0, v?.third.orEmpty(), v != null)
        }
        val logged = dayList.filter { it.logged && it.date != today }.ifEmpty { dayList.filter { it.logged } }
        val avgKcal = if (logged.isEmpty()) 0.0 else logged.sumOf { it.kcal } / logged.size
        val avgProtein = if (logged.isEmpty()) 0.0 else logged.sumOf { it.protein } / logged.size

        // weight over the period, or the last 30 days if the period is short
        val wFrom = minOf(from, now - 30 * DAY)
        val weights = app.db.notes().kindSince(Kind.READING, wFrom).mapNotNull { n ->
            runCatching { JSONObject(n.details) }.getOrNull()?.takeIf { it.optString("type") == "weight" }?.let { n.occurredAt to it.getDouble("v1") }
        }.sortedBy { it.first }
        val weightChange = if (weights.size >= 2) weights.last().second - weights.first().second else null
        val weightDays = if (weights.size >= 2) ((weights.last().first - weights.first().first) / DAY).toInt() else 0
        val kg = weights.lastOrNull()?.second

        // targets: the doctor's, or worked out from weight
        val docK = targetKcal(ctx); val docP = targetProtein(ctx)
        val kTarget = docK ?: kg?.let { it * KCAL_PER_KG }
        val pTarget = docP ?: kg?.let { it * PROTEIN_PER_KG }

        // what the numbers say, worst first
        val findings = mutableListOf<Finding>()
        if (logged.isEmpty()) findings.add(Finding("No food or feeds logged in this time", "AMBER"))
        kTarget?.takeIf { logged.isNotEmpty() }?.let { t ->
            val pct = 100 * avgKcal / t
            when {
                pct < 60 -> findings.add(Finding("Eating far too little: ${avgKcal.roundToInt()} of ${t.roundToInt()} kcal a day (${pct.roundToInt()}%)", "RED"))
                pct < 85 -> findings.add(Finding("Eating less than needed: ${avgKcal.roundToInt()} of ${t.roundToInt()} kcal a day (${pct.roundToInt()}%)", "AMBER"))
                else -> findings.add(Finding("Calories meet the target: ${avgKcal.roundToInt()} of ${t.roundToInt()} kcal a day", "GREEN"))
            }
        }
        pTarget?.takeIf { logged.isNotEmpty() }?.let { t ->
            val pct = 100 * avgProtein / t
            if (pct < 80) findings.add(Finding("Protein low: ${avgProtein.roundToInt()} of ${t.roundToInt()} g a day (${pct.roundToInt()}%)", if (pct < 60) "RED" else "AMBER"))
            else findings.add(Finding("Protein enough: ${avgProtein.roundToInt()} of ${t.roundToInt()} g a day", "GREEN"))
        }
        weightChange?.let { ch ->
            val pct = 100 * ch / weights.first().second
            val words = "${if (ch < 0) "down" else "up"} ${"%.1f".format(abs(ch))} kg in $weightDays days"
            when {
                pct <= -5 -> findings.add(Finding("Weight $words (${"%.1f".format(abs(pct))}%)", "RED"))
                pct <= -2 -> findings.add(Finding("Weight $words (${"%.1f".format(abs(pct))}%)", "AMBER"))
                else -> findings.add(Finding("Weight steady: $words", "GREEN"))
            }
        } ?: findings.add(Finding(if (weights.isEmpty()) "No weight recorded" else "Only one weight recorded", "AMBER"))
        if (missed.isNotEmpty()) findings.add(Finding("${missed.size} feed${if (missed.size == 1) "" else "s"} missed", if (missed.size >= 3) "RED" else "AMBER"))
        val gaps = dayList.count { !it.logged && it.date != today }
        if (gaps > 0 && logged.isNotEmpty()) findings.add(Finding("Nothing logged on $gaps day${if (gaps == 1) "" else "s"}", "AMBER"))
        val order = mapOf("RED" to 0, "AMBER" to 1, "GREEN" to 2)
        val sorted = findings.sortedBy { order[it.level] }

        // what else was noticed that bears on eating
        val watch = mapOf("nausea" to "Nausea", "vomiting" to "Vomiting", "no_appetite" to "No appetite", "diarrhea" to "Loose stools", "diarrhoea" to "Loose stools",
            "constipation" to "Constipation", "swallowing" to "Trouble swallowing", "choking" to "Choking", "weight_loss" to "Losing weight")
        val observed = app.db.notes().symptomsSince(from).filter { it.problemId in watch }.groupBy { watch[it.problemId]!! }
            .map { (k, v) -> "$k: ${v.size} time${if (v.size == 1) "" else "s"}, last ${d(v.maxOf { it.occurredAt })}" }

        // what changed in the feeds
        val changes = meds.values.filter { it.changedAt >= from }.map { m ->
            if (m.startDate >= from) "Started ${m.name}, ${m.amount} ${m.times.split(",").count { it.isNotBlank() }} times a day, ${d(m.startDate)}"
            else "${m.name}: ${m.changeNote.ifBlank { "changed" }}, ${d(m.changedAt)}"
        }
        val feeds = meds.values.filter { it.active }.map { m ->
            val info = Feeds.infoOf(m.feedInfo)
            "${m.name}, ${m.amount} × ${m.times.split(",").count { it.isNotBlank() }} a day" + if (info?.tube == true) " by tube" else " by mouth"
        }

        val headline = sorted.firstOrNull() ?: Finding("Nothing logged yet", "AMBER")
        return Report(dayList, kTarget, pTarget, docK != null, avgKcal, avgProtein, weights, weightChange, weightDays, headline, sorted, missed, changes, observed, feeds, logged.size)
    }
}
