package com.suryaprakash.medlog.pictogram

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

/**
 * The MedLog pictogram set (plan section 9): one consistent character, a glow on *where* it is,
 * and a simple mark for *what* it is. Drawn as vectors, so it is sharp at any size, works in the
 * widget, and follows high-contrast mode. Always shown with its word label.
 *
 * A designer's hand-finished set can replace this drawing code later without changing any screen.
 */
object Pictogram {
    data class Spec(
        val region: String,
        val glyph: String,
        val figure: Int = 0,     // 0 older woman, 1 older man, 2 woman, 3 man
        val skin: Int = 1,
        val highContrast: Boolean = false,
    )

    private val SKIN = intArrayOf(0xFFF7D9C4.toInt(), 0xFFE9BD98.toInt(), 0xFFC68E62.toInt(), 0xFF8B5A3C.toInt())
    private val HAIR = intArrayOf(0xFFD9D9DE.toInt(), 0xFFC4C4CA.toInt(), 0xFF3B2A20.toInt(), 0xFF8A6A4C.toInt())
    private const val SHIRT = 0xFF8DB4DC.toInt()
    private const val SHIRT_DARK = 0xFF6F98C4.toInt()
    private const val TROUSERS = 0xFF5C6F86.toInt()
    private const val RED = 0xFFD6453B.toInt()
    private const val GREEN = 0xFF7BB661.toInt()
    private const val BLUE = 0xFF5BA3D9.toInt()
    private const val YELLOW = 0xFFF2C230.toInt()

    private val FULL_REGIONS = setOf("arm", "hand", "leg", "knee", "foot", "hip", "pelvis", "bladder")
    private val FULL_GLYPHS = setOf("fall", "walker", "cast")

    fun bitmap(sizePx: Int, spec: Spec): Bitmap {
        val b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        draw(Canvas(b), sizePx.toFloat(), spec)
        return b
    }

    fun draw(c: Canvas, size: Float, spec: Spec) {
        val k = size / 100f
        c.save()
        c.scale(k, k)
        val d = Drawer(c, spec)
        d.background()
        val full = spec.region in FULL_REGIONS || spec.glyph in FULL_GLYPHS
        if (spec.glyph == "fall") { c.save(); c.rotate(-38f, 50f, 70f) }
        if (full) d.fullBody() else d.bust()
        if (spec.glyph == "fall") c.restore()
        d.glow(full)
        d.glyph(full)
        c.restore()
    }

    private class Drawer(val c: Canvas, val s: Spec) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val outline = if (s.highContrast) Color.BLACK else 0xFF33404D.toInt()
        val skin = SKIN[s.skin.coerceIn(0, 3)]
        val hair = HAIR[s.figure.coerceIn(0, 3)]
        // anchor of the head/face for expressions
        var hx = 50f; var hy = 40f; var hr = 17f

        fun fill(color: Int) = p.apply { style = Paint.Style.FILL; this.color = color; shader = null }
        fun line(color: Int, w: Float) = stroke.apply { this.color = color; strokeWidth = w }

        fun background() {
            fill(if (s.highContrast) Color.WHITE else 0xFFECE7F6.toInt())
            c.drawCircle(50f, 52f, 46f, p)
            if (s.highContrast) c.drawCircle(50f, 52f, 46f, line(Color.BLACK, 2f))
            c.save()
            val clip = Path().apply { addCircle(50f, 52f, 46f, Path.Direction.CW) }
            c.clipPath(clip)
            clipped = true
        }
        var clipped = false

        fun bust() {
            hx = 50f; hy = 40f; hr = 17f
            // torso
            val body = Path().apply {
                moveTo(16f, 104f); cubicTo(16f, 80f, 26f, 69f, 40f, 67f); lineTo(60f, 67f); cubicTo(74f, 69f, 84f, 80f, 84f, 104f); close()
            }
            c.drawPath(body, fill(SHIRT)); c.drawPath(body, line(outline, 1.6f))
            // neck
            c.drawRect(44.5f, 54f, 55.5f, 68f, fill(skin))
            c.drawLine(44.5f, 56f, 44.5f, 67f, line(outline, 1.4f)); c.drawLine(55.5f, 56f, 55.5f, 67f, line(outline, 1.4f))
            // collar
            val collar = Path().apply { moveTo(42f, 67f); lineTo(50f, 75f); lineTo(58f, 67f) }
            c.drawPath(collar, line(SHIRT_DARK, 1.6f))
            head(hx, hy, hr)
        }

