package com.suryaprakash.medlog.feedback

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import com.suryaprakash.medlog.BuildConfig
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Route
import java.io.ByteArrayOutputStream

/** What was on the phone when the report was started. */
data class CaptureMeta(val route: String, val version: String, val role: String, val device: String, val android: String, val time: Long)

/**
 * The picture and details handed from "the page the person was on" to the report page. Routes are plain
 * objects, so this small holder carries the bitmap across.
 */
object FeedbackDraft {
    @Volatile var bitmap: Bitmap? = null
    @Volatile var meta: CaptureMeta? = null
    fun clear() { bitmap = null; meta = null }
}

object Capture {
    /** Longest side of the saved picture; keeps the upload small. */
    private const val MAX_WIDTH = 1080

    fun activity(ctx: Context): Activity? {
        var c: Context? = ctx
        while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
        return null
    }

    fun meta(ctx: Context, nav: Nav?) = CaptureMeta(
        route = nav?.current?.let { it::class.java.simpleName.ifEmpty { it.toString() } } ?: "unknown",
        version = BuildConfig.VERSION_NAME,
        role = ctx.medlog.settings.value.role,
        device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        time = System.currentTimeMillis(),
    )

    /** Photographs the window as it is right now. Null if it can't be drawn. */
    fun shot(activity: Activity): Bitmap? = runCatching {
        val v = activity.window.decorView
        if (v.width <= 0 || v.height <= 0) return@runCatching null
        val full = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(full))
        if (full.width <= MAX_WIDTH) full
        else Bitmap.createScaledBitmap(full, MAX_WIDTH, full.height * MAX_WIDTH / full.width, true).also { full.recycle() }
    }.getOrNull()

    /** Takes the picture and details of the current page, then opens the report page. Used by the shake and the settings rows. */
    fun openFeedback(ctx: Context, nav: Nav) {
        if (nav.current == Route.Feedback) return
        FeedbackDraft.bitmap = activity(ctx)?.let { shot(it) }
        FeedbackDraft.meta = meta(ctx, nav)
        nav.go(Route.Feedback)
    }

    /** The picture with the red pen strokes burned in, as JPEG. Strokes are points from 0 to 1 across the picture. */
    fun compose(bitmap: Bitmap, strokes: List<List<Pair<Float, Float>>>): ByteArray {
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED; style = Paint.Style.STROKE; strokeWidth = out.width * 0.008f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        for (s in strokes) {
            if (s.isEmpty()) continue
            val path = Path().apply {
                moveTo(s[0].first * out.width, s[0].second * out.height)
                if (s.size == 1) lineTo(s[0].first * out.width + 0.1f, s[0].second * out.height)
                for (p in s.drop(1)) lineTo(p.first * out.width, p.second * out.height)
            }
            c.drawPath(path, paint)
        }
        val bytes = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 80, bytes)
        return bytes.toByteArray()
    }
}
