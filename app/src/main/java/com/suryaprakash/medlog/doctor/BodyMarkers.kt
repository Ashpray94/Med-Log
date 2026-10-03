package com.suryaprakash.medlog.doctor

import kotlin.math.hypot

object BodyMarkers {
    data class Mark(val number: Int, val back: Boolean, val x: Float, val y: Float, val labelX: Float, val labelY: Float, val all: Boolean)
    fun layout(pins: List<Pair<Int, String>>): List<Mark> {
        val out = mutableListOf<Mark>()
        for ((number, code) in pins.distinct()) {
            val all = code.endsWith(":all")
            val xy = if (all) listOf(50f, 62f) else code.substringAfter(":").split(",").mapNotNull { it.toFloatOrNull() }
            if (xy.size != 2 || xy.any { !it.isFinite() } || xy[0] !in 0f..100f || xy[1] !in 0f..170f) continue
            val back = code.startsWith("back")
            val candidates = (0..6).flatMap { ring -> (0 until 16).map { k ->
                val a = k * Math.PI / 8
                (xy[0] + kotlin.math.cos(a).toFloat() * ring * 12).coerceIn(10f, 90f) to
                    (xy[1] + kotlin.math.sin(a).toFloat() * ring * 12).coerceIn(10f, 160f)
            } }
            val point = candidates.firstOrNull { (x,y) -> out.filter { it.back == back }.all { hypot(x-it.labelX,y-it.labelY) >= 21f } }
                ?: (xy[0] to xy[1])
            out += Mark(number, back, xy[0], xy[1], point.first, point.second, all)
        }
        return out
    }
}
