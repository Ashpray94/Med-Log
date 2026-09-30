package com.suryaprakash.medlog.help

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.screens.ChatAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles reply actions from alert notifications. */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val notifId = intent.getIntExtra("notifId", 0)
        val inboxId = intent.getLongExtra("inboxId", 0)

        // Cancel the notification
        NotificationManagerCompat.from(ctx).cancel(notifId)

        // Check for RemoteInput reply text
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        if (remoteInput != null) {
            val replyText = remoteInput.getCharSequence("reply_text")?.toString() ?: ""
            if (replyText.isNotEmpty()) {
                ChatAdapter.send(ctx, "patient", replyText)
                ctx.medlog.scope.launch {
                    if (inboxId > 0) ctx.medlog.db.inbox().ack(inboxId)
                }
            }
            return
        }

        val reply = intent.getStringExtra("reply") ?: return

        when (reply) {
            "coming", "5min", "call", "cant" -> {
                AlertSound.stop()
                Alerts.answered(AlertActivity.helperType("HELPER"), reply)
                ctx.medlog.scope.launch {
                    if (inboxId > 0) ctx.medlog.db.inbox().ack(inboxId)
                    Nearby.reply(ctx, reply)
                }
            }
            "seen" -> {
                ctx.medlog.scope.launch {
                    if (inboxId > 0) ctx.medlog.db.inbox().ack(inboxId)
                }
            }
        }
    }
}