        fun fullBody() {
            hx = 50f; hy = 23f; hr = 11f
            // legs
            c.drawRoundRect(RectF(41f, 60f, 49f, 90f), 3f, 3f, fill(TROUSERS)); c.drawRoundRect(RectF(51f, 60f, 59f, 90f), 3f, 3f, fill(TROUSERS))
            c.drawRoundRect(RectF(41f, 60f, 49f, 90f), 3f, 3f, line(outline, 1.2f)); c.drawRoundRect(RectF(51f, 60f, 59f, 90f), 3f, 3f, line(outline, 1.2f))
            // feet
            c.drawOval(RectF(38f, 88f, 49f, 94f), fill(0xFF3D4650.toInt())); c.drawOval(RectF(51f, 88f, 62f, 94f), fill(0xFF3D4650.toInt()))
            // arms
            line(SHIRT, 7f); c.drawLine(38f, 39f, 30f, 58f, stroke); c.drawLine(62f, 39f, 70f, 58f, stroke)
            c.drawCircle(29.5f, 60f, 3.6f, fill(skin)); c.drawCircle(70.5f, 60f, 3.6f, fill(skin))
            // torso
            val t = RectF(37f, 34f, 63f, 63f)
            c.drawRoundRect(t, 7f, 7f, fill(SHIRT)); c.drawRoundRect(t, 7f, 7f, line(outline, 1.4f))
            c.drawRect(47f, 31f, 53f, 35f, fill(skin))
            head(hx, hy, hr)
            if (s.glyph == "walker") { line(0xFF8D6E4F.toInt(), 2.6f); c.drawLine(73f, 58f, 78f, 93f, stroke); c.drawLine(73f, 58f, 69f, 56f, stroke) }
        }

        fun head(x: Float, y: Float, r: Float) {
            val older = s.figure <= 1
            // hair behind (long hair for younger woman, bun for older woman)
            if (s.figure == 2) { c.drawRoundRect(RectF(x - r * 1.08f, y - r * 0.9f, x + r * 1.08f, y + r * 1.15f), r * 0.6f, r * 0.6f, fill(hair)) }
            if (s.figure == 0) c.drawCircle(x, y - r * 1.05f, r * 0.42f, fill(hair))
            // ears
            c.drawCircle(x - r * 0.98f, y + r * 0.05f, r * 0.22f, fill(skin)); c.drawCircle(x + r * 0.98f, y + r * 0.05f, r * 0.22f, fill(skin))
            // face
            c.drawCircle(x, y, r, fill(skin)); c.drawCircle(x, y, r, line(outline, 1.5f))
            // hair on top
            val top = Path()
            when (s.figure) {
                0 -> { top.addArc(RectF(x - r * 1.02f, y - r * 1.05f, x + r * 1.02f, y + r * 0.6f), 180f, 180f) }
                1 -> { // grey at the sides, thin on top
                    top.addArc(RectF(x - r * 1.04f, y - r * 0.7f, x - r * 0.35f, y + r * 0.4f), 160f, 130f)
                    top.addArc(RectF(x + r * 0.35f, y - r * 0.7f, x + r * 1.04f, y + r * 0.4f), 250f, 130f)
                }
                2 -> { top.addArc(RectF(x - r * 1.05f, y - r * 1.08f, x + r * 1.05f, y + r * 0.7f), 180f, 180f) }
                else -> { // curly
                    for (i in 0..6) { val a = Math.toRadians((200 + i * 23).toDouble()); top.addCircle(x + (r * 0.82f * cos(a)).toFloat(), y - r * 0.15f + (r * 0.82f * sin(a)).toFloat(), r * 0.36f, Path.Direction.CW) }
                }
            }
            if (s.figure == 1) c.drawPath(top, line(hair, r * 0.28f)) else c.drawPath(top, fill(hair))
            // glasses for older figures
            if (older) {
                line(outline, 1.1f)
                c.drawCircle(x - r * 0.38f, y + r * 0.05f, r * 0.27f, stroke); c.drawCircle(x + r * 0.38f, y + r * 0.05f, r * 0.27f, stroke)
                c.drawLine(x - r * 0.11f, y + r * 0.05f, x + r * 0.11f, y + r * 0.05f, stroke)
            }
            face(x, y, r)
        }

