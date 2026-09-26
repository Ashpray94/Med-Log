package com.suryaprakash.medlog.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/** App settings. Nothing medical lives here; that is all in the encrypted database. */
data class Settings(
    val onboarded: Boolean = false,
    /** "self" = the person being cared for; "helper" = a family member's / carer's phone */
    val role: String = "self",
    // ── Easy mode (plan 4.3) ──
    val easyMode: Boolean = true,
    val bigMode: Boolean = false,
    val highContrast: Boolean = false,
    val steadyTouch: Boolean = false,
    val touchToHear: Boolean = false,
    /** read each screen out loud when it opens (asked in setup; off unless the person wants it) */
    val autoRead: Boolean = false,
    /** show the Read aloud button at all */
    val readAloud: Boolean = true,
    val speechRate: Float = 1.0f,
    val leftHand: Boolean = false,
    val lessMotion: Boolean = false,
    val flashAlerts: Boolean = false,
    val boldText: Boolean = false,
    val bilingual: String = "",
    /** features the helper hid: "food", "readings", "reports", "doctor", "meds", "help" */
    val hidden: Set<String> = emptySet(),
    // ── pictogram figure ──
    val figure: Int = 0,     // 0 older woman, 1 older man, 2 woman, 3 man
    val skin: Int = 1,       // 0..3 light → dark
    // ── care ──
    val emergencyNumber: String = "108",
    val country: String = "IN",
    val checkInEnabled: Boolean = false,
    val checkInTime: String = "10:00",
    val fallDetection: Boolean = false,
    val keepAudioDays: Int = 30,
    val waterGoal: Int = 8,
    val diabetic: Boolean = false,
    val helperPin: String = "",
    val appLock: Boolean = false,
    // ── SOS ──
    val sosCountdown: Int = 10,
    val whatsappSos: Boolean = false,
    val whatsappGroupLink: String = "",
    val sosCallTimeoutSec: Int = 25,
    // ── reminders ──
    val snoozeMinutes: Int = 10,
    val escalateMinutes: Int = 30,
    val escalateCriticalMinutes: Int = 15,
    val useMeetingTimer: Boolean = false,
    val calendarId: Long = -1,
    val calendarNeutralTitles: Boolean = true,
    val persistentNotification: Boolean = false,
    val weeklySummary: Boolean = true,
    val nearbyListening: Boolean = true,
    /** reach helper phones over the internet too, through a relay that only sees sealed notes (plan 13.2) */
    val internetLink: Boolean = true,
    /** "" = the public relay ([com.suryaprakash.medlog.help.Relay.DEFAULT_URL]); or the family's own */
    val relayUrl: String = "",
    val pairedWith: String = "",      // on a helper phone: name of the person they help
    /** the help messages the person chose to show ("key|text"); custom ones are "custom_<n>|text" */
    val messages: List<String> = emptyList(),
    /** languages the person speaks, first = main (BCP-47, e.g. "ta-IN") */
    val languages: List<String> = listOf("en-IN"),
    /** "auto": phone's on-device recogniser when it can and a non-English language is chosen; "medlog": bundled English model */
    val voiceEngine: String = "auto",
    /** let the phone's speech service use the internet when it has no offline pack for a language */
    val voiceOnline: Boolean = true,
) {
    companion object {
        /** Ready-made messages the person can choose from (plan 13.2). */
        val MESSAGE_OPTIONS = listOf(
            "please_come|Please come",
            "call|Please call me",
            "water|I need water",
            "bathroom|Help with bathroom",
            "medicine|Bring my medicine",
            "unwell|I don't feel well",
            "hungry|I'm hungry",
            "walk|Help me walk",
            "bed|Help me to bed",
            "cold|I feel cold",
            "sit|Come and sit with me",
            "phone|Help with my phone or TV",
            "ok|I'm OK, don't worry",
        )
    }
}

class SettingsStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("medlog_settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<Settings> = _flow
    val value: Settings get() = _flow.value

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            onboarded = p.getBoolean("onboarded", d.onboarded),
            role = p.getString("role", d.role)!!,
            easyMode = p.getBoolean("easyMode", d.easyMode),
            bigMode = p.getBoolean("bigMode", d.bigMode),
            highContrast = p.getBoolean("highContrast", d.highContrast),
            steadyTouch = p.getBoolean("steadyTouch", d.steadyTouch),
            touchToHear = p.getBoolean("touchToHear", d.touchToHear),
            autoRead = p.getBoolean("autoRead2", d.autoRead),
            readAloud = p.getBoolean("readAloud", d.readAloud),
            speechRate = p.getFloat("speechRate2", d.speechRate),
            leftHand = p.getBoolean("leftHand", d.leftHand),
            lessMotion = p.getBoolean("lessMotion", d.lessMotion),
            flashAlerts = p.getBoolean("flashAlerts", d.flashAlerts),
            boldText = p.getBoolean("boldText", d.boldText),
            bilingual = p.getString("bilingual", d.bilingual)!!,
            hidden = p.getStringSet("hidden", d.hidden)!!.toSet(),
            figure = p.getInt("figure", d.figure),
            skin = p.getInt("skin", d.skin),
            emergencyNumber = p.getString("emergencyNumber", d.emergencyNumber)!!,
            country = p.getString("country", d.country)!!,
            checkInEnabled = p.getBoolean("checkInEnabled", d.checkInEnabled),
            checkInTime = p.getString("checkInTime", d.checkInTime)!!,
            fallDetection = p.getBoolean("fallDetection", d.fallDetection),
            keepAudioDays = p.getInt("keepAudioDays", d.keepAudioDays),
            waterGoal = p.getInt("waterGoal", d.waterGoal),
            diabetic = p.getBoolean("diabetic", d.diabetic),
            helperPin = p.getString("helperPin", d.helperPin)!!,
            appLock = p.getBoolean("appLock", d.appLock),
            sosCountdown = p.getInt("sosCountdown", d.sosCountdown),
            whatsappSos = p.getBoolean("whatsappSos", d.whatsappSos),
            whatsappGroupLink = p.getString("whatsappGroupLink", d.whatsappGroupLink)!!,
            sosCallTimeoutSec = p.getInt("sosCallTimeoutSec", d.sosCallTimeoutSec),
            snoozeMinutes = p.getInt("snoozeMinutes", d.snoozeMinutes),
            escalateMinutes = p.getInt("escalateMinutes", d.escalateMinutes),
            escalateCriticalMinutes = p.getInt("escalateCriticalMinutes", d.escalateCriticalMinutes),
            useMeetingTimer = p.getBoolean("useMeetingTimer", d.useMeetingTimer),
            calendarId = p.getLong("calendarId", d.calendarId),
            calendarNeutralTitles = p.getBoolean("calendarNeutralTitles", d.calendarNeutralTitles),
            persistentNotification = p.getBoolean("persistentNotification", d.persistentNotification),
            weeklySummary = p.getBoolean("weeklySummary", d.weeklySummary),
            nearbyListening = p.getBoolean("nearbyListening", d.nearbyListening),
            internetLink = p.getBoolean("internetLink", d.internetLink),
            relayUrl = p.getString("relayUrl", d.relayUrl)!!,
            pairedWith = p.getString("pairedWith", d.pairedWith)!!,
            languages = p.getString("languages", null)?.split(",")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: d.languages,
            voiceEngine = p.getString("voiceEngine", d.voiceEngine)!!,
            voiceOnline = p.getBoolean("voiceOnline", d.voiceOnline),
            messages = p.getString("messages", null)?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map { a.getString(it) } } } ?: d.messages,
        )
    }

    fun update(f: (Settings) -> Settings) {
        val s = f(value)
        p.edit().apply {
            putBoolean("onboarded", s.onboarded); putString("role", s.role)
            putBoolean("easyMode", s.easyMode); putBoolean("bigMode", s.bigMode); putBoolean("highContrast", s.highContrast)
            putBoolean("steadyTouch", s.steadyTouch); putBoolean("touchToHear", s.touchToHear); putBoolean("autoRead2", s.autoRead); putBoolean("readAloud", s.readAloud)
            putFloat("speechRate2", s.speechRate); putBoolean("leftHand", s.leftHand); putBoolean("lessMotion", s.lessMotion)
            putBoolean("flashAlerts", s.flashAlerts); putBoolean("boldText", s.boldText); putString("bilingual", s.bilingual)
            putStringSet("hidden", s.hidden); putInt("figure", s.figure); putInt("skin", s.skin)
            putString("emergencyNumber", s.emergencyNumber); putString("country", s.country)
            putBoolean("checkInEnabled", s.checkInEnabled); putString("checkInTime", s.checkInTime)
            putBoolean("fallDetection", s.fallDetection); putInt("keepAudioDays", s.keepAudioDays); putInt("waterGoal", s.waterGoal)
            putBoolean("diabetic", s.diabetic); putString("helperPin", s.helperPin); putBoolean("appLock", s.appLock)
            putInt("sosCountdown", s.sosCountdown); putBoolean("whatsappSos", s.whatsappSos); putString("whatsappGroupLink", s.whatsappGroupLink)
            putInt("sosCallTimeoutSec", s.sosCallTimeoutSec); putInt("snoozeMinutes", s.snoozeMinutes)
            putInt("escalateMinutes", s.escalateMinutes); putInt("escalateCriticalMinutes", s.escalateCriticalMinutes)
            putBoolean("useMeetingTimer", s.useMeetingTimer); putLong("calendarId", s.calendarId)
            putBoolean("calendarNeutralTitles", s.calendarNeutralTitles); putBoolean("persistentNotification", s.persistentNotification)
            putBoolean("weeklySummary", s.weeklySummary); putBoolean("nearbyListening", s.nearbyListening)
            putString("pairedWith", s.pairedWith)
            putBoolean("internetLink", s.internetLink); putString("relayUrl", s.relayUrl)
            putString("messages", JSONArray(s.messages).toString())
            putString("languages", s.languages.joinToString(",")); putString("voiceEngine", s.voiceEngine); putBoolean("voiceOnline", s.voiceOnline)
        }.apply()
        _flow.value = s
    }

    /** Every stored setting, for the setup file. */
    fun all(): Map<String, Any?> = p.all

    /** Writes values from a setup file and reloads. Types must match what [update] writes. */
    fun putAll(values: Map<String, Any>) {
        p.edit().apply {
            for ((k, v) in values) when (v) {
                is Boolean -> putBoolean(k, v); is Int -> putInt(k, v); is Long -> putLong(k, v); is Float -> putFloat(k, v)
                is Double -> putFloat(k, v.toFloat()); is String -> putString(k, v)
                is Set<*> -> putStringSet(k, v.map { it.toString() }.toSet())
            }
        }.commit()
        _flow.value = read()
    }

    /** Small non-medical counters (widget order day, last weekly summary...). */
    fun getLong(key: String, def: Long = 0) = p.getLong("x_$key", def)
    fun putLong(key: String, v: Long) = p.edit().putLong("x_$key", v).apply()
    fun getString(key: String): String? = p.getString("x_$key", null)
    fun putString(key: String, v: String?) = p.edit().putString("x_$key", v).apply()
}
