package com.suryaprakash.medlog.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** One thing that happened, for announcing here and for sending to other people's phones. */
data class Event(val kind: String, val actor: String, val what: String, val entryId: Long?, val at: Long)

/**
 * The app-wide "it worked" announcer. Shows ONE short toast (a new one replaces the old one) such as
 * "Asha added a note" or "You took Metformin", and plays the saved tick.
 */
object Announce {
    /** Set by the sync layer: called for every event made on THIS phone, so it can tell other people. */
    @Volatile var onLocal: ((Event) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var current: Toast? = null

    /**
     * Something was done on this phone. [actor] null means "You". [what] is the rest of the sentence:
     * done(ctx, null, "took Metformin") shows "You took Metformin"; done(ctx, "Asha", "added a note") shows "Asha added a note".
     */
    fun done(ctx: Context, actor: String?, what: String, kind: String = "action", entryId: Long? = null) {
        feedback(ctx)
        show(ctx, actor, what)
        val e = Event(kind, actor ?: "You", what, entryId, System.currentTimeMillis())
        runCatching { onLocal?.invoke(e) }
    }

    /** Show an event that came from another phone. Never re-broadcasts. */
    fun remote(ctx: Context, e: Event) = show(ctx, e.actor, e.what)

    /** Just the toast, no sound and no broadcast. */
    fun show(ctx: Context, actor: String?, what: String) {
        val app = ctx.applicationContext
        val text = "${actor?.takeIf { it.isNotBlank() } ?: "You"} $what"
        main.post {
            runCatching {
                current?.cancel()
                current = Toast.makeText(app, text, Toast.LENGTH_SHORT).also { it.show() }
            }
        }
    }

    /** The saved tick and a short buzz. */
    fun feedback(ctx: Context) {
        runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 60).startTone(android.media.ToneGenerator.TONE_PROP_ACK, 180) }
        runCatching {
            val v = ctx.getSystemService(android.os.Vibrator::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) v?.vibrate(android.os.VibrationEffect.createOneShot(120, 180)) else @Suppress("DEPRECATION") v?.vibrate(120)
        }
    }
}
