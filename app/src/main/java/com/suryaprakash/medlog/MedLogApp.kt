package com.suryaprakash.medlog

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.suryaprakash.medlog.clinical.Catalogue
import com.suryaprakash.medlog.clinical.Describe
import com.suryaprakash.medlog.data.MedDb
import com.suryaprakash.medlog.data.Repo
import com.suryaprakash.medlog.data.SettingsStore
import com.suryaprakash.medlog.nlu.Parser
import com.suryaprakash.medlog.speech.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll

class MedLogApp : Application() {
    /** Work that must finish even if the screen closes (saving, scheduling). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings by lazy { SettingsStore(this) }
    val catalogue by lazy { Catalogue.parse(assets.open("clinical/catalogue.json").bufferedReader().use { it.readText() }) }
    val parser by lazy { Parser(catalogue) }
    val describe by lazy { Describe(catalogue) }
    val db by lazy { MedDb.open(this) }
    val repo by lazy { Repo(db, catalogue, describe) }
    val speaker by lazy { Speaker(this) { settings.value.speechRate } }
    override fun onCreate() {
        super.onCreate()
        app = this
        com.suryaprakash.medlog.speech.I18n.use(this, settings.value.languages.firstOrNull() ?: "en-IN")
        scope.launch { settings.flow.collect { com.suryaprakash.medlog.speech.I18n.use(this@MedLogApp, it.languages.firstOrNull() ?: "en-IN"); refreshWidgets() } }
        channels()
        speaker.init()
        com.suryaprakash.medlog.data.Sync.init(this)
        scope.launch {
            runCatching { com.suryaprakash.medlog.help.Nearby.startListening(this@MedLogApp) }
            runCatching { Updater.dailyCheck(this@MedLogApp) }
            catalogue
            runCatching { repo.purgeRemoved() }
            runCatching { com.suryaprakash.medlog.meds.Scheduler.reschedule(this@MedLogApp) }
            runCatching { cleanOldAudio() }
            refreshWidgets()
        }
    }

    /** Redraws the home-screen widget after anything it shows has changed. */
    fun refreshWidgets() {
        scope.launch {
            runCatching { com.suryaprakash.medlog.widget.MedLogWidget().updateAll(this@MedLogApp) }
        }
    }

    private fun cleanOldAudio() {
        val keep = settings.value.keepAudioDays
        if (keep <= 0) return
        val cutoff = System.currentTimeMillis() - keep * 24 * 3600_000L
        java.io.File(filesDir, "audio").listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun channels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        val alarm = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannel(NotificationChannel(CH_DOSE, "Medicine reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings at medicine time, even on silent"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarm)
            enableVibration(true); vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        })
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Help alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "SOS, help messages and missed-medicine alerts"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarm)
            enableVibration(true); vibrationPattern = longArrayOf(0, 800, 200, 800, 200, 800, 200, 800)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        })
        nm.createNotificationChannel(NotificationChannel(CH_CARE, "Check-in and gentle reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Good-morning check-in, water, refills, doctor visits"
        })
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "Running in background", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while MedLog listens for helpers or watches for falls"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_QUICK, "Quick buttons", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Always-there Tell and Help buttons"
            setShowBadge(false)
        })
    }

    companion object {
        const val CH_DOSE = "dose"
        const val CH_ALERT = "alert"
        const val CH_CARE = "care"
        const val CH_SERVICE = "service"
        const val CH_QUICK = "quick"
        lateinit var app: MedLogApp
            private set
    }
}

val Context.medlog: MedLogApp get() = applicationContext as MedLogApp
