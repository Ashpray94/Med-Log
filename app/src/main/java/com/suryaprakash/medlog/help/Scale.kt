package com.suryaprakash.medlog.help

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.SparseArray
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/**
 * Reads a bathroom scale over Bluetooth while the weight page is open.
 *
 * OKOK-app scales (Chipsea chips) shout the weight in their Bluetooth advertisement, so no pairing is needed:
 * MedLog listens, shows the number as it settles, and reports it once the scale marks it steady.
 * Scales that follow the standard Bluetooth weight format are connected to briefly and read the same way.
 */
object Scale {
    data class Weight(val kg: Double, val steady: Boolean)

    /** The latest number from a scale; null until one is heard. */
    val live = MutableStateFlow<Weight?>(null)
    /** True while listening. */
    val listening = MutableStateFlow(false)

    private val WEIGHT_SERVICE: UUID = UUID.fromString("0000181d-0000-1000-8000-00805f9b34fb")
    private val WEIGHT_MEASUREMENT: UUID = UUID.fromString("00002a9d-0000-1000-8000-00805f9b34fb")
    private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var callback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var lastPlain: Double? = null
    private var samePlain = 0

    @SuppressLint("MissingPermission")
    fun start(ctx: Context): Boolean {
        if (callback != null) return true
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        if (!adapter.isEnabled) return false
        val scanner = adapter.bluetoothLeScanner ?: return false
        live.value = null
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, r: ScanResult) = handle(ctx, r)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { handle(ctx, it) }
        }
        callback = cb
        return runCatching {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
            listening.value = true
            true
        }.getOrElse { callback = null; false }
    }

    @SuppressLint("MissingPermission")
    fun stop(ctx: Context) {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        callback?.let { cb -> runCatching { adapter?.bluetoothLeScanner?.stopScan(cb) } }
        callback = null
        runCatching { gatt?.close() }
        gatt = null
        listening.value = false
    }

    private fun handle(ctx: Context, r: ScanResult) {
        val rec = r.scanRecord ?: return
        rec.manufacturerSpecificData?.let { m -> fromBroadcast(m)?.let { w -> publish(w); return } }
        if (rec.serviceUuids?.any { it.uuid == WEIGHT_SERVICE } == true && gatt == null) connect(ctx, r.device)
    }

    private fun publish(w: Weight) {
        if (w.kg !in 2.0..350.0) return
        val cur = live.value
        if (cur != null && cur.steady && !w.steady && kotlin.math.abs(cur.kg - w.kg) < 0.05) return
        live.value = w
    }

    // ── OKOK / Chipsea advertisements ──

    private fun u16(hi: Byte, lo: Byte) = ((hi.toInt() and 0xFF) shl 8) or (lo.toInt() and 0xFF)

    /** Reads the weight out of the known advertisement layouts; null if this isn't a scale we know. */
    private fun fromBroadcast(m: SparseArray<ByteArray>): Weight? {
        for (i in 0 until m.size()) {
            val id = m.keyAt(i); val b = m.valueAt(i) ?: continue
            when {
                // layout "20": flags at 6 (bit 0 steady, bit 2 two decimals), weight at 8-9, XOR check at 12
                id == 0x20CA && b.size == 19 -> {
                    var x = 0x20; for (k in 0 until 12) x = x xor (b[k].toInt() and 0xFF)
                    if ((b[12].toInt() and 0xFF) != (x and 0xFF)) continue
                    val flags = b[6].toInt() and 0xFF
                    return Weight(u16(b[8], b[9]) / if (flags and 0x04 != 0) 100.0 else 10.0, flags and 0x01 != 0)
                }
                // layout "11": weight at 3-4, settings at 9 (decimals in bits 1-2, unit in bits 3-4), XOR check at 16
                id == 0x11CA && b.size == 23 -> {
                    var x = 0xCA xor 0x11; for (k in 0 until 16) x = x xor (b[k].toInt() and 0xFF)
                    if ((b[16].toInt() and 0xFF) != (x and 0xFF)) continue
                    val kg = weightWithUnit(b[3], b[4], b[9].toInt() and 0xFF) ?: continue
                    return Weight(kg, true)
                }
                // layout "F0": weight at 3 (high) and 2 (low), tenths of a kilo, no steady flag
                id == 0xF0FF && b.size >= 4 -> return plainSteady(u16(b[3], b[2]) / 10.0)
                // layout "C0": weight at 0-1, settings at 6 (bit 0 steady, decimals in bits 1-2, unit in bits 3-4)
                (id and 0xFF) == 0xC0 && b.size >= 13 -> {
                    val attrib = b[6].toInt() and 0xFF
                    val kg = weightWithUnit(b[0], b[1], attrib) ?: continue
                    return Weight(kg, attrib and 0x01 != 0)
                }
            }
        }
        return null
    }

    private fun weightWithUnit(hi: Byte, lo: Byte, settings: Int): Double? {
        val div = when ((settings shr 1) and 0x3) { 1 -> 1.0; 2 -> 100.0; else -> 10.0 }
        val raw = u16(hi, lo)
        return when ((settings shr 3) and 0x3) {
            0 -> raw / div                                                            // kilograms
            1 -> raw / div / 2                                                        // jin (half kilos)
            2 -> raw / div * 0.4535924                                                // pounds
            else -> (hi.toInt() and 0xFF) * 6.350293 + (lo.toInt() and 0xFF) / div * 0.4535924   // stone and pounds
        }
    }

    /** For scales that don't say when they're steady: the same number three times in a row counts as steady. */
    private fun plainSteady(kg: Double): Weight {
        samePlain = if (lastPlain != null && kotlin.math.abs(lastPlain!! - kg) < 0.05) samePlain + 1 else 0
        lastPlain = kg
        return Weight(kg, samePlain >= 2)
    }

    // ── the standard Bluetooth weight format ──

    @SuppressLint("MissingPermission")
    private fun connect(ctx: Context, device: BluetoothDevice) {
        gatt = device.connectGatt(ctx, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
                if (state == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
                else if (state == BluetoothProfile.STATE_DISCONNECTED) { g.close(); if (gatt == g) gatt = null }
            }
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val ch = g.getService(WEIGHT_SERVICE)?.getCharacteristic(WEIGHT_MEASUREMENT) ?: return
                g.setCharacteristicNotification(ch, true)
                val d = ch.getDescriptor(CCCD) ?: return
                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                else @Suppress("DEPRECATION") run { d.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE; g.writeDescriptor(d) }
            }
            override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) = read(value)
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION") ch.value?.let { read(it) }
            }
        })
    }

    /** Flags, then the weight in 5-gram steps (or hundredths of a pound). A reading sent this way is final. */
    private fun read(v: ByteArray) {
        if (v.size < 3) return
        val imperial = v[0].toInt() and 0x01 != 0
        val raw = (v[1].toInt() and 0xFF) or ((v[2].toInt() and 0xFF) shl 8)
        publish(Weight(if (imperial) raw * 0.01 * 0.4535924 else raw * 0.005, true))
    }
}
