package com.suryaprakash.medlog.help

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.suryaprakash.medlog.MedLogApp
import com.suryaprakash.medlog.R
import com.suryaprakash.medlog.data.Helper
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.Perms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * SOS (plan 13.3). After a spoken countdown, all at once:
 *  1. SMS with location to every SOS helper (mobile network, no internet)
 *  2. alarm on nearby helper phones
 *  3. optional WhatsApp family group call
 *  4. phone calls to each helper in turn, on speaker
 *  5. then the emergency number
 * Every step is logged with its time.
 */
object Sos {
    sealed interface Phase {
        data object Idle : Phase
        data class Countdown(val seconds: Int) : Phase
        data object Messaging : Phase
        data class WhatsApp(val started: Boolean) : Phase
        data class Calling(val name: String, val index: Int, val total: Int) : Phase
        data class Answered(val name: String, val secondsLeft: Int) : Phase
        data class EmergencyCountdown(val seconds: Int, val number: String) : Phase
        data class EmergencyCalling(val number: String) : Phase
        data object HelpComing : Phase
        data object Cancelled : Phase
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase
    val log = MutableStateFlow<List<String>>(emptyList())
    val smsSentTo = MutableStateFlow<List<String>>(emptyList())

    internal fun set(p: Phase) { _phase.value = p }
    internal fun note(s: String) { log.value = log.value + "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())} $s" }

    /** Answers from the SOS screen. */
    val answer = MutableStateFlow<String?>(null)

    fun start(ctx: Context, reason: String, countdown: Boolean = true) {
        if (_phase.value !is Phase.Idle && _phase.value !is Phase.Cancelled && _phase.value !is Phase.HelpComing) { showScreen(ctx); return }
        log.value = emptyList(); smsSentTo.value = emptyList(); answer.value = null
        val i = Intent(ctx, SosService::class.java).putExtra("reason", reason).putExtra("countdown", countdown)
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        showScreen(ctx)
    }

    fun cancel(ctx: Context) { answer.value = "cancel" }
    fun helpComing() { answer.value = "coming" }
    fun next() { answer.value = "next" }

