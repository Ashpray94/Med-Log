package com.suryaprakash.medlog.help

import android.content.Context
import com.suryaprakash.medlog.care.HelperDose
import com.suryaprakash.medlog.data.People
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.screens.otherHelpers

/** What a tap on one of the three answers does (see [AlertReplies]). */
object AlertAnswer {
    /**
     * What a tap on one of the three answers does, from the alert screen, the notification or the home card.
     * Stops the alarm, marks the alert answered and tells the person's phone. Returns a line to show the helper, or null.
     * - "In 5 min": the alarm comes back in 5 minutes ([AlertSnooze]).
     * - "Skip this dose": the dose is marked not given, the way the helper's own "not given" does it ([HelperDose.notGiven]).
     * - "Ask another helper": the next paired helper's phone rings, and the person is told who was asked.
     */
    suspend fun perform(ctx: Context, code: String, kind: String, text: String, from: String, inboxId: Long, mid: String, pairId: String?): String? {
        val app = ctx.medlog
        Loud.done(ctx, Loud.alertId(inboxId))
        if (inboxId > 0) app.db.inbox().ack(inboxId)
        var say: String? = null
        var wire = code
        var to: String? = null
        when (code) {
            AlertReplies.IN_5 -> AlertSnooze.schedule(ctx, inboxId, from, text, kind, mid, pairId)
            AlertReplies.SKIP_DOSE -> pairOf(ctx, pairId)?.let { HelperDose.skipFromAlert(ctx, it, text) }
            AlertReplies.ASK -> {
                val p = pairOf(ctx, pairId)
                val other = p?.let { otherHelpers(ctx, it).firstOrNull() }
                if (other != null) {
                    Nearby.askOther(ctx, p, other.second, text)
                    to = other.first
                    say = "Asked ${other.first} to go"
                } else say = "No other helper is paired"
            }
        }
        if (mid.isNotEmpty()) Nearby.reply(ctx, wire, re = mid, pairId = pairId, to = to) else Nearby.reply(ctx, wire, pairId = pairId, to = to)
        return say
    }

    private fun pairOf(ctx: Context, pairId: String?): String? =
        (People.all(ctx).firstOrNull { it.pairId == pairId } ?: People.all(ctx).firstOrNull())?.pairId
}
