package com.suryaprakash.medlog.pictogram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.LruCache
import com.suryaprakash.medlog.medlog
import org.json.JSONObject

/**
 * The symptom icons: three sprite sheets in assets/sprites (top, mid, low body), 70 icons in all.
 * Each icon is decoded on its own from its sheet region, so memory stays small.
 * Less common problems use the closest common icon ([LIKE]).
 */
object Sprites {
    data class Cell(val sheet: String, val index: Int)

    /** The 70 choices shown in the picture list, in order, grouped by body area. */
    val SECTIONS: List<Pair<String, List<String>>> by lazy { order.map { (sheet, ids) -> TITLES.getValue(sheet) to ids } }
    private val TITLES = mapOf("top" to "Head, face and mind", "mid" to "Chest, stomach, back and arms", "low" to "Toilet, legs and feet")

    private var cells: Map<String, Cell> = emptyMap()
    private var order: List<Pair<String, List<String>>> = emptyList()
    private var meta: Map<String, Pair<Int, Int>> = emptyMap()   // sheet -> cols, cell px
    private val decoders = HashMap<String, BitmapRegionDecoder>()
    private val cache = LruCache<String, Bitmap>(12 * 1024 * 1024).also { }
    private lateinit var app: Context

    @Synchronized
    fun init(ctx: Context) {
        if (cells.isNotEmpty()) return
        app = ctx.applicationContext
        val o = JSONObject(app.assets.open("sprites/sprites.json").bufferedReader().use { it.readText() })
        val c = HashMap<String, Cell>()
        val ord = ArrayList<Pair<String, List<String>>>()
        val m = HashMap<String, Pair<Int, Int>>()
        // "extra" holds a picture of its own for every less common problem; only the first three make the main list
        for (sheet in listOf("top", "mid", "low", "extra")) {
            val s = o.optJSONObject(sheet) ?: continue
            val ids = s.getJSONArray("ids").let { a -> (0 until a.length()).map { a.getString(it) } }
            ids.forEachIndexed { i, id -> c[id] = Cell(sheet, i) }
            if (sheet != "extra") ord += sheet to ids.filter { id -> listOf("depth_", "feel_", "face_", "burn_", "size_").none { id.startsWith(it) } }
            m[sheet] = s.getInt("cols") to s.getInt("cell")
        }
        cells = c; order = ord; meta = m
    }

    fun has(id: String) = cells.containsKey(id)
    fun keyFor(problemId: String?): String = when {
        problemId == null -> "confusion"
        cells.containsKey(problemId) -> problemId
        else -> LIKE[problemId] ?: "confusion"
    }

