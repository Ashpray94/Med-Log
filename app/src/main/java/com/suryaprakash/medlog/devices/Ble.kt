package com.suryaprakash.medlog.devices

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.suryaprakash.medlog.nlu.Reading
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID
import kotlin.math.pow

/**
 * Bluetooth health devices using the standard Bluetooth health profiles (plan phase 3):
 * blood pressure (0x1810), thermometer (0x1809), pulse oximeter (0x1822) and weight scale (0x181D).
 * Readings arrive over Bluetooth only; nothing goes to the internet. Glucose meters (0x1808) need a
 * record-access handshake that varies by brand; they are listed but read by typing for now.
 */
object Ble {
    private fun u(short: Int) = UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", short))
    val BP = u(0x1810); val BP_MEAS = u(0x2A35)
    val THERMO = u(0x1809); val TEMP_MEAS = u(0x2A1C)
    val PLX = u(0x1822); val PLX_SPOT = u(0x2A5E); val PLX_CONT = u(0x2A5F)
    val WEIGHT = u(0x181D); val WEIGHT_MEAS = u(0x2A9D)
    val GLUCOSE = u(0x1808)
    private val CCC = u(0x2902)

    data class Found(val address: String, val name: String, val kind: String)

    val found = MutableStateFlow<List<Found>>(emptyList())
    val status = MutableStateFlow("")
    val readings = MutableStateFlow<List<Reading>>(emptyList())

    private var scanning: ScanCallback? = null
    private var gatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    fun scan(ctx: Context) {
        val bt = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (bt == null || !bt.isEnabled) { status.value = "Bluetooth is off. Turn it on and try again."; return }
        val scanner = bt.bluetoothLeScanner ?: return
        stop(ctx)
        found.value = emptyList()
        status.value = "Looking… Turn on your machine now."
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, r: ScanResult) {
                val uuids = r.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
                val kind = when { BP in uuids -> "Blood pressure"; THERMO in uuids -> "Thermometer"; PLX in uuids -> "Oximeter"; WEIGHT in uuids -> "Weighing scale"; GLUCOSE in uuids -> "Sugar meter"; else -> return }
                val f = Found(r.device.address, r.device.name ?: r.scanRecord?.deviceName ?: kind, kind)
                if (found.value.none { it.address == f.address }) found.value = found.value + f
            }
        }
        val filters = listOf(BP, THERMO, PLX, WEIGHT, GLUCOSE).map { ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build() }
        scanner.startScan(filters, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
        scanning = cb
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ stopScan(ctx); if (found.value.isEmpty()) status.value = "No machine found. Make sure it is on and close by." }, 20_000)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan(ctx: Context) {
        scanning?.let { runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(it) } }
        scanning = null
    }

    @SuppressLint("MissingPermission")
    fun connect(ctx: Context, address: String, onReading: (Reading) -> Unit) {
        stopScan(ctx)
        val dev: BluetoothDevice = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address) ?: return
        status.value = "Connecting… Take your reading now."
        val cb = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, s: Int, state: Int) {
                if (state == BluetoothProfile.STATE_CONNECTED) g.discoverServices() else if (state == BluetoothProfile.STATE_DISCONNECTED) { status.value = "Disconnected."; g.close() }
            }
            override fun onServicesDiscovered(g: BluetoothGatt, s: Int) {
                val chars = listOf(BP to BP_MEAS, THERMO to TEMP_MEAS, PLX to PLX_SPOT, PLX to PLX_CONT, WEIGHT to WEIGHT_MEAS)
                    .mapNotNull { (svc, ch) -> g.getService(svc)?.getCharacteristic(ch) }
                enableNext(g, chars.toMutableList())
                status.value = "Connected. Take your reading."
            }
            private fun enableNext(g: BluetoothGatt, left: MutableList<BluetoothGattCharacteristic>) {
                val c = left.removeFirstOrNull() ?: return
                g.setCharacteristicNotification(c, true)
                val d = c.getDescriptor(CCC) ?: return enableNext(g, left)
                val value = if (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                pendingEnable = left
                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, value) else { @Suppress("DEPRECATION") run { d.value = value; g.writeDescriptor(d) } }
            }
            var pendingEnable: MutableList<BluetoothGattCharacteristic> = mutableListOf()
            override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, s: Int) { enableNext(g, pendingEnable) }
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) { @Suppress("DEPRECATION") handle(c.uuid, c.value) }
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) { handle(c.uuid, value) }
            private fun handle(id: UUID, v: ByteArray) {
                val r = parse(id, v) ?: return
                readings.value = readings.value + r
                status.value = "Got it: ${r.label()}"
                android.os.Handler(android.os.Looper.getMainLooper()).post { onReading(r) }
            }
        }
        gatt = if (Build.VERSION.SDK_INT >= 23) dev.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE) else dev.connectGatt(ctx, false, cb)
    }

    @SuppressLint("MissingPermission")
    fun stop(ctx: Context) { stopScan(ctx); runCatching { gatt?.disconnect(); gatt?.close() }; gatt = null }

    /** IEEE-11073 16-bit SFLOAT. */
    fun sfloat(lo: Int, hi: Int): Double {
        val raw = (hi shl 8) or lo
        var mantissa = raw and 0x0FFF
        var exp = raw shr 12
        if (mantissa >= 0x0800) mantissa -= 0x1000
        if (exp >= 0x08) exp -= 0x10
        return mantissa * 10.0.pow(exp)
    }

    /** IEEE-11073 32-bit FLOAT. */
    fun float32(b: ByteArray, o: Int): Double {
        var mantissa = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or ((b[o + 2].toInt() and 0xff) shl 16)
        if (mantissa >= 0x800000) mantissa -= 0x1000000
        val exp = b[o + 3].toInt()
        return mantissa * 10.0.pow(exp)
    }

    fun parse(id: UUID, v: ByteArray): Reading? = runCatching {
        fun b(i: Int) = v[i].toInt() and 0xff
        when (id) {
            BP_MEAS -> {
                val flags = b(0)
                val sys = sfloat(b(1), b(2)); val dia = sfloat(b(3), b(4))
                val kpa = flags and 1 != 0
                val k = if (kpa) 7.50062 else 1.0
                Reading("bp", Math.round(sys * k).toDouble(), Math.round(dia * k).toDouble(), "mmHg")
            }
            TEMP_MEAS -> {
                val flags = b(0)
                val t = float32(v, 1)
                val f = if (flags and 1 != 0) t else t * 9 / 5 + 32
                Reading("temp", Math.round(f * 10) / 10.0, unit = "°F")
            }
            PLX_SPOT, PLX_CONT -> Reading("spo2", sfloat(b(1), b(2)), unit = "%")
            WEIGHT_MEAS -> {
                val flags = b(0)
                val raw = b(1) or (b(2) shl 8)
                val kg = if (flags and 1 != 0) raw * 0.01 * 0.453592 else raw * 0.005
                Reading("weight", Math.round(kg * 10) / 10.0, unit = "kg")
            }
            else -> null
        }
    }.getOrNull()
}