        fun face(x: Float, y: Float, r: Float) {
            val g = s.glyph
            val ex = r * 0.38f; val ey = y + r * 0.05f; val er = r * 0.09f
            // tints
            when (g) {
                "green", "vomit" -> { fill(0x807BB661.toInt()); c.drawArc(RectF(x - r, y - r, x + r, y + r), 180f, 180f, true, p) }
                "heat", "sun" -> { fill(0x55E0554A); c.drawCircle(x, y, r, p) }
                "yellow" -> { fill(0x55F2C230); c.drawCircle(x, y, r, p) }
                "stroke" -> {}
            }
            line(outline, 1.4f)
            when (g) {
                "pain", "throb", "press", "bite", "sting", "flame", "cast", "bandage", "bruise", "swell", "fall", "itch", "tingle", "lump" -> {
                    // squeezed eyes > <
                    c.drawLine(x - ex - er * 1.6f, ey - er * 1.3f, x - ex + er * 1.2f, ey, stroke); c.drawLine(x - ex + er * 1.2f, ey, x - ex - er * 1.6f, ey + er * 1.3f, stroke)
                    c.drawLine(x + ex + er * 1.6f, ey - er * 1.3f, x + ex - er * 1.2f, ey, stroke); c.drawLine(x + ex - er * 1.2f, ey, x + ex + er * 1.6f, ey + er * 1.3f, stroke)
                }
                "zzz", "vomit", "green", "heat" -> {
                    c.drawArc(RectF(x - ex - er * 1.6f, ey - er, x - ex + er * 1.6f, ey + er * 1.4f), 20f, 140f, false, stroke)
                    c.drawArc(RectF(x + ex - er * 1.6f, ey - er, x + ex + er * 1.6f, ey + er * 1.4f), 20f, 140f, false, stroke)
                }
                "spin", "stars" -> {
                    for (sx in listOf(x - ex, x + ex)) { val sp = Path(); var a = 0.0; sp.moveTo(sx, ey); while (a < 12) { val rr = er * 0.25f * a.toFloat(); sp.lineTo(sx + rr * cos(a).toFloat(), ey + rr * sin(a).toFloat()); a += 0.4 }; c.drawPath(sp, line(outline, 0.9f)) }
                }
                "blur" -> { c.drawLine(x - ex - er * 1.5f, ey, x - ex + er * 1.5f, ey, line(outline, 1.6f)); c.drawLine(x + ex - er * 1.5f, ey, x + ex + er * 1.5f, ey, stroke) }
                "stroke" -> {
                    c.drawCircle(x - ex, ey, er, fill(outline))
                    c.drawLine(x + ex - er * 1.4f, ey + er * 0.4f, x + ex + er * 1.4f, ey + er * 0.9f, line(outline, 1.5f))
                }
                else -> { c.drawCircle(x - ex, ey, er, fill(outline)); c.drawCircle(x + ex, ey, er, fill(outline)) }
            }
            // eyebrows worried for sad/worry/question/cloud
            if (g in setOf("cloud", "worry", "question", "heart", "down", "shake")) {
                line(outline, 1.2f)
                c.drawLine(x - ex - er * 1.6f, ey - er * 2.4f, x - ex + er * 1.4f, ey - er * 3.4f, stroke)
                c.drawLine(x + ex + er * 1.6f, ey - er * 2.4f, x + ex - er * 1.4f, ey - er * 3.4f, stroke)
            }
            // cheeks
            if (!s.highContrast) { fill(0x40E0554A); c.drawCircle(x - r * 0.55f, y + r * 0.35f, r * 0.13f, p); c.drawCircle(x + r * 0.55f, y + r * 0.35f, r * 0.13f, p) }
            // mouth
            val my = y + r * 0.52f; val mw = r * 0.28f
            line(outline, 1.5f)
            when (g) {
                "puff", "vomit", "spray", "hic", "wind", "choke", "block" -> c.drawOval(RectF(x - mw * 0.5f, my - mw * 0.35f, x + mw * 0.5f, my + mw * 0.45f), fill(0xFF7A2E2E.toInt()))
                "stroke" -> c.drawLine(x - mw, my - mw * 0.2f, x + mw, my + mw * 0.45f, stroke)
                "heart", "cloud", "down" -> c.drawArc(RectF(x - mw, my, x + mw, my + mw * 1.2f), 200f, 140f, false, stroke)
                "zzz" -> c.drawLine(x - mw * 0.6f, my + mw * 0.2f, x + mw * 0.6f, my + mw * 0.2f, stroke)
                "none" -> c.drawArc(RectF(x - mw, my - mw * 0.9f, x + mw, my + mw * 0.5f), 25f, 130f, false, stroke)
                else -> c.drawArc(RectF(x - mw, my, x + mw, my + mw * 1.3f), 205f, 130f, false, stroke)
            }
            if (g == "cold") { fill(BLUE); c.drawOval(RectF(x - mw * 0.9f, my, x + mw * 0.9f, my + mw * 0.5f), p) }
            if (g == "yellow") { fill(YELLOW); c.drawCircle(x - ex, ey, er * 1.7f, p); c.drawCircle(x + ex, ey, er * 1.7f, p); c.drawCircle(x - ex, ey, er, fill(outline)); c.drawCircle(x + ex, ey, er, fill(outline)) }
        }

