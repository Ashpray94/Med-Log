package com.suryaprakash.medlog.feedback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Decides whether a stream of movement readings is a shake: two hard jolts (over 2.7 g) within one second.
 * After a shake it ignores everything for 3 seconds. No Android in here, so it can be tested.
 */
class ShakeLogic(
    private val threshold: Float = 2.7f,
    private val windowMs: Long = 1000,
    private val cooldownMs: Long = 3000,
    /** one jolt shows up as several readings in a row; readings this close count as the same jolt */
    private val sameJoltMs: Long = 120,
) {
    private var firstPeak = -1L
    private var lastPeak = -1L
    private var lastShake = -1L

    /** Feed one reading (time in ms, force in g, where 1.0 is just sitting still). True when this reading completes a shake. */
    fun feed(timeMs: Long, g: Float): Boolean {
        if (lastShake >= 0 && timeMs - lastShake < cooldownMs) return false
        if (g <= threshold) return false
        if (lastPeak >= 0 && timeMs - lastPeak < sameJoltMs) { lastPeak = timeMs; return false }
        lastPeak = timeMs
        if (firstPeak >= 0 && timeMs - firstPeak <= windowMs) {
            firstPeak = -1; lastPeak = -1; lastShake = timeMs
            return true
        }
        firstPeak = timeMs
        return false
    }

    companion object {
        /** The times at which a run of (time, g) readings counts as a shake. */
        fun detect(samples: List<Pair<Long, Float>>): List<Long> {
            val l = ShakeLogic()
            return samples.filter { (t, g) -> l.feed(t, g) }.map { it.first }
        }
    }
}

/** Listens to the accelerometer while the app is on screen. Start it when resumed, stop it when paused. */
class ShakeDetector(ctx: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val sm = ctx.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var logic = ShakeLogic()
    private var running = false

    fun start() {
        if (running) return
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        logic = ShakeLogic()
        running = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!running) return
        sm?.unregisterListener(this)
        running = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / SensorManager.GRAVITY_EARTH
        if (logic.feed(e.timestamp / 1_000_000, g)) onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
