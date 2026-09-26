package com.suryaprakash.medlog.data

import android.content.Context
import android.net.Uri
import com.suryaprakash.medlog.medlog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The first-time setup, saved to a file and loaded again on a new or reset phone, so it never has to be
 * answered twice: the person's details, how MedLog looks and sounds, languages, helpers, messages and
 * reminder settings. No notes, readings or medicines history (that is what Backup is for), and no pairing
 * keys: helper phones are paired again face to face.
 */
object SetupFile {
    private const val TYPE = "medlog-setup"
    /** Settings that belong to this phone, not to the person. */
    private val PHONE_ONLY = setOf("onboarded", "role", "pairedWith", "calendarId")

    suspend fun export(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val app = ctx.medlog
        val p = app.repo.profile()
        val profile = JSONObject().put("name", p.name).put("dob", p.dob).put("sex", p.sex).put("bloodGroup", p.bloodGroup)
            .put("hospitalId", p.hospitalId).put("conditions", p.conditions).put("allergies", p.allergies)
            .put("doctorName", p.doctorName).put("doctorPhone", p.doctorPhone).put("onBloodThinner", p.onBloodThinner).put("notes", p.notes)
        val helpers = JSONArray()
        app.db.helpers().all().forEach { h ->
            helpers.put(JSONObject().put("name", h.name).put("phone", h.phone).put("relation", h.relation).put("sos", h.sos)
                .put("alerts", h.alerts).put("canSeeNotes", h.canSeeNotes))
        }
        val settings = JSONObject()
        for ((k, v) in app.settings.all()) {
            if (k in PHONE_ONLY || k.startsWith("x_")) continue
            settings.put(k, when (v) {
                is Set<*> -> JSONObject().put("set", JSONArray(v.toList()))
                is Float -> JSONObject().put("float", v.toDouble())
                is Long -> JSONObject().put("long", v)
                else -> v
            })
        }
        val o = JSONObject().put("type", TYPE).put("version", 1).put("savedAt", System.currentTimeMillis())
            .put("profile", profile).put("helpers", helpers).put("settings", settings)
        ctx.contentResolver.openOutputStream(uri, "w")!!.use { it.write(o.toString(2).toByteArray()) }
    }

    /** Loads a setup file and finishes setup. Returns the person's name. Throws if the file isn't a MedLog setup. */
    suspend fun import(ctx: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val app = ctx.medlog
        val o = JSONObject(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) })
        require(o.optString("type") == TYPE) { "Not a MedLog setup file" }
        o.optJSONObject("profile")?.let { j ->
            app.db.profile().put(app.repo.profile().copy(
                name = j.optString("name"), dob = j.optString("dob"), sex = j.optString("sex"), bloodGroup = j.optString("bloodGroup"),
                hospitalId = j.optString("hospitalId"), conditions = j.optString("conditions"), allergies = j.optString("allergies"),
                doctorName = j.optString("doctorName"), doctorPhone = j.optString("doctorPhone"), onBloodThinner = j.optBoolean("onBloodThinner"),
                notes = j.optString("notes"),
            ))
        }
        o.optJSONObject("settings")?.let { j ->
            val values = HashMap<String, Any>()
            for (k in j.keys()) {
                if (k in PHONE_ONLY || k.startsWith("x_")) continue
                values[k] = when (val v = j.get(k)) {
                    is JSONObject -> when {
                        v.has("set") -> v.getJSONArray("set").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                        v.has("float") -> v.getDouble("float").toFloat()
                        v.has("long") -> v.getLong("long")
                        else -> continue
                    }
                    else -> v
                }
            }
            app.settings.putAll(values)
        }
        val existing = app.db.helpers().all()
        o.optJSONArray("helpers")?.let { a ->
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                val phone = j.optString("phone")
                if (phone.isBlank() || existing.any { it.phone.filter(Char::isDigit).takeLast(10) == phone.filter(Char::isDigit).takeLast(10) }) continue
                app.db.helpers().insert(Helper(name = j.optString("name"), phone = phone, relation = j.optString("relation"),
                    sos = j.optBoolean("sos", true), alerts = j.optBoolean("alerts", true), canSeeNotes = j.optBoolean("canSeeNotes"), sortOrder = existing.size + i))
            }
        }
        app.settings.update { it.copy(role = "self", onboarded = true) }
        runCatching { com.suryaprakash.medlog.meds.Scheduler.reschedule(ctx) }
        app.refreshWidgets()
        app.repo.profile().name
    }
}
