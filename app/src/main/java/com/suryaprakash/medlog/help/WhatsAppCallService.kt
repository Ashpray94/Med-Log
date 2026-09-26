package com.suryaprakash.medlog.help

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional SOS extra (plan 13.3, decision D4): when MedLog has just opened the family SOS group in
 * WhatsApp during an SOS, this presses "Voice call" and confirms. It acts only inside that SOS window
 * (60 seconds), reads nothing else, and is never relied on: phone calls and SMS always follow.
 *
 * WhatsApp changes its screens from time to time, so this looks for the button by several names.
 */
class WhatsAppCallService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!armed()) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString()?.startsWith("com.whatsapp") != true) return
        // already in a call?
        if (find(root, CALL_SCREEN) != null) { started = true; pendingUntil = 0; return }
        // confirm dialog ("Start group call?" → "Call")
        find(root, CONFIRM)?.let { if (click(it)) return }
        // the call button in the chat's top bar
        find(root, CALL_BUTTON)?.let { click(it) }
    }

    private fun find(root: AccessibilityNodeInfo, names: List<String>): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var n = 0
        while (queue.isNotEmpty() && n < 600) {
            val node = queue.removeFirst(); n++
            val label = ((node.contentDescription ?: "").toString() + "|" + (node.text ?: "").toString()).lowercase()
            if (names.any { label.startsWith(it) || label.contains("|$it") || label == it }) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        return n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    override fun onInterrupt() {}

    companion object {
        private val CALL_BUTTON = listOf("voice call", "audio call", "call")
        private val CONFIRM = listOf("start call", "call now", "start voice call", "call group")
        private val CALL_SCREEN = listOf("end call", "ringing", "calling…", "calling...")

        @Volatile var pendingUntil = 0L
        @Volatile var started = false

        fun request() { started = false; pendingUntil = System.currentTimeMillis() + 60_000 }
        fun clear() { pendingUntil = 0 }
        private fun armed() = System.currentTimeMillis() < pendingUntil

        fun isEnabled(ctx: Context): Boolean {
            val on = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(ctx, WhatsAppCallService::class.java).flattenToString()
            return on.split(':').any { it.equals(me, true) }
        }
    }
}
