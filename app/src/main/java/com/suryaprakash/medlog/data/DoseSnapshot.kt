package com.suryaprakash.medlog.data

import org.json.JSONObject

object DoseSnapshot {
    fun encode(m: Medicine): String = JSONObject().put("name", m.name).put("amount", m.amount)
        .put("strength", m.strength).put("purpose", m.purpose).toString()
    fun medicine(d: Dose, current: Medicine): Medicine = runCatching {
        val o = JSONObject(d.snapshot)
        current.copy(name = o.optString("name", current.name), amount = o.optString("amount", current.amount),
            strength = o.optString("strength", current.strength), purpose = o.optString("purpose", current.purpose))
    }.getOrDefault(current)
}
