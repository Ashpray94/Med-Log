package com.suryaprakash.medlog.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.devices.Ble
import com.suryaprakash.medlog.help.Alerts
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Perms
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.rememberPermissionAsker
import com.suryaprakash.medlog.ui.savedFeedback
import kotlinx.coroutines.launch

@Composable
fun DevicesScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val found by Ble.found.collectAsState()
    val status by Ble.status.collectAsState()
    var allowed by remember { mutableStateOf(Perms.has(ctx, *Perms.BLE)) }
    val ask = rememberPermissionAsker { allowed = Perms.has(ctx, *Perms.BLE) }
    DisposableEffect(Unit) { onDispose { Ble.stop(ctx) } }
    Screen("BP and sugar machines", "Connect a Bluetooth blood pressure machine, thermometer, oximeter or scale. Readings are saved by themselves.", onHome = { nav.home() }, onBack = { nav.back() }) {
        Hint("Works with machines that use the standard Bluetooth health profiles. Readings come over Bluetooth only.")
        if (!allowed) { BigButton("Allow Bluetooth", onClick = { ask(Perms.BLE) }); return@Screen }
        BigButton("Find my machine", icon = Icons.Rounded.Bluetooth, onClick = { Ble.scan(ctx) })
        if (status.isNotBlank()) Card(color = p.brandSoft) { Body(status, bold = true) }
        found.forEach { f ->
            BigButton("${f.kind}: ${f.name}", tone = Tone.SECONDARY, onClick = {
                Ble.connect(ctx, f.address) { r ->
                    scope.launch {
                        app.repo.addReading(r, "From ${f.name}")
                        savedFeedback(ctx)
                        val t = DangerRules.evaluate(null, emptyMap(), listOf(r), emptyList(), app.repo.person())
                        app.speaker.say("Saved. ${r.label()}. " + if (t.level != Level.GREEN) t.say else "")
                        if (t.level == Level.RED) Alerts.dangerToHelpers(ctx, r.label(), t)
                    }
                }
            })
        }
        if (found.any { it.kind == "Sugar meter" }) Hint("Sugar meters can't be read automatically yet. Please type the number in BP, sugar & more.")
    }
}
