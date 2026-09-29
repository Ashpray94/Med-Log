package com.suryaprakash.medlog

import com.suryaprakash.medlog.sync.SyncEngine
import com.suryaprakash.medlog.sync.SyncSender
import java.util.Random

/**
 * A stand-in for the relay mailbox. Messages are kept with the time they arrived and handed to every other online phone.
 * It can drop messages, hold them back, and forget old ones (the real relay keeps notes 12 hours).
 */
class FakeRelay(private val retentionMs: Long = 12 * 3600_000L, private val random: Random = Random(1)) {
    private class Msg(val id: Int, val at: Long, val from: String, val text: String, val heldUntil: Long)

    var now = 0L
    var dropRate = 0.0
    var holdMs = 0L
    private val log = ArrayList<Msg>()
    private val engines = LinkedHashMap<String, SyncEngine>()
    private val online = HashSet<String>()
    private val delivered = HashMap<String, HashSet<Int>>()
    private var nextId = 0
    var sent = 0
        private set

    fun join(device: String, engine: SyncEngine) { engines[device] = engine; online += device; delivered.getOrPut(device) { HashSet() } }
    fun sender(device: String) = SyncSender { publish(device, it) }
    fun setOnline(device: String, on: Boolean) { if (on) online += device else online -= device }

    fun publish(from: String, text: String) {
        sent++
        if (dropRate > 0 && random.nextDouble() < dropRate) return
        log += Msg(nextId++, now, from, text, now + holdMs)
    }

    fun advance(ms: Long) { now += ms; expire() }

    /** Forgets messages older than the retention time. */
    fun expire() { log.removeAll { now - it.at > retentionMs } }

    /** Delivers until nothing more is waiting. Offline phones get their share when they come back online and pump again. */
    fun pump(maxRounds: Int = 1000) {
        var rounds = 0
        while (rounds++ < maxRounds) {
            var any = false
            for (m in ArrayList(log)) {
                if (m.heldUntil > now) continue
                for ((dev, eng) in engines) {
                    if (dev == m.from || dev !in online) continue
                    if (delivered.getValue(dev).add(m.id)) { eng.onMessage(m.text); any = true }
                }
            }
            if (!any) return
        }
        error("relay never went quiet")
    }
}
