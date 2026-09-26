package com.suryaprakash.medlog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.suryaprakash.medlog.help.FallService
import com.suryaprakash.medlog.help.Nearby
import com.suryaprakash.medlog.meds.Scheduler
import com.suryaprakash.medlog.widget.QuickNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** After a restart, an update or a clock/time-zone change: re-arm every reminder and background helper (plan 16). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) return   // the encrypted database opens after unlock
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Scheduler.reschedule(ctx)
                FallService.sync(ctx)
                Nearby.startListening(ctx)
                QuickNotification.sync(ctx)
            } finally { pending.finish() }
        }
    }
}
