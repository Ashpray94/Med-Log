package com.suryaprakash.medlog.help

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.suryaprakash.medlog.MedLogApp
import com.suryaprakash.medlog.R
import com.suryaprakash.medlog.medlog
import kotlin.math.sqrt

/**
 * Optional fall detection (plan 13.4). Looks for the pattern of a fall on the phone's motion sensor:
 * a short free-fall, a hard impact, then stillness. Then it asks "Did you fall?" for 60 seconds before
 * starting an SOS. It can be wrong both ways, so it is off by default and never replaces SOS.
 */
class FallService : Service(), SensorEventListener {
    private var sm: SensorManager? = null
    private var freeFallStart = 0L
    private var freeFallAt = 0L
    private var impactAt = 0L
    private var stillSamples = 0
    private var totalSamples = 0
    private var lastAlert = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = NotificationCompat.Builder(this, MedLogApp.CH_SERVICE).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(com.suryaprakash.medlog.ui.tr("Watching for falls")).setContentText(com.suryaprakash.medlog.ui.tr("You'll be asked if you're OK after a fall."))
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        runCatching { ServiceCompat.startForeground(this, 43, n, type) }.onFailure { stopSelf(); return START_NOT_STICKY }
        sm = getSystemService(SensorManager::class.java)
        sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } ?: stopSelf()
        return START_STICKY
    }

    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()
        when {
            impactAt > 0 -> {
                // watch for stillness for 8 s after the impact
                totalSamples++
                if (kotlin.math.abs(g - 1f) < 0.15f) stillSamples++
                if (now - impactAt > 8000) {
                    if (totalSamples > 50 && stillSamples > totalSamples * 0.8 && now - lastAlert > 60_000) {
                        lastAlert = now
                        startActivity(Intent(this, AlertActivity::class.java).putExtra(AlertActivity.MODE, AlertActivity.FALL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    impactAt = 0; freeFallAt = 0
                }
            }
            g < 0.45f -> { if (freeFallStart == 0L) freeFallStart = now; if (now - freeFallStart >= 60) freeFallAt = now }
            else -> {
                freeFallStart = 0
                if (freeFallAt > 0 && now - freeFallAt < 1000 && g > 2.5f) { impactAt = now; stillSamples = 0; totalSamples = 0 }
                else if (freeFallAt > 0 && now - freeFallAt >= 1000) freeFallAt = 0
            }
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onDestroy() { sm?.unregisterListener(this); super.onDestroy() }

    companion object {
        fun sync(ctx: Context) {
            val on = ctx.medlog.settings.value.fallDetection && ctx.medlog.settings.value.role == "self"
            val i = Intent(ctx, FallService::class.java)
            if (on) runCatching { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i) } else ctx.stopService(i)
        }
    }
}