    /** The icon for a problem, at roughly [px] pixels square. */
    fun bitmap(ctx: Context, problemId: String?, px: Int): Bitmap? {
        init(ctx)
        val key = keyFor(problemId)
        val cell = cells[key] ?: return null
        val (cols, size) = meta.getValue(cell.sheet)
        val sample = when { px * 4 <= size -> 4; px * 2 <= size -> 2; else -> 1 }
        val ck = "$key@$sample"
        cache.get(ck)?.let { return it }
        val dec = synchronized(decoders) {
            decoders.getOrPut(cell.sheet) {
                app.assets.open("sprites/${cell.sheet}.png").use {
                    if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(it) else @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(it, false)
                }!!
            }
        }
        val x = (cell.index % cols) * size
        val y = (cell.index / cols) * size
        val bmp = synchronized(dec) { dec.decodeRegion(Rect(x, y, x + size, y + size), BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        cache.put(ck, bmp)
        return bmp
    }

    /** The soft background colour for a problem's picture, by body area (null for things that aren't problems). */
    fun areaColor(ctx: Context, problemId: String?): Int? {
        val group = problemId?.let { ctx.medlog.catalogue.problem(it)?.group } ?: return null
        return when (group) {
            "head" -> 0xFFDCEBFF; "mind" -> 0xFFEDE4FF; "chest" -> 0xFFFFE1E1; "tummy" -> 0xFFFFE9D6
            "toilet" -> 0xFFFFF4CC; "pain" -> 0xFFFFE3DA; "injury" -> 0xFFFFEFD5; "skin" -> 0xFFFDE7F0
            "women" -> 0xFFFCE4EC; "daily" -> 0xFFE2F5E7; else -> 0xFFD9F2EF
        }.toInt()
    }

    /** The picture already on its coloured square (for the widget, which can't layer views). */
    fun onSquare(ctx: Context, problemId: String?, px: Int): Bitmap? {
        val color = areaColor(ctx, problemId) ?: return bitmap(ctx, problemId, px)
        val inner = (px * 0.7f).toInt()
        val pic = bitmap(ctx, problemId, inner) ?: return null
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawRoundRect(android.graphics.RectF(0f, 0f, px.toFloat(), px.toFloat()), px * 0.28f, px * 0.28f, paint)
        c.drawBitmap(pic, null, android.graphics.RectF((px - inner) / 2f, (px - inner) / 2f, (px + inner) / 2f, (px + inner) / 2f), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Closest common icon for the other 75 problems. */
    val LIKE = mapOf(
        "chills" to "chills", "unwell" to "nausea", "weight_loss" to "no_appetite", "night_sweats" to "fever", "thirsty" to "dry_mouth",
        "dehydrated" to "dry_mouth", "migraine" to "headache", "fits" to "fainted", "face_droop" to "confusion", "speech_trouble" to "confusion",
        "one_side_weak" to "weakness", "tingling" to "numbness", "balance" to "near_fall", "ear_discharge" to "earache", "ringing_ears" to "hearing_loss",
        "hoarse" to "sore_throat", "sinus" to "blocked_nose", "no_smell" to "blocked_nose", "watery_eye" to "red_eye", "vision_loss" to "blurred_vision",
        "double_vision" to "blurred_vision", "dry_eyes" to "red_eye", "floaters" to "blurred_vision", "itchy_eyes" to "red_eye", "bleeding_gums" to "toothache",
        "denture_pain" to "toothache", "chest_tight" to "chest_pain", "wheeze" to "breathless", "cough_blood" to "cough", "low_oxygen" to "breathless",
        "snoring" to "cant_sleep", "low_bp" to "high_bp", "vomit_blood" to "vomiting", "black_stool" to "blood_stool", "burp" to "acidity",
        "hiccup" to "acidity", "jaundice" to "red_eye", "no_urine" to "frequent_urine", "kidney_pain" to "back_pain", "face_swelling_morning" to "sneeze",
        "morning_stiffness" to "back_pain", "gout" to "foot_pain", "body_ache" to "weakness", "hives" to "rash", "allergic_reaction" to "rash",
        "blisters" to "burn", "sunburn" to "burn", "wound_not_healing" to "cut", "bed_sore" to "back_pain", "mole" to "rash", "hair_loss" to "memory",
        "fungal" to "itching", "bruising_easily" to "bruise", "sleep_too_much" to "tired", "lonely" to "low_mood", "hallucination" to "confusion",
        "self_harm" to "low_mood", "head_injury" to "headache", "bleeding" to "cut", "swelling" to "joint_swelling", "bite" to "leg_pain",
        "sting" to "rash", "broken_bone" to "arm_pain", "choking" to "swallowing", "heavy_bleeding" to "period_pain", "postmeno_bleeding" to "period_pain",
        "discharge" to "period_pain", "breast_lump" to "chest_pain", "breast_pain" to "chest_pain", "hot_flush" to "fever", "high_sugar" to "low_sugar",
        "lump" to "swallowing", "pale" to "weakness", "trouble_eating" to "no_appetite", "toilet_accident" to "leaking_urine", "night_pain" to "cant_sleep",
        "leaking_urine" to "leaking_urine",
    )
}
