package com.suryaprakash.medlog.pictogram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.suryaprakash.medlog.ui.LocalPalette
import kotlin.math.pow
import androidx.compose.ui.unit.dp

/** One tappable part of the body. Coordinates are in a 100 × 170 box. "Right"/"Left" are the person's own sides. */
data class BodyPart(
    val id: String,
    val label: String,
    val back: Boolean,
    val cx: Float, val cy: Float, val rx: Float, val ry: Float,
    /** catalogue region, used for the picture */
    val region: String,
    /** the problem this usually means when it hurts */
    val painProblem: String,
    val side: String? = null,
)

object Body {
    private fun pair(id: String, label: String, back: Boolean, cx: Float, cy: Float, rx: Float, ry: Float, region: String, pain: String) = listOf(
        BodyPart("${id}_r", "Right $label", back, if (back) 100 - cx else cx, cy, rx, ry, region, pain, "right"),
        BodyPart("${id}_l", "Left $label", back, if (back) cx else 100 - cx, cy, rx, ry, region, pain, "left"),
    )

    val parts: List<BodyPart> = buildList {
        // ── front (the person faces you: their right is on your left) ──
        add(BodyPart("forehead", "Forehead", false, 50f, 12f, 9f, 5f, "head", "headache"))
        addAll(pair("temple", "temple", false, 41f, 18f, 3f, 3.5f, "temple", "headache"))
        addAll(pair("eye", "eye", false, 45.5f, 19.5f, 2.8f, 2f, "eye", "eye_pain"))
        addAll(pair("ear", "ear", false, 38.5f, 22f, 2.4f, 4f, "ear", "earache"))
        add(BodyPart("nose", "Nose", false, 50f, 23.5f, 2.4f, 2.8f, "nose", "blocked_nose"))
        add(BodyPart("mouth", "Mouth and teeth", false, 50f, 29f, 5f, 2.6f, "mouth", "toothache"))
        add(BodyPart("throat", "Throat", false, 50f, 36.5f, 4.5f, 3f, "throat", "sore_throat"))
        addAll(pair("shoulder", "shoulder", false, 32f, 44f, 6f, 4.5f, "shoulder", "shoulder_pain"))
        addAll(pair("chest", "side of chest", false, 42.5f, 53f, 7f, 6f, "chest", "chest_pain"))
        add(BodyPart("chest_c", "Middle of chest", false, 50f, 53f, 3.5f, 6f, "chest", "chest_pain", "middle"))
        add(BodyPart("upper_stomach", "Upper stomach", false, 50f, 66f, 10f, 5f, "stomach", "stomach_pain", "middle"))
        addAll(pair("belly", "lower belly", false, 43.5f, 78f, 6f, 5.5f, "belly", "stomach_pain"))
        add(BodyPart("pelvis", "Bladder area", false, 50f, 88f, 7f, 3.5f, "bladder", "burning_urine", "middle"))
        addAll(pair("upperarm", "upper arm", false, 27.5f, 58f, 4f, 9f, "arm", "arm_pain"))
        addAll(pair("forearm", "forearm", false, 24.5f, 76f, 3.6f, 8f, "arm", "arm_pain"))
        addAll(pair("hand", "hand", false, 22.5f, 91f, 4f, 5f, "hand", "arm_pain"))
        addAll(pair("hip", "hip", false, 39f, 90f, 4f, 4f, "hip", "hip_pain"))
        addAll(pair("thigh", "thigh", false, 44f, 107f, 5.5f, 11f, "leg", "leg_pain"))
        addAll(pair("knee", "knee", false, 44f, 123.5f, 4.6f, 4.2f, "knee", "knee_pain"))
        addAll(pair("shin", "lower leg", false, 44f, 141f, 4.2f, 10f, "leg", "leg_pain"))
        addAll(pair("foot", "foot", false, 43f, 159.5f, 5.5f, 3.6f, "foot", "foot_pain"))
        // ── back (the person faces away: their right is on your right) ──
        add(BodyPart("head_back", "Back of head", true, 50f, 17f, 10f, 9f, "head", "headache"))
        add(BodyPart("neck_back", "Neck", true, 50f, 35.5f, 5.5f, 3.5f, "neck", "neck_pain"))
        addAll(pair("upperback", "upper back", true, 43f, 52f, 7f, 7f, "back", "back_pain"))
        addAll(pair("lowerback", "lower back", true, 44f, 72f, 6.5f, 6f, "back", "back_pain"))
        add(BodyPart("spine", "Spine", true, 50f, 62f, 2.5f, 18f, "back", "back_pain", "middle"))
        addAll(pair("buttock", "buttock", true, 44f, 87f, 6f, 5f, "hip", "hip_pain"))
        addAll(pair("backthigh", "back of thigh", true, 44f, 107f, 5.5f, 11f, "leg", "leg_pain"))
        addAll(pair("calf", "calf", true, 44f, 141f, 4.2f, 10f, "leg", "cramps"))
        addAll(pair("heel", "heel", true, 44f, 159.5f, 4.5f, 3.4f, "foot", "foot_pain"))
    }

    fun byId(id: String) = parts.firstOrNull { it.id == id }
}

