package com.suryaprakash.medlog.help

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * The helper's alarm, in the style of Meeting Timer: a short 2.4 kHz beep on the alarm stream (it sounds even on
 * silent), with urgency only in how close together the beeps come.
 *
 * A message gives the helper [WINDOW] seconds to answer: one beep when it arrives, one at 0:10, two at 0:05,
 * three at 0:03, then a continuous alarm until someone answers. The countdown runs here, not on the screen, so it
 * still escalates when the screen can't show (a locked phone that blocks full-screen alerts). Urgent alerts
 * (SOS, danger, fall) also beep every five seconds while counting down.
 */
object AlertSound {
    const val WINDOW = 30
    /** When the helper must answer by (ms), or null when no alert is waiting. */
    val deadline = MutableStateFlow<Long?>(null)
    /** True once the countdown ran out and the alarm is continuous. */
    val screaming = MutableStateFlow(false)

    private const val RATE = 44_100
    private const val BEEP_MS = 110
    private val handler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var vibrator: Vibrator? = null
    private var tick: Runnable? = null

    fun start(ctx: Context, urgent: Boolean) {
        stop()
        val app = ctx.applicationContext
        vibrator = if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
        val end = System.currentTimeMillis() + WINDOW * 1000L
        deadline.value = end
        beeps(if (urgent) 3 else 1, 0.25)
        var last = WINDOW
        val r = object : Runnable {
            override fun run() {
                val left = ((end - System.currentTimeMillis() + 999) / 1000).toInt()
                if (left != last) {
                    last = left
                    when {
                        left <= 0 -> { screaming.value = true; loop(if (urgent) 0.3 else 0.5); handler.postDelayed({ stop() }, 10 * 60_000L); return }
                        left == 3 -> beeps(3, 0.2)
                        left == 5 -> beeps(2, 0.35)
                        left == 10 -> beeps(1, 0.3)
                        urgent && left % 5 == 0 -> beeps(2, 0.3)
                    }
                }
                handler.postDelayed(this, 250)
            }
        }
        tick = r
        handler.postDelayed(r, 250)
    }

    /** Someone answered (or opened the app): quiet, and no more countdown. */
    fun stop() {
        tick?.let { handler.removeCallbacks(it) }; tick = null
        handler.removeCallbacksAndMessages(null)
        track?.runCatching { stop(); release() }; track = null
        vibrator?.cancel()
        deadline.value = null
        screaming.value = false
    }

    private fun beepInto(buf: ShortArray, offset: Int) {
        val n = RATE * BEEP_MS / 1000
        val attack = RATE * 5 / 1000
        val release = RATE * 20 / 1000
        val amp = 0.95 * Short.MAX_VALUE / (1 + 0.33 + 0.14)
        for (i in 0 until n) {
            if (offset + i >= buf.size) break
            val w = 2 * PI * 2400 * (i.toDouble() / RATE)
            val env = min(1.0, min(i.toDouble() / attack, (n - i).toDouble() / release))
            buf[offset + i] = ((sin(w) + 0.33 * sin(3 * w) + 0.14 * sin(5 * w)) * amp * env).toInt().toShort()
        }
    }

    private fun play(buf: ShortArray, loop: Boolean) {
        track?.runCatching { stop(); release() }
        runCatching {
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(buf.size * 2).build()
            t.write(buf, 0, buf.size)
            if (loop) t.setLoopPoints(0, buf.size, -1)
            t.play()
            track = t
        }
    }

    /** n beeps, [gap] seconds apart. */
    private fun beeps(n: Int, gap: Double) {
        val step = (gap * RATE).toInt()
        val buf = ShortArray(step * (n - 1) + RATE * BEEP_MS / 1000 + RATE / 20)
        for (i in 0 until n) beepInto(buf, i * step)
        play(buf, loop = false)
        val pattern = LongArray(n * 2) { if (it % 2 == 0) (if (it == 0) 0L else (gap * 1000).toLong() - BEEP_MS) else BEEP_MS.toLong() * 2 }
        vibrate(pattern, -1)
    }

    /** The continuous alarm: a beep every [gap] seconds until [stop]. */
    private fun loop(gap: Double) {
        val buf = ShortArray((gap * RATE).toInt())
        beepInto(buf, 0)
        play(buf, loop = true)
        vibrate(longArrayOf(0, 400, 300), 0)
    }

    private fun vibrate(pattern: LongArray, repeat: Int) = runCatching {
        if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, repeat)) else @Suppress("DEPRECATION") vibrator?.vibrate(pattern, repeat)
    }
}