        /** Where on the body: (x, y) for each region in the two layouts. */
        fun anchor(full: Boolean): Pair<Float, Float> = if (full) when (s.region) {
            "arm" -> 33f to 50f; "hand" -> 29.5f to 60f; "leg" -> 45f to 80f; "knee" -> 45f to 76f; "foot" -> 44f to 90f
            "hip" -> 42f to 62f; "pelvis", "bladder" -> 50f to 60f; "back" -> 50f to 48f; "stomach", "belly" -> 50f to 52f
            "chest", "heart", "lungs", "breast" -> 50f to 42f; "head", "mind" -> 50f to 16f; else -> 50f to 48f
        } else when (s.region) {
            "head" -> 50f to 27f; "temple" -> 35.5f to 35f; "face" -> 50f to 45f; "eye" -> 43.5f to 41f; "ear" -> 33f to 41f
            "nose" -> 50f to 45f; "mouth" -> 50f to 49f; "tooth" -> 54f to 49f; "throat", "neck" -> 50f to 61f; "shoulder" -> 28f to 76f
            "chest", "lungs" -> 50f to 83f; "heart" -> 57f to 82f; "breast" -> 40f to 84f; "stomach" -> 50f to 92f; "belly" -> 50f to 96f
            "back" -> 74f to 86f; "mind" -> 50f to 16f; "whole" -> 50f to 60f; else -> 50f to 80f
        }

        fun glow(full: Boolean) {
            if (s.region == "whole" || s.region == "mind") return
            if (s.glyph in setOf("zzz", "cloud", "question", "worry", "heart", "spin", "stars", "sun", "fall", "walker", "glass", "plate", "pill", "sugar", "up", "down", "flower")) return
            val (x, y) = anchor(full)
            val r = if (full) 8f else 10f
            p.shader = RadialGradient(x, y, r, intArrayOf(0xAAE0554A.toInt(), 0x00E0554A), null, Shader.TileMode.CLAMP)
            p.style = Paint.Style.FILL
            c.drawCircle(x, y, r, p)
            p.shader = null
        }

        fun zig(x: Float, y: Float, h: Float, color: Int = RED) {
            val z = Path().apply { moveTo(x, y); lineTo(x + h * 0.35f, y + h * 0.35f); lineTo(x - h * 0.05f, y + h * 0.5f); lineTo(x + h * 0.3f, y + h) }
            c.drawPath(z, line(color, 1.8f))
        }

        fun drop(x: Float, y: Float, r: Float, color: Int) {
            val d = Path().apply { moveTo(x, y - r * 1.8f); cubicTo(x + r * 1.1f, y - r * 0.4f, x + r, y + r, x, y + r); cubicTo(x - r, y + r, x - r * 1.1f, y - r * 0.4f, x, y - r * 1.8f); close() }
            c.drawPath(d, fill(color))
        }

        fun cloud(x: Float, y: Float, w: Float, color: Int) {
            fill(color)
            c.drawCircle(x - w * 0.3f, y, w * 0.28f, p); c.drawCircle(x, y - w * 0.12f, w * 0.34f, p); c.drawCircle(x + w * 0.32f, y, w * 0.26f, p)
            c.drawRoundRect(RectF(x - w * 0.55f, y, x + w * 0.55f, y + w * 0.26f), w * 0.13f, w * 0.13f, p)
        }

        fun text(t: String, x: Float, y: Float, size: Float, color: Int) {
            p.style = Paint.Style.FILL; p.color = color; p.textSize = size; p.typeface = Typeface.DEFAULT_BOLD; p.shader = null
            c.drawText(t, x, y, p)
        }