    fun showScreen(ctx: Context) {
        ctx.startActivity(Intent(ctx, AlertActivity::class.java).putExtra(AlertActivity.MODE, AlertActivity.SOS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
}

class SosService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    @Volatile private var callState = TelephonyManager.CALL_STATE_IDLE
    private var legacyListener: PhoneStateListener? = null
    private var callback: Any? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = intent?.getStringExtra("reason") ?: "SOS"
        val countdown = intent?.getBooleanExtra("countdown", true) ?: true
        val hasLoc = Perms.has(this, Manifest.permission.ACCESS_FINE_LOCATION) || Perms.has(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        val type = if (Build.VERSION.SDK_INT >= 34) (if (hasLoc) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0) or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 41, notification("SOS: getting help"), type)
        watchCalls()
        if (job?.isActive != true) job = scope.launch { run(reason, countdown); stopSelf() }
        return START_NOT_STICKY
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 41, Intent(this, AlertActivity::class.java).putExtra(AlertActivity.MODE, AlertActivity.SOS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, MedLogApp.CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(com.suryaprakash.medlog.ui.tr("MedLog SOS")).setContentText(com.suryaprakash.medlog.ui.tr(text))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true).setContentIntent(open).setFullScreenIntent(open, true).build()
    }

    /** Waits up to [sec] seconds for a tap on the SOS screen. */
    private suspend fun waitAnswer(sec: Int, onTick: (Int) -> Unit = {}): String? {
        fun take(): String? = Sos.answer.value?.also { Sos.answer.value = null }
        // "Help is coming" / "Cancel" tapped during a call is kept, not lost
        take()?.let { if (it == "coming" || it == "cancel") return it }
        for (left in sec downTo 1) {
            onTick(left)
            repeat(10) { delay(100); take()?.let { return it } }
        }
        return take()
    }

    private suspend fun run(reason: String, countdown: Boolean) {
        val app = medlog
        val s = app.settings.value
        val profile = app.repo.profile()
        val name = profile.name

        // ── countdown, spoken ──
        if (countdown) {
            app.speaker.say("Calling for help in ${s.sosCountdown} seconds. Tap Cancel to stop.")
            val a = waitAnswer(s.sosCountdown) { Sos.set(Sos.Phase.Countdown(it)) }
            if (a == "cancel") { Sos.set(Sos.Phase.Cancelled); Sos.note("Cancelled"); app.speaker.say("SOS cancelled."); return }
        }
        Sos.set(Sos.Phase.Messaging)
        Sos.note("SOS started: $reason")
        app.speaker.say("Getting help. Messaging your helpers.")
        val helpers = app.db.helpers().all().filter { it.sos }

        // ── location, taken on this phone ──
        val loc = withTimeoutOrNull(12_000) { location() } ?: lastKnown()
        val where = loc?.let { "https://maps.google.com/?q=%.5f,%.5f (within about %d m)".format(java.util.Locale.US, it.latitude, it.longitude, it.accuracy.toInt()) }
        Sos.note(if (where != null) "Location found" else "Location not available")

        // ── SMS to everyone ──
        val recent = app.db.notes().symptomsSince(System.currentTimeMillis() - 6 * 3600_000L).firstOrNull()?.text
        val text = Wording.sos(name, reason, recent, where)
        val sent = ArrayList<String>()
        for (h in helpers) if (Calls.sms(this, h.phone, text)) sent += h.name
        Sos.smsSentTo.value = sent
        Sos.note(if (sent.isEmpty()) "SMS could not be sent" else "SMS sent to ${sent.joinToString()}")
        Nearby.broadcast(this, "SOS", text)
        Sos.note("Alert sent to nearby helper phones")

        // ── optional WhatsApp family group call ──
        if (s.whatsappSos && s.whatsappGroupLink.isNotBlank() && WhatsAppCallService.isEnabled(this)) {
            Sos.set(Sos.Phase.WhatsApp(false))
            WhatsAppCallService.request()
            val open = Intent(Intent.ACTION_VIEW, Uri.parse(s.whatsappGroupLink)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { runCatching { packageManager.getPackageInfo(it, 0) }.isSuccess }?.let { open.setPackage(it) }
            runCatching { startActivity(open) }
            val started = withTimeoutOrNull(25_000) { while (!WhatsAppCallService.started) delay(250); true } ?: false
            Sos.note(if (started) "WhatsApp group call started" else "WhatsApp group call did not start")
            if (started) {
                Sos.set(Sos.Phase.WhatsApp(true))
                Sos.showScreen(this)
                val a = waitAnswer(180)
                if (a == "coming") { finishHelp(); return }
                if (a == "cancel") { Sos.set(Sos.Phase.Cancelled); return }
            }
            WhatsAppCallService.clear()
        }

        // ── call each helper in turn ──
        for ((i, h) in helpers.withIndex()) {
            Sos.set(Sos.Phase.Calling(h.name, i + 1, helpers.size))
            Sos.note("Calling ${h.name}")
            app.speaker.say("Calling ${h.name}.")
            delay(1500)
            val talked = callAndWait(h)
            Sos.showScreen(this)
            Sos.note("Call to ${h.name} ended after ${talked}s")
            val a = waitAnswer(15) { Sos.set(Sos.Phase.Answered(h.name, it)) }
            if (a == "coming") { finishHelp(); return }
            if (a == "cancel") { Sos.set(Sos.Phase.Cancelled); Sos.note("Stopped"); saveLog(reason, where); return }
        }

        // ── emergency number ──
        val num = s.emergencyNumber
        app.speaker.say("Calling $num in 20 seconds. Tap Cancel to stop.")
        val a = waitAnswer(20) { Sos.set(Sos.Phase.EmergencyCountdown(it, num)) }
        if (a == "coming") { finishHelp(); return }
        if (a != "cancel") {
            Sos.set(Sos.Phase.EmergencyCalling(num))
            Sos.note("Calling $num")
            Calls.call(this, num, speaker = true)
            delay(3000)
        } else Sos.note("Emergency call cancelled")
        Sos.set(Sos.Phase.Cancelled)
        saveLog(reason, where)
    }

    private suspend fun finishHelp() {
        Sos.set(Sos.Phase.HelpComing)
        Sos.note("Help is coming")
        medlog.speaker.say("Good. Help is coming. Stay where you are.")
        saveLog("", null)
    }

    private suspend fun saveLog(reason: String, where: String?) {
        medlog.repo.addEvent(Kind.SOS, "SOS" + if (reason.isNotBlank()) ": $reason" else "",
            JSONObject().put("log", JSONArray(Sos.log.value)).put("where", where ?: "").toString())
    }

    /** Places the call and waits until it ends. Returns seconds off-hook. */
    private suspend fun callAndWait(h: Helper): Int {
        Calls.call(this, h.phone, speaker = true)
        val canWatch = Perms.has(this, Manifest.permission.READ_PHONE_STATE)
        if (!canWatch) { delay(medlog.settings.value.sosCallTimeoutSec * 1000L + 20_000); return 0 }
        // wait for the call to start (up to 15 s), then to end (up to 10 min)
        withTimeoutOrNull(15_000) { while (callState == TelephonyManager.CALL_STATE_IDLE) delay(200) }
        val start = System.currentTimeMillis()
        withTimeoutOrNull(600_000) { while (callState != TelephonyManager.CALL_STATE_IDLE) delay(300) }
        return ((System.currentTimeMillis() - start) / 1000).toInt()
    }

    @SuppressLint("MissingPermission")
    private fun watchCalls() {
        if (!Perms.has(this, Manifest.permission.READ_PHONE_STATE)) return
        val tm = getSystemService(TelephonyManager::class.java) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener { override fun onCallStateChanged(state: Int) { callState = state } }
                tm.registerTelephonyCallback(mainExecutor, cb); callback = cb
            } else {
                @Suppress("DEPRECATION")
                val l = object : PhoneStateListener() { @Deprecated("Deprecated in Java") override fun onCallStateChanged(state: Int, number: String?) { callState = state } }
                @Suppress("DEPRECATION") tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE); legacyListener = l
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun location(): Location? = suspendCancellableCoroutine { cont ->
        val lm = getSystemService(LocationManager::class.java)
        if (lm == null || !(Perms.has(this, Manifest.permission.ACCESS_FINE_LOCATION) || Perms.has(this, Manifest.permission.ACCESS_COARSE_LOCATION))) { cont.resume(null); return@suspendCancellableCoroutine }
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) { cont.resume(null); return@suspendCancellableCoroutine }
        runCatching {
            if (Build.VERSION.SDK_INT >= 30) lm.getCurrentLocation(provider, null, mainExecutor) { if (cont.isActive) cont.resume(it) }
            else @Suppress("DEPRECATION") lm.requestSingleUpdate(provider, { if (cont.isActive) cont.resume(it) }, Looper.getMainLooper())
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? = runCatching {
        val lm = getSystemService(LocationManager::class.java)
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
    }.getOrNull()

    override fun onDestroy() {
        runCatching {
            val tm = getSystemService(TelephonyManager::class.java)
            if (Build.VERSION.SDK_INT >= 31) (callback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
            else @Suppress("DEPRECATION") legacyListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
        scope.cancel()
        super.onDestroy()
    }
}
