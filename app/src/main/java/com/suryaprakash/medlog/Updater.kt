package com.suryaprakash.medlog

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Check for updates and install them from inside MedLog, so nobody has to find a file and fight Play Protect.
 *
 * The public Med-Log repository lists each version in a small latest.json (with its releases, and on its
 * downloads branch). MedLog reads
 * it, downloads the file for this phone, checks its SHA-256, and checks it is signed by the same key as the
 * MedLog already installed, before Android shows its own "Update?" box. A file from anyone else can't get
 * through. Turned off for copies installed from the Play Store (Play updates those itself).
 */
object Updater {
    /** Where new versions are listed: the latest GitHub release of Med-Log, and its downloads branch. The newest wins. */
    val SOURCES = listOf(
        "https://github.com/Ashpray94/Med-Log/releases/latest/download/latest.json",
        "https://raw.githubusercontent.com/Ashpray94/Med-Log/downloads/latest.json",
    )
    private const val TAG = "MedLogUpdate"

    data class Release(val code: Int, val name: String, val notes: String, val url: String, val sha256: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val percent: Int) : State
        data object Installing : State
        data class Failed(val why: String) : State
    }
    val state = MutableStateFlow<State>(State.Idle)

    /** Play Store copies are updated by Play. */
    fun allowed(ctx: Context): Boolean = runCatching {
        val installer = if (Build.VERSION.SDK_INT >= 30) ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName
        else @Suppress("DEPRECATION") ctx.packageManager.getInstallerPackageName(ctx.packageName)
        installer != "com.android.vending"
    }.getOrDefault(true)

    private fun get(url: String, timeout: Int = 15_000) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = timeout; readTimeout = timeout; useCaches = false; instanceFollowRedirects = true
    }

    /** Looks for a newer version. [quiet] leaves the screen alone when there is none (the daily check). */
    suspend fun check(ctx: Context, quiet: Boolean = false): Release? = withContext(Dispatchers.IO) {
        if (!allowed(ctx)) return@withContext null
        if (!quiet) state.value = State.Checking
        val r = SOURCES.mapNotNull { src ->
            runCatching {
                val c = get(src)
                val o = try { JSONObject(c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
                val apks = o.getJSONObject("apk")
                val abi = Build.SUPPORTED_ABIS.firstOrNull { apks.has(it) } ?: error("No file for this phone")
                val a = apks.getJSONObject(abi)
                Release(o.getInt("versionCode"), o.getString("versionName"), o.optString("notes"), a.getString("url"), a.getString("sha256").lowercase())
            }.onFailure { Log.w(TAG, "check $src", it) }.getOrNull()
        }.maxByOrNull { it.code }
        ctx.medlog.settings.putLong("update_checked", System.currentTimeMillis())
        when {
            r == null -> { if (!quiet) state.value = State.Failed("Couldn't check. Is the internet on?"); null }
            r.code > BuildConfig.VERSION_CODE -> { state.value = State.Available(r); r }
            else -> { if (!quiet) state.value = State.UpToDate; null }
        }
    }

    /** At most once a day, when MedLog opens. */
    suspend fun dailyCheck(ctx: Context) {
        if (System.currentTimeMillis() - ctx.medlog.settings.getLong("update_checked") > 20 * 3600_000L) check(ctx, quiet = true)
    }

    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** The one-time "Allow from this source" switch Android needs before an app can install updates. */
    fun openInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) runCatching {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    suspend fun install(ctx: Context, r: Release) = withContext(Dispatchers.IO) {
        val file = File(ctx.cacheDir, "update.apk").apply { delete() }
        try {
            state.value = State.Downloading(0)
            val c = get(r.url, 30_000)
            try {
                val total = c.contentLengthLong
                val md = MessageDigest.getInstance("SHA-256")
                c.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024); var done = 0L; var n: Int
                        while (input.read(buf).also { n = it } > 0) {
                            out.write(buf, 0, n); md.update(buf, 0, n); done += n
                            if (total > 0) state.value = State.Downloading((done * 100 / total).toInt())
                        }
                    }
                }
                val sum = md.digest().joinToString("") { "%02x".format(it) }
                if (sum != r.sha256) error("The download was damaged. Please try again.")
            } finally { c.disconnect() }
            if (!sameSigner(ctx, file)) error("This file isn't from MedLog, so it wasn't installed.")
            state.value = State.Installing
            val pi = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("medlog.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val status = PendingIntent.getBroadcast(ctx, 77, Intent(ctx, UpdateReceiver::class.java), flags)
                s.commit(status.intentSender)
            }
        } catch (e: Exception) {
            Log.w(TAG, "install", e)
            state.value = State.Failed(e.message ?: "The update didn't install.")
            file.delete()
        }
    }

    /** The downloaded file must be signed by the same key as this MedLog. */
    private fun sameSigner(ctx: Context, apk: File): Boolean = runCatching {
        val pm = ctx.packageManager
        if (Build.VERSION.SDK_INT >= 28) {
            val flag = PackageManager.GET_SIGNING_CERTIFICATES
            val mine = pm.getPackageInfo(ctx.packageName, flag).signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            val theirs = pm.getPackageArchiveInfo(apk.path, flag)?.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            mine.isNotEmpty() && mine == theirs
        } else {
            @Suppress("DEPRECATION") val flag = PackageManager.GET_SIGNATURES
            @Suppress("DEPRECATION") val mine = pm.getPackageInfo(ctx.packageName, flag).signatures?.map { it.toCharsString() }?.toSet().orEmpty()
            @Suppress("DEPRECATION") val theirs = pm.getPackageArchiveInfo(apk.path, flag)?.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
            mine.isNotEmpty() && mine == theirs
        }
    }.getOrDefault(false)
}

/** Android's answer about the update: show its "Update?" box, or report what went wrong. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.state.value = Updater.State.Idle
            else -> Updater.state.value = Updater.State.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { "The update didn't install ($it)." } ?: "The update didn't install.")
        }
    }
}