        fun glyph(full: Boolean) {
            val (ax, ay) = anchor(full)
            when (s.glyph) {
                "pain" -> { zig(ax - 11f, ay - 9f, 9f); zig(ax + 9f, ay - 9f, 9f) }
                "throb" -> { line(RED, 1.6f); for (i in 1..2) { c.drawArc(RectF(ax - 6f - i * 4, ay - 6f - i * 4, ax + 6f + i * 4, ay + 6f + i * 4), 200f, 50f, false, stroke); c.drawArc(RectF(ax - 6f - i * 4, ay - 6f - i * 4, ax + 6f + i * 4, ay + 6f + i * 4), 290f, 50f, false, stroke) } }
                "press" -> { line(RED, 2f); for ((dx, dy) in listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)) { val sx = ax + dx * 16f; val sy = ay + dy * 10f; c.drawLine(sx, sy, ax + dx * 8f, ay + dy * 5f, stroke) } }
                "vomit" -> {
                    val v = Path().apply { moveTo(hx - 3f, hy + hr * 0.6f); cubicTo(hx - 8f, hy + hr + 6f, hx + 6f, hy + hr + 10f, hx - 2f, hy + hr + 20f); lineTo(hx + 4f, hy + hr + 20f); cubicTo(hx + 10f, hy + hr + 8f, hx + 2f, hy + hr + 4f, hx + 3f, hy + hr * 0.6f); close() }
                    c.drawPath(v, fill(GREEN)); drop(hx - 8f, hy + hr + 20f, 2f, GREEN); drop(hx + 9f, hy + hr + 16f, 1.6f, GREEN)
                }
                "green" -> { line(GREEN, 2f); for (i in 0..2) { val y = ay - 4f + i * 4f; val w = Path().apply { moveTo(ax - 10f, y); cubicTo(ax - 5f, y - 3f, ax, y + 3f, ax + 5f, y); cubicTo(ax + 7f, y - 2f, ax + 9f, y - 1f, ax + 10f, y) }; c.drawPath(w, stroke) } }
                "blood" -> { drop(ax + 6f, ay + 2f, 3.6f, RED); drop(ax - 4f, ay + 8f, 2.4f, RED) }
                "drip" -> { drop(ax + 3f, ay + 7f, 2.2f, BLUE); drop(ax + 6f, ay + 14f, 1.8f, BLUE) }
                "drops" -> { drop(hx + hr * 0.95f, hy - hr * 0.2f, 2.4f, BLUE); drop(hx - hr * 0.9f, hy + 2f, 2f, BLUE) }
                "heat" -> {
                    line(0xFF9E9E9E.toInt(), 2.2f); c.drawLine(hx + 2f, hy + hr * 0.55f, hx + hr * 0.95f, hy + hr * 0.9f, stroke)
                    fill(RED); c.drawCircle(hx + hr * 0.98f, hy + hr * 0.93f, 1.8f, p)
                    line(RED, 1.4f); for (i in -1..1) { val x = hx + i * 7f; val w = Path().apply { moveTo(x, hy - hr - 3f); cubicTo(x - 3f, hy - hr - 6f, x + 3f, hy - hr - 8f, x, hy - hr - 12f) }; c.drawPath(w, stroke) }
                }
                "cold" -> { line(BLUE, 1.6f); for (side in listOf(-1, 1)) for (i in 0..2) { val x = 50f + side * (36f - i * 3f); c.drawLine(x, 68f + i * 8f, x + side * 4f, 72f + i * 8f, stroke) } ; text("*", 70f, 26f, 16f, BLUE) }
                "spin" -> { line(0xFF6A5ACD.toInt(), 1.6f); c.drawOval(RectF(hx - 16f, hy - hr - 8f, hx + 16f, hy - hr + 2f), stroke); fill(YELLOW); c.drawCircle(hx + 14f, hy - hr - 5f, 2f, p) }
                "stars" -> { for ((dx, dy) in listOf(-13f to -2f, 0f to -8f, 13f to -2f)) star(hx + dx, hy - hr + dy, 3.4f) }
                "puff" -> { cloud(hx + hr + 9f, hy + hr * 0.45f, 11f, 0xFFB8C3CF.toInt()); cloud(hx + hr + 17f, hy + hr * 0.1f, 7f, 0xFFCDD5DE.toInt()) }
                "spray" -> { fill(BLUE); for (i in 0..7) { val a = Math.toRadians(-30.0 + i * 9); c.drawCircle(hx + 4f + (10 + i % 3 * 4) * cos(a).toFloat(), hy + 6f + (10 + i % 3 * 4) * sin(a).toFloat(), 1.2f, p) } }
                "zzz" -> { text("z", hx + hr + 1f, hy - hr + 6f, 7f, 0xFF6A5ACD.toInt()); text("z", hx + hr + 6f, hy - hr, 9f, 0xFF6A5ACD.toInt()); text("Z", hx + hr + 12f, hy - hr - 7f, 12f, 0xFF6A5ACD.toInt()) }
                "cloud" -> { cloud(hx, hy - hr - 9f, 18f, 0xFF9AA5B1.toInt()); line(BLUE, 1.4f); for (i in -1..1) c.drawLine(hx + i * 6f, hy - hr - 2f, hx + i * 6f - 1.5f, hy - hr + 3f, stroke) }
                "worry" -> { drop(hx + hr * 0.9f, hy - hr * 0.35f, 2.4f, BLUE); line(0xFF6A5ACD.toInt(), 1.4f); val w = Path().apply { moveTo(hx - 9f, hy - hr - 7f); cubicTo(hx - 5f, hy - hr - 12f, hx - 1f, hy - hr - 2f, hx + 3f, hy - hr - 7f); cubicTo(hx + 6f, hy - hr - 11f, hx + 9f, hy - hr - 5f, hx + 11f, hy - hr - 8f) }; c.drawPath(w, stroke) }
                "dots" -> { fill(RED); for ((dx, dy) in listOf(-5f to -3f, 0f to 2f, 5f to -2f, -3f to 5f, 4f to 5f, 1f to -6f)) c.drawCircle(ax + dx, ay + dy, 1.5f, p) }
                "itch" -> { line(RED, 1.4f); for (i in 0..2) c.drawLine(ax - 5f + i * 4f, ay - 5f, ax - 2f + i * 4f, ay + 5f, stroke) }
                "swell" -> { fill(0x99F4A6A0.toInt()); c.drawCircle(ax, ay, 6.5f, p); c.drawCircle(ax, ay, 6.5f, line(RED, 1.2f)) }
                "bandage" -> { c.save(); c.rotate(-30f, ax, ay); val r = RectF(ax - 8f, ay - 3.2f, ax + 8f, ay + 3.2f); c.drawRoundRect(r, 3f, 3f, fill(0xFFF1D3A8.toInt())); c.drawRoundRect(r, 3f, 3f, line(0xFFB08A5A.toInt(), 0.9f)); fill(0xFFE3B97F.toInt()); c.drawRect(ax - 2.4f, ay - 3.2f, ax + 2.4f, ay + 3.2f, p); c.restore() }
                "cast" -> { fill(Color.WHITE); c.drawRoundRect(RectF(26f, 44f, 48f, 54f), 4f, 4f, p); c.drawRoundRect(RectF(26f, 44f, 48f, 54f), 4f, 4f, line(outline, 1f)); line(0xFF5B8DB8.toInt(), 2.4f); c.drawLine(40f, 34f, 60f, 46f, stroke) }
                "flame" -> { val f = Path().apply { moveTo(ax, ay + 6f); cubicTo(ax - 8f, ay + 2f, ax - 3f, ay - 6f, ax, ay - 10f); cubicTo(ax + 2f, ay - 4f, ax + 8f, ay - 2f, ax, ay + 6f); close() }; c.drawPath(f, fill(0xFFF08A24.toInt())); drop(ax, ay + 2f, 2f, YELLOW) }
                "wind" -> { line(BLUE, 1.6f); for (i in 0..2) { val y = hy + hr * 0.4f + i * 4f - 4f; c.drawLine(hx + hr * 0.5f, y, hx + hr + 12f, y + (i - 1) * 2f, stroke) }; fill(skin); c.drawCircle(50f, 82f, 4f, p) }
                "beat" -> { heart(ax, ay, 7f, RED); line(RED, 1.2f); c.drawLine(ax + 9f, ay - 3f, ax + 13f, ay - 5f, stroke); c.drawLine(ax + 9f, ay + 1f, ax + 14f, ay + 1f, stroke) }
                "sugar" -> { val r = RectF(62f, 70f, 76f, 90f); c.drawRoundRect(r, 3f, 3f, fill(0xFF6B7785.toInt())); c.drawRect(64.5f, 73f, 73.5f, 79f, fill(0xFFCFE8D6.toInt())); drop(69f, 86f, 1.8f, RED) }
                "pill" -> { c.save(); c.rotate(-35f, 70f, 80f); c.drawRoundRect(RectF(62f, 76f, 78f, 84f), 4f, 4f, fill(Color.WHITE)); c.drawRoundRect(RectF(62f, 76f, 70f, 84f), 4f, 4f, fill(RED)); c.drawRect(66f, 76f, 70f, 84f, p); c.drawRoundRect(RectF(62f, 76f, 78f, 84f), 4f, 4f, line(outline, 0.9f)); c.restore() }
                "lump" -> { fill(0xFFE6A67F.toInt()); c.drawCircle(ax + 2f, ay, 3.8f, p); c.drawCircle(ax + 2f, ay, 3.8f, line(RED, 1.2f)) }
                "blur" -> { line(0xFF9AA5B1.toInt(), 1.4f); for (i in 0..1) { val y = hy + i * 3f - 1f; val w = Path().apply { moveTo(hx - hr * 0.8f, y); cubicTo(hx - 4f, y - 2f, hx + 4f, y + 2f, hx + hr * 0.8f, y) }; c.drawPath(w, stroke) } }
                "waves" -> { line(BLUE, 1.5f); for (i in 1..3) c.drawArc(RectF(ax - 4f - i * 3.5f, ay - 4f - i * 3.5f, ax + 4f + i * 3.5f, ay + 4f + i * 3.5f), 140f, 80f, false, stroke) }
                "question" -> text("?", hx + hr * 0.6f, hy - hr - 1f, 18f, 0xFF6A5ACD.toInt())
                "toilet" -> { fill(Color.WHITE); c.drawRoundRect(RectF(64f, 74f, 76f, 80f), 2f, 2f, p); c.drawOval(RectF(62f, 79f, 80f, 86f), p); c.drawRect(66f, 85f, 74f, 93f, p); line(outline, 1f); c.drawRoundRect(RectF(64f, 74f, 76f, 80f), 2f, 2f, stroke); c.drawOval(RectF(62f, 79f, 80f, 86f), stroke); c.drawRect(66f, 85f, 74f, 93f, stroke) }
                "shake" -> { line(0xFF6A5ACD.toInt(), 1.4f); for (side in listOf(-1, 1)) for (i in 0..1) { val x = ax + side * (10f + i * 3.5f); c.drawArc(RectF(x - 3f, ay - 6f, x + 3f, ay + 6f), if (side < 0) 110f else -70f, 140f, false, stroke) } }
                "glass" -> { val g = Path().apply { moveTo(64f, 70f); lineTo(76f, 70f); lineTo(74f, 90f); lineTo(66f, 90f); close() }; c.drawPath(g, fill(0x6690CAF9)); c.drawRect(65.5f, 78f, 74.5f, 89f, fill(0xAA64B5F6.toInt())); c.drawPath(g, line(outline, 1f)) }
                "plate" -> { c.drawOval(RectF(58f, 80f, 84f, 90f), fill(Color.WHITE)); c.drawOval(RectF(58f, 80f, 84f, 90f), line(outline, 1f)); c.drawOval(RectF(64f, 82f, 78f, 88f), line(0xFFB0BEC5.toInt(), 0.8f)); line(0xFF90A4AE.toInt(), 1.6f); c.drawLine(86f, 76f, 86f, 92f, stroke) }
                "bite" -> { line(RED, 1.2f); for (i in 0..3) { val x = ax - 6f + i * 4f; c.drawLine(x, ay - 4f, x + 2f, ay - 1f, stroke); c.drawLine(x, ay + 4f, x + 2f, ay + 1f, stroke) } }
                "sting" -> { c.drawOval(RectF(ax + 4f, ay - 12f, ax + 13f, ay - 6f), fill(YELLOW)); line(outline, 1.4f); c.drawLine(ax + 7f, ay - 12f, ax + 7f, ay - 6f, stroke); c.drawLine(ax + 10f, ay - 12f, ax + 10f, ay - 6f, stroke); fill(0x99FFFFFF.toInt()); c.drawOval(RectF(ax + 5f, ay - 17f, ax + 10f, ay - 12f), p) }
                "block" -> { line(RED, 2f); c.drawCircle(ax + 10f, ay - 6f, 6f, stroke); c.drawLine(ax + 6f, ay - 10f, ax + 14f, ay - 2f, stroke) }
                "up", "down" -> { line(if (s.glyph == "up") RED else BLUE, 2.4f); val x = 76f; val (y1, y2) = if (s.glyph == "up") 88f to 68f else 68f to 88f; c.drawLine(x, y1, x, y2, stroke); c.drawLine(x, y2, x - 5f, y2 + (y1 - y2) / 4f, stroke); c.drawLine(x, y2, x + 5f, y2 + (y1 - y2) / 4f, stroke) }
                "hic" -> text("hic", hx + hr + 2f, hy + 2f, 9f, 0xFF6A5ACD.toInt())
                "bubble" -> { fill(0x99E3F2FD.toInt()); for ((dx, dy) in listOf(-4f to -2f, 3f to 2f, 0f to 6f)) { c.drawCircle(ax + dx, ay + dy, 2.6f, p); c.drawCircle(ax + dx, ay + dy, 2.6f, line(0xFF90A4AE.toInt(), 0.8f)) } }
                "sun" -> { fill(YELLOW); c.drawCircle(18f, 20f, 6f, p); line(YELLOW, 1.6f); for (i in 0 until 8) { val a = Math.toRadians(i * 45.0); c.drawLine(18f + 8f * cos(a).toFloat(), 20f + 8f * sin(a).toFloat(), 18f + 11f * cos(a).toFloat(), 20f + 11f * sin(a).toFloat(), stroke) } }
                "bruise" -> { fill(0xAA7E57C2.toInt()); c.drawOval(RectF(ax - 5f, ay - 3.5f, ax + 5f, ay + 3.5f), p) }
                "flower" -> { fill(0xFFF48FB1.toInt()); for (i in 0 until 5) { val a = Math.toRadians(i * 72.0); c.drawCircle(74f + 4f * cos(a).toFloat(), 78f + 4f * sin(a).toFloat(), 3f, p) }; fill(YELLOW); c.drawCircle(74f, 78f, 2f, p) }
                "tingle" -> { line(0xFF6A5ACD.toInt(), 1.2f); for (i in 0 until 6) { val a = Math.toRadians(i * 60.0); c.drawLine(ax + 5f * cos(a).toFloat(), ay + 5f * sin(a).toFloat(), ax + 8f * cos(a).toFloat(), ay + 8f * sin(a).toFloat(), stroke) } }
                "stroke" -> { line(RED, 2f); c.drawLine(70f, 70f, 70f, 86f, stroke); c.drawLine(70f, 86f, 66f, 81f, stroke); c.drawLine(70f, 86f, 74f, 81f, stroke) }
                "heart" -> heart(hx + hr + 8f, hy - hr + 2f, 6f, 0xFFF48FB1.toInt())
                "fall" -> { line(outline, 1.6f); c.drawLine(10f, 94f, 90f, 94f, stroke); line(0xFF9AA5B1.toInt(), 1.4f); c.drawArc(RectF(60f, 20f, 80f, 40f), 270f, 90f, false, stroke); c.drawArc(RectF(66f, 14f, 90f, 38f), 270f, 90f, false, stroke) }
                "walker" -> {}
                "choke" -> { fill(skin); c.drawCircle(47f, 62f, 4f, p); c.drawCircle(53f, 62f, 4f, p) }
            }
            if (clipped) { c.restore(); clipped = false }
        }

        fun star(x: Float, y: Float, r: Float) {
            val st = Path()
            for (i in 0 until 10) { val rr = if (i % 2 == 0) r else r * 0.45f; val a = Math.toRadians(-90.0 + i * 36); if (i == 0) st.moveTo(x + rr * cos(a).toFloat(), y + rr * sin(a).toFloat()) else st.lineTo(x + rr * cos(a).toFloat(), y + rr * sin(a).toFloat()) }
            st.close(); c.drawPath(st, fill(YELLOW)); c.drawPath(st, line(0xFFB8860B.toInt(), 0.6f))
        }

        fun heart(x: Float, y: Float, r: Float, color: Int) {
            val h = Path().apply { moveTo(x, y + r * 0.9f); cubicTo(x - r * 1.4f, y - r * 0.1f, x - r * 0.7f, y - r * 1.2f, x, y - r * 0.4f); cubicTo(x + r * 0.7f, y - r * 1.2f, x + r * 1.4f, y - r * 0.1f, x, y + r * 0.9f); close() }
            c.drawPath(h, fill(color))
        }
    }
}
