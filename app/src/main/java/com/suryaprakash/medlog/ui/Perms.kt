package com.suryaprakash.medlog.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/** Plain-language permission list (plan 12, 16). Each has one "Allow" button. */
object Perms {
    data class P(val id: String, val title: String, val why: String, val perms: Array<String>, val required: Boolean)

    fun has(ctx: Context, vararg p: String) = p.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    val MIC = arrayOf(Manifest.permission.RECORD_AUDIO)
    val NOTIFY = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
    val SMS = arrayOf(Manifest.permission.SEND_SMS)
    val CALL = arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
    val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val NEARBY: Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION)
        Build.VERSION.SDK_INT >= 31 -> arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
        else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val BLE: Array<String> = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val CALENDAR = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun list(role: String) = if (role == "helper") listOf(
        P("notify", "Let this phone alert you", "So you hear when someone needs you.", NOTIFY, true),
        P("nearby", "Find the phone nearby", "So messages arrive without internet, over Bluetooth.", NEARBY, true),
    ) else listOf(
        P("mic", "Let this phone hear you", "So you can talk instead of typing. Your voice stays on this phone.", MIC, true),
        P("notify", "Let this phone remind you", "For medicine times and check-ins.", NOTIFY, true),
        P("sms", "Send help messages", "So your helpers get a text in an SOS. Uses SMS, not internet.", SMS, true),
        P("call", "Call your helpers", "So your helpers can be called in an SOS.", CALL, true),
        P("location", "Share where you are in an SOS", "Only sent in an SOS message, only to your helpers.", LOCATION, false),
        P("nearby", "Reach phones in your home", "So your family's phones ring when you tap a help message.", NEARBY, false),
    )

    fun exactAlarmsOk(ctx: Context) = Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun fullScreenOk(ctx: Context) = Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    fun batteryOk(ctx: Context) = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun openExactAlarms(ctx: Context) { if (Build.VERSION.SDK_INT >= 31) open(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) }
    fun openFullScreen(ctx: Context) { if (Build.VERSION.SDK_INT >= 34) open(ctx, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))) }
    @android.annotation.SuppressLint("BatteryLife")
    fun openBattery(ctx: Context) = open(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
    fun openAppSettings(ctx: Context) = open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))

    fun open(ctx: Context, i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { openAppSettings(ctx) } }

    /**
     * Phone makers that stop background apps (plan 16). Opens their "auto-start" screen where one exists.
     */
    fun makerGuide(): Pair<String, List<Intent>>? {
        val m = Build.MANUFACTURER.lowercase()
        fun c(pkg: String, cls: String) = Intent().setClassName(pkg, cls)
        return when {
            "xiaomi" in m || "redmi" in m || "poco" in m -> "On Xiaomi phones: turn on Autostart for MedLog, and set Battery saver to No restrictions." to
                listOf(c("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            "oppo" in m || "realme" in m || "oneplus" in m -> "On this phone: allow MedLog to Auto launch, and allow background activity." to
                listOf(c("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"), c("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"))
            "vivo" in m || "iqoo" in m -> "On Vivo phones: allow MedLog to Auto start and allow High background power use." to
                listOf(c("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"), c("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
            "huawei" in m || "honor" in m -> "On this phone: in App launch, set MedLog to Manage manually and turn everything on." to
                listOf(c("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
            "samsung" in m -> "On Samsung phones: in Battery, set MedLog to Unrestricted, and remove it from Sleeping apps." to emptyList()
            else -> null
        }
    }
}

/** The words people see in Android's permission screen for each permission. */
private fun permWord(p: String) = when (p) {
    Manifest.permission.SEND_SMS -> "SMS"
    Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE -> "Phone"
    Manifest.permission.RECORD_AUDIO -> "Microphone"
    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION -> "Location"
    Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR -> "Calendar"
    "android.permission.POST_NOTIFICATIONS" -> "Notifications"
    else -> "Nearby devices"
}

private tailrec fun Context.activity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Asks Android for [perms]. When Android doesn't even show its question (the person said "Don't ask again" before,
 * or, on Android 13 and newer, SMS and Phone are "restricted" for apps installed from a file), it opens this app's
 * settings with plain steps instead of silently doing nothing. Coming back to the app checks again, so a card
 * asking for a permission goes away as soon as it's allowed.
 */
@Composable
fun rememberPermissionAsker(onResult: (Boolean) -> Unit): (Array<String>) -> Unit {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val result = androidx.compose.runtime.rememberUpdatedState(onResult)
    var last by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Array<String>?>(null) }
    var askedAt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var blocked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<String>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val denied = r.filterValues { !it }.keys
        val act = ctx.activity()
        // no question was shown: the answer came back at once, and Android wouldn't explain it either
        val silent = System.currentTimeMillis() - askedAt < 700 && denied.none { act?.shouldShowRequestPermissionRationale(it) == true }
        if (denied.isNotEmpty() && silent) blocked = denied.map(::permWord).distinct()
        result.value(denied.isEmpty())
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) last?.let { p -> result.value(Perms.has(ctx, *p)) }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    if (blocked.isNotEmpty()) androidx.compose.ui.window.Dialog(onDismissRequest = { blocked = emptyList() }) {
        val names = blocked.joinToString(" and ")
        Card {
            Title("Allow it in Settings")
            Body("Android didn't show the question here, so please turn it on in Settings:")
            Body("1. Tap Open Settings.\n2. Tap Permissions, then $names, then Allow.", bold = true)
            Hint("If $names is greyed out: tap ⋮ at the top right of that screen, then Allow restricted settings, and try again.")
            BigButton("Open Settings", onClick = { blocked = emptyList(); Perms.openAppSettings(ctx) })
            BigButton("Not now", tone = Tone.SECONDARY, onClick = { blocked = emptyList() })
        }
    }
    return { perms ->
        if (perms.isEmpty()) result.value(true)
        else { last = perms; askedAt = System.currentTimeMillis(); launcher.launch(perms) }
    }
}

/** True while all of [perms] are allowed; checked again each time the app comes back to the front. */
@Composable
fun rememberAllowed(vararg perms: String): Boolean {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var ok by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Perms.has(ctx, *perms)) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) ok = Perms.has(ctx, *perms) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return ok
}