/** A tapped point on the body: which side of the figure, where (0..100 × 0..170), and the part it falls in. */
data class Pin(val back: Boolean, val x: Float, val y: Float, val part: BodyPart) {
    fun encode() = "${if (back) "back" else "front"}:${"%.1f".format(java.util.Locale.US, x)},${"%.1f".format(java.util.Locale.US, y)}"
}

/**
 * Front or back body drawing. Tap the exact spot: a red pin is dropped there, and the nearest named part
 * is used for words ("left knee"). Generous reach, so a shaky tap still lands.
 */
/** The part of the body to show, in body units (0..100 × 0..170). */
data class View(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

val WHOLE = View(0f, 0f, 100f, 170f)

/** Zoom to where the problem is, so the drawing is big enough to point at precisely. */
fun viewFor(region: String?): View = when (region) {
    "head", "temple", "face", "eye", "ear", "nose", "mouth", "tooth" -> View(26f, 0f, 74f, 42f)
    "throat", "neck", "shoulder" -> View(14f, 4f, 86f, 64f)
    "chest", "heart", "lungs", "breast", "stomach", "belly", "back", "bladder", "pelvis" -> View(12f, 30f, 88f, 100f)
    "hip", "leg", "knee", "foot" -> View(22f, 80f, 78f, 168f)
    "arm", "hand" -> View(8f, 36f, 92f, 100f)
    else -> WHOLE
}

/** The body chart images (design/pictograms/body.py), cut to the part in view at the size it is shown. */
object BodyArt {
    const val UNIT_PX = 20                // image pixels per body unit
    private val decoders = HashMap<Boolean, android.graphics.BitmapRegionDecoder>()
    private val cache = android.util.LruCache<String, android.graphics.Bitmap>(16 * 1024 * 1024)

    fun bitmap(ctx: android.content.Context, back: Boolean, view: View, widthPx: Int): android.graphics.Bitmap? {
        val want = (view.x1 - view.x0) * UNIT_PX
        var sample = 1
        while (want / (sample * 2) >= widthPx) sample *= 2
        val key = "$back/${view}/$sample"
        cache.get(key)?.let { return it }
        val dec = synchronized(decoders) {
            decoders[back] ?: ctx.applicationContext.assets.open(if (back) "sprites/body_back.png" else "sprites/body_front.png").use {
                if (android.os.Build.VERSION.SDK_INT >= 31) android.graphics.BitmapRegionDecoder.newInstance(it)
                else @Suppress("DEPRECATION") android.graphics.BitmapRegionDecoder.newInstance(it, false)
            }?.also { decoders[back] = it }
        }
        val r = android.graphics.Rect((view.x0 * UNIT_PX).toInt(), (view.y0 * UNIT_PX).toInt(), (view.x1 * UNIT_PX).toInt(), (view.y1 * UNIT_PX).toInt())
        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = if (dec != null) synchronized(dec) { dec.decodeRegion(r, options) } else {
            // A decoder may be unavailable. Use the same asset and crop, rather than crashing.
            val full = ctx.applicationContext.assets.open(if (back) "sprites/body_back.png" else "sprites/body_front.png").use {
                android.graphics.BitmapFactory.decodeStream(it, null, options)
            } ?: return null
            android.graphics.Bitmap.createBitmap(full, r.left / sample, r.top / sample, r.width() / sample, r.height() / sample)
        } ?: return null
        cache.put(key, bmp)
        return bmp
    }
}

@Composable
fun BodyMap(back: Boolean, pins: List<Pin>, modifier: Modifier = Modifier, view: View = WHOLE, onTap: (Pin) -> Unit) {
    val parts = Body.parts.filter { it.back == back && it.cx in view.x0..view.x1 && it.cy in view.y0..view.y1 }.ifEmpty { Body.parts.filter { it.back == back } }
    val vw = view.x1 - view.x0; val vh = view.y1 - view.y0
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var widthPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val art by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, back, view, widthPx) {
        if (widthPx > 0) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            BodyArt.bitmap(ctx, back, view, widthPx)?.asImageBitmap()
        }
    }
    Canvas(
        modifier.aspectRatio(vw / vh, matchHeightConstraintsFirst = true)
            .onSizeChanged { widthPx = it.width }
            .semantics { contentDescription = if (back) "Body from the back. Tap where it is." else "Body from the front. Tap where it is." }
            .pointerInput(back) {
                detectTapGestures { o ->
                    val u = size.width / vw
                    val x = o.x / u + view.x0; val y = o.y / u + view.y0
                    val best = parts.minByOrNull { ((x - it.cx) / (it.rx + 3)).pow(2) + ((y - it.cy) / (it.ry + 3)).pow(2) }
                    if (best != null && ((x - best.cx) / (best.rx + 4)).pow(2) + ((y - best.cy) / (best.ry + 4)).pow(2) <= 1.3f) onTap(Pin(back, x, y, best))
                }
            }
    ) {
        val u = size.width / vw
        art?.let { drawImage(it, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()), filterQuality = androidx.compose.ui.graphics.FilterQuality.High) }
        for (pin in pins.filter { it.back == back }) {
            val c = Offset((pin.x - view.x0) * u, (pin.y - view.y0) * u)
            val r = 9.dp.toPx()
            drawCircle(Color(0x33EF4444), r * 2.4f, c)
            drawCircle(Color(0xFFEF4444), r, c)
            drawCircle(Color.White, r * 0.4f, c)
        }
    }
}
