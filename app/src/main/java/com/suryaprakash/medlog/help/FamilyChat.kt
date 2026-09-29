package com.suryaprakash.medlog.help

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.suryaprakash.medlog.MedLogApp
import com.suryaprakash.medlog.R
import com.suryaprakash.medlog.data.InboxItem
import com.suryaprakash.medlog.data.Keys
import com.suryaprakash.medlog.medlog
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Quick messages between helpers, without the person (plan 13.1): "I'm going there now", "Can someone check
 * on Amma?". Only helper phones can read them.
 *
 * The person's phone makes one family key and gives it to each paired helper phone, sealed with that
 * helper's own pairing key (at pairing, or once afterwards for phones paired before). Helper phones then
 * share one mailbox on the relay, sealed with the family key. The person's phone never listens to it.
 * When a helper answers the person ("I'm coming"), the others are told automatically, so two people don't
 * both rush over, or both assume the other went.
 */
object FamilyChat {
    const val KIND = "FAMILY"
    private const val DIR = "family"

    // ───────────── the person's phone: hand out the key ─────────────

    /** The family key, made once on the person's phone. */
    fun familyKey(ctx: Context): String {
        val s = ctx.medlog.settings
        // this phone's own family mailbox (for its own helpers); kept apart from those of people it helps
        s.getString("own_family_key")?.let { return it }
        val old = if (s.getString("people") == null && s.value.role == "self") s.getString("family_key") else null
        return (old ?: Keys.randomB64(32)).also { s.putString("own_family_key", it) }
    }

    /** Gives the family key to every paired helper phone that doesn't have it yet. */
    suspend fun shareKey(ctx: Context) {
        val app = ctx.medlog
        if (app.settings.value.role != "self" || !Relay.enabled(ctx)) return
        val key = familyKey(ctx)
        for (h in app.ownDb.helpers().all()) {
            val pk = h.pairKey ?: continue
            val mark = "family_sent_${h.pairId}"
            if (app.settings.getString(mark) == key) continue
            val body = JSONObject().put("kind", "FAMILY_KEY").put("family", key).put("mid", "fk-${h.pairId}").put("at", System.currentTimeMillis())
            if (Relay.post(ctx, Base64.decode(pk, Base64.NO_WRAP), Relay.DOWN, body)) app.settings.putString(mark, key)
        }
    }

    // ───────────── helper phones ─────────────

    /** The family mailbox for one person this phone helps (the first, if not said). */
    fun key(ctx: Context, p: com.suryaprakash.medlog.data.CaredFor? = null) =
        (p ?: com.suryaprakash.medlog.data.People.all(ctx).firstOrNull { it.familyKey.isNotBlank() })?.familyBytes
    fun topic(ctx: Context) = key(ctx)?.let { Relay.topic(it, DIR) }
    fun myName(ctx: Context) = ctx.medlog.settings.getString("my_name").orEmpty()

    /** This phone's id, so a helper's own messages coming back from the relay aren't shown twice. */
    private fun device(ctx: Context): String {
        val s = ctx.medlog.settings
        return s.getString("device_id") ?: Keys.randomB64(9).also { s.putString("device_id", it) }
    }

    /** A helper phone received the family key from the person's phone. */
    fun gotKey(ctx: Context, o: JSONObject, pairId: String) {
        val k = o.optString("family").takeIf { it.isNotBlank() } ?: return
        val p = com.suryaprakash.medlog.data.People.byPairId(ctx, pairId) ?: return
        if (p.familyKey == k) return
        com.suryaprakash.medlog.data.People.put(ctx, p.copy(familyKey = k))
        Relay.reconnect()
    }

    /** Sends [text] to the other helpers, and keeps it in this phone's chat. */
    fun send(ctx: Context, text: String, p: com.suryaprakash.medlog.data.CaredFor? = null) {
        val app = ctx.medlog
        val key = key(ctx, p) ?: return
        val me = myName(ctx).ifBlank { "Helper" }
        app.scope.launch {
            app.ownDb.inbox().insert(InboxItem(fromName = "You", text = text, kind = KIND, acked = true))
            Relay.post(ctx, key, DIR, JSONObject().put("from", me).put("text", text).put("at", System.currentTimeMillis())
                .put("mid", Keys.randomB64(9)).put("dev", device(ctx)))
        }
    }

    /** Another helper wrote. A quiet notification, not an alarm. */
    fun received(ctx: Context, o: JSONObject) {
        if (o.optString("dev") == device(ctx)) return
        val app = ctx.medlog
        app.scope.launch {
            val id = app.ownDb.inbox().insert(InboxItem(fromName = o.optString("from", "Helper"), text = o.optString("text"), kind = KIND,
                at = o.optLong("at", System.currentTimeMillis()), acked = true))
            val pi = PendingIntent.getActivity(ctx, 6000 + (id % 500).toInt(), Intent(ctx, com.suryaprakash.medlog.MainActivity::class.java).setData(android.net.Uri.parse("medlog://helper")), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, MedLogApp.CH_ALERT).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(com.suryaprakash.medlog.ui.tr("Family") + ": " + o.optString("from", "Helper"))
                .setContentText(o.optString("text"))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(pi).setAutoCancel(true).build()
            runCatching { androidx.core.app.NotificationManagerCompat.from(ctx).notify(6000 + (id % 500).toInt(), n) }
        }
    }

    /** After a helper answers the person, the other helpers see who is going. */
    fun announceReply(ctx: Context, reply: String, about: String?, p: com.suryaprakash.medlog.data.CaredFor) {
        if (key(ctx, p) == null) return
        val who = p.name.ifBlank { "them" }
        val words = Nearby.replyWords(reply)
        send(ctx, if (about.isNullOrBlank()) "$words (answering $who)" else "$words (answering $who: \"$about\")", p)
    }
}
