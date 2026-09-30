package com.suryaprakash.medlog.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Detects phone shake (~2.7g acceleration) with 1s debounce. Disabled during SOS. */
class ShakeDetector(private val ctx: Context) : SensorEventListener {
    private val sensorManager = ctx.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastShakeTime = 0L
    private val SHAKE_THRESHOLD = 2.7f * 9.81f // ~2.7g in m/s²
    private val DEBOUNCE_MS = 1000L

    private val _shakeEvent = MutableStateFlow<Unit?>(null)
    val shakeEvent: StateFlow<Unit?> = _shakeEvent

    fun start() {
        if (accelerometer != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val (x, y, z) = event.values
        val acceleration = kotlin.math.sqrt(x*x + y*y + z*z)

        val now = System.currentTimeMillis()
        if (acceleration > SHAKE_THRESHOLD && now - lastShakeTime > DEBOUNCE_MS) {
            lastShakeTime = now
            _shakeEvent.value = Unit
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}

/** Settings: is shake-to-feedback enabled? */
fun shakeFeedbackEnabled(ctx: Context): Boolean {
    return ctx.getSharedPreferences("medlog_settings", Context.MODE_PRIVATE)
        .getBoolean("shakeFeedback", true)
}
