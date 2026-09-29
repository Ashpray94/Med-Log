package com.suryaprakash.medlog.integration

import android.content.Context
import android.content.Intent
import com.suryaprakash.medlog.data.Keys
import com.suryaprakash.medlog.medlog

/**
 * medlog:// links can be opened by any app or web page (the Assistant needs that for "tell"). The links that DO something
 * (call a helper, start SOS, send a help message) only act when MedLog itself made the link: it carries a secret kept on
 * this phone. Anything else just opens the page.
 */
object TrustedLinks {
    const val EXTRA = "medlog_link_secret"

    /** The secret for this phone: made once, kept in the settings. */
    fun secret(ctx: Context): String {
        val s = ctx.medlog.settings
        s.getString("link_secret")?.takeIf { it.length >= 16 }?.let { return it }
        return Keys.randomB64(24).also { s.putString("link_secret", it) }
    }

    /** Marks an intent MedLog made itself (widget, notification) as trusted. */
    fun trust(intent: Intent, ctx: Context): Intent = intent.putExtra(EXTRA, secret(ctx))

    fun isTrusted(intent: Intent?, ctx: Context): Boolean {
        val got = intent?.getStringExtra(EXTRA) ?: return false
        return got == secret(ctx)
    }
}

/** What a medlog:// link is allowed to do. Kept free of Android so it can be tested. */
object LinkPolicy {
    const val CALL = "call"; const val SOS = "sos"; const val SEND = "send"
    const val PAGE_EMERGENCY = "page:emergency"; const val PAGE_HELP = "page:help"; const val NORMAL = "normal"

    fun linkAction(host: String, hasSend: Boolean, trusted: Boolean): String = when (host) {
        "call" -> if (trusted) CALL else PAGE_EMERGENCY
        "sos" -> if (trusted) SOS else PAGE_EMERGENCY
        "help" -> if (hasSend && trusted) SEND else PAGE_HELP
        else -> NORMAL
    }
}
