package com.suryaprakash.medlog.help

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.Perms

/** Phone calls and SMS. These use the mobile network, never the internet. */
object Calls {
    /** Calls straight away when allowed; otherwise opens the dialer with the number ready. */
    fun call(ctx: Context, number: String, speaker: Boolean = false) {
        val clean = number.filter { it.isDigit() || it == '+' }
        if (clean.isEmpty()) return
        val direct = Perms.has(ctx, Manifest.permission.CALL_PHONE)
        val i = Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$clean")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }.onFailure { Log.w("Calls", "call failed", it) }
        if (speaker && direct) Speakerphone.turnOnSoon(ctx)
    }

    /** Opens the dialer with the number ready; the person presses call themselves. */
    fun dial(ctx: Context, number: String) {
        val clean = number.filter { it.isDigit() || it == '+' }
        if (clean.isEmpty()) return
        runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { Log.w("Calls", "dial failed", it) }
    }

    /** For buttons on pages: while another person's MedLog is open, only the dialer opens (a helper calls the doctor themselves, nothing rings on its own). */
    fun ui(ctx: Context, number: String, speaker: Boolean = false) =
        if (ctx.medlog.viewing.active) dial(ctx, number) else call(ctx, number, speaker)

    /** Sends a text. Returns false if SMS isn't allowed or the phone can't send. */
    fun sms(ctx: Context, number: String, text: String): Boolean {
        if (!Perms.has(ctx, Manifest.permission.SEND_SMS)) return false
        val clean = number.filter { it.isDigit() || it == '+' }
        if (clean.isEmpty()) return false
        return runCatching {
            val sm: SmsManager = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = sm.divideMessage(text)
            if (parts.size > 1) sm.sendMultipartTextMessage(clean, null, parts, null, null) else sm.sendTextMessage(clean, null, text, null, null)
            true
        }.getOrElse { Log.w("Calls", "sms failed", it); false }
    }
}

/** Turns on the loudspeaker a moment after an SOS call connects, so the person needn't hold the phone. */
object Speakerphone {
    fun turnOnSoon(ctx: Context) {
        val am = ctx.getSystemService(android.media.AudioManager::class.java) ?: return
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        for (delay in listOf(1500L, 3500L, 7000L)) h.postDelayed({
            runCatching {
                if (Build.VERSION.SDK_INT >= 31) {
                    am.availableCommunicationDevices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let { am.setCommunicationDevice(it) }
                } else {
                    am.mode = android.media.AudioManager.MODE_IN_CALL
                    @Suppress("DEPRECATION") am.isSpeakerphoneOn = true
                }
            }
        }, delay)
    }
}
