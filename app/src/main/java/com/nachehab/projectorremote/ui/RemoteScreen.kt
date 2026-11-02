@file:Suppress("DEPRECATION") // Some Filled icons have AutoMirrored replacements; the classic ones are fine here.

package com.nachehab.projectorremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachehab.projectorremote.Mode
import com.nachehab.projectorremote.RemoteViewModel
import com.nachehab.projectorremote.ScanState
import com.nachehab.projectorremote.bt.BluetoothState
import com.nachehab.projectorremote.bt.KeyVariants
import com.nachehab.projectorremote.bt.PairedDevice
import com.nachehab.projectorremote.model.RemoteKey
import com.nachehab.projectorremote.wifi.InstalledApp
import com.nachehab.projectorremote.wifi.WifiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    onRequestBluetoothPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onToast: (String) -> Unit,
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val bt by viewModel.bluetooth.state.collectAsStateWithLifecycle()
    val wifi by viewModel.wifi.state.collectAsStateWithLifecycle()
    val paired by viewModel.paired.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val variants by viewModel.variants.collectAsStateWithLifecycle()
    val needsRepair by viewModel.needsRepair.collectAsStateWithLifecycle()
    val waking by viewModel.waking.collectAsStateWithLifecycle()
    val powerBusy by viewModel.powerBusy.collectAsStateWithLifecycle()

    LaunchedEffect(toast) {
        toast?.let {
            onToast(it)
            viewModel.consumeToast()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Goaiey Remote", fontWeight = FontWeight.SemiBold) })
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ModeTabs(mode = mode, onSelect = viewModel::setMode)

            when (mode) {
                Mode.BLUETOOTH -> BluetoothCard(
                    state = bt,
                    paired = paired,
                    needsRepair = needsRepair,
                    waking = waking,
                    onRepairDone = viewModel::dismissRepairNotice,
                    onGrantPermission = onRequestBluetoothPermission,
                    onEnable = onEnableBluetooth,
                    onDiscoverable = onMakeDiscoverable,
                    onRefresh = viewModel::refreshPaired,
                    onConnect = viewModel::connectBluetooth,
                    onDisconnect = viewModel::disconnectBluetooth,
                )
                Mode.WIFI -> WifiCard(
                    state = wifi,
                    scan = scan,
                    initialHost = viewModel.savedHost,
                    onConnect = { host -> viewModel.connectWifi(host, viewModel.savedPort) },
                    onDisconnect = viewModel::disconnectWifi,
                    onScan = viewModel::scanNetwork,
                )
            }

            RemotePad(
                onDown = viewModel::onKeyDown,
                onUp = viewModel::onKeyUp,
            )

            TextInputCard(
                onSend = viewModel::sendText,
                onDown = viewModel::onKeyDown,
                onUp = viewModel::onKeyUp,
            )

            if (mode == Mode.BLUETOOTH) {
                ButtonSetupCard(
                    selected = variants,
                    onSelect = viewModel::selectVariant,
                )
                TouchpadCard(
                    enabled = bt.connected,
                    onMove = viewModel::mouseMove,
                    onScroll = viewModel::mouseScroll,
                    onClick = viewModel::mouseClick,
                )
            } else if (wifi.connected) {
                WifiPowerCard(
                    busy = powerBusy,
                    onToggle = viewModel::wifiTogglePower,
                    onShutDown = viewModel::wifiShutDown,
                )
                AppsCard(
                    apps = apps,
                    onLaunch = viewModel::launchApp,
                    onRefresh = viewModel::loadApps,
                    onSettings = viewModel::openProjectorSettings,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeTabs(mode: Mode, onSelect: (Mode) -> Unit) {
    TabRow(selectedTabIndex = mode.ordinal) {
        Tab(
            selected = mode == Mode.BLUETOOTH,
            onClick = { onSelect(Mode.BLUETOOTH) },
            text = { Text("Bluetooth") },
            icon = { Icon(Icons.Filled.Bluetooth, contentDescription = null) },
        )
        Tab(
            selected = mode == Mode.WIFI,
            onClick = { onSelect(Mode.WIFI) },
            text = { Text("Wi-Fi") },
            icon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
        )
    }
}

// ---------------------------------------------------------------- connection cards

@Composable
private fun StatusDot(on: Boolean, busy: Boolean = false) {
    val color = when {
        on -> Color(0xFF4CAF50)
        busy -> Color(0xFFFFC107)
        else -> Color(0xFF9E9E9E)
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun HelpSection(lines: List<String>) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(if (open) "Hide setup help" else "How to set up") }
    if (open) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEachIndexed { i, line ->
                Text("${i + 1}. $line", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BluetoothCard(
    state: BluetoothState,
    paired: List<PairedDevice>,
    needsRepair: Boolean,
    waking: Boolean,
    onRepairDone: () -> Unit,
    onGrantPermission: () -> Unit,
    onEnable: () -> Unit,
    onDiscoverable: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(on = state.connected, busy = state.connecting)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        !state.supported -> "Bluetooth remote not supported"
                        !state.enabled -> "Bluetooth is off"
                        state.connected -> "Connected to ${state.connectedName}"
                        state.connecting -> "Connecting…"
                        else -> "Not connected"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            state.message?.takeIf { !state.connected }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            if (waking) {
                Text("Waking projector…", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (!state.connected && state.enabled) {
                Text(
                    "Projector in standby? Press Power to reconnect and wake it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (needsRepair) {
                Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pair again to enable new buttons", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "This update adds new Power / Wake keys. The projector only learns them when " +
                                "pairing: remove this phone in the projector's Bluetooth list, remove the " +
                                "projector in the phone's Bluetooth settings, then pair again.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onRepairDone) { Text("I've paired again") }
                    }
                }
            }

            when {
                !state.supported -> Unit
                !state.enabled -> Button(onClick = onEnable) { Text("Turn on Bluetooth") }
                state.connected -> OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                else -> {
                    if (!state.registered) {
                        OutlinedButton(onClick = onGrantPermission) { Text("Grant Bluetooth permission") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDiscoverable) { Text("Make phone visible") }
                        OutlinedButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh")
                        }
                    }
                    if (paired.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Paired devices", style = MaterialTheme.typography.labelLarge)
                        paired.forEach { device ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(device.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(device.address, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(
                                    onClick = { onConnect(device.address) },
                                    enabled = state.registered && !state.connecting,
                                ) { Text("Connect") }
                            }
                        }
                    }
                }
            }

            HelpSection(
                listOf(
                    "Keep this app open on the Bluetooth tab and tap \"Make phone visible\".",
                    "On the projector open Settings → Bluetooth (or Remotes & Accessories) → Search / Add accessory.",
                    "Select this phone in the list and confirm pairing on both screens.",
                    "The phone now works as a Bluetooth remote, keyboard and mouse. Next time just tap Connect.",
                    "If the projector already paired with this phone before, remove it on both sides and pair again.",
                )
            )
        }
    }
}

@Composable
private fun WifiCard(
    state: WifiState,
    scan: ScanState,
    initialHost: String,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit,
) {
    var host by rememberSaveable { mutableStateOf(initialHost) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(on = state.connected, busy = state.connecting)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        state.connected -> "Connected to ${state.deviceName ?: state.host}"
                        state.connecting -> "Connecting…"
                        else -> "Not connected"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            state.message?.takeIf { !state.connected }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            if (state.connected) {
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            } else {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Projector IP address") },
                    placeholder = { Text("e.g. 192.168.1.50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onConnect(host) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onConnect(host) }, enabled = !state.connecting) { Text("Connect") }
                    OutlinedButton(onClick = onScan, enabled = !scan.scanning) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (scan.scanning) "Scanning…" else "Find projector")
                    }
                }
                if (scan.scanning) {
                    LinearProgressIndicator(progress = { scan.progress }, modifier = Modifier.fillMaxWidth())
                }
                scan.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                scan.results.forEach { ip ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(ip, Modifier.weight(1f))
                        TextButton(onClick = {
                            host = ip
                            onConnect(ip)
                        }) { Text("Use") }
                    }
                }
            }

            HelpSection(
                listOf(
                    "Connect the phone and the projector to the same Wi-Fi network.",
                    "On the projector open Settings → About (Device info) and tap \"Build number\" 7 times to unlock Developer options.",
                    "In Developer options turn on \"USB debugging\" and, if present, \"Network/ADB debugging\" (Wi-Fi ADB, port 5555).",
                    "Find the projector's IP in Settings → Network, or tap \"Find projector\".",
                    "Tap Connect, then on the projector tick \"Always allow from this computer\" and press OK.",
                )
            )
        }
    }
}

// ---------------------------------------------------------------- remote pad

@Composable
private fun RemoteButton(
    key: RemoteKey,
    onDown: (RemoteKey) -> Unit,
    onUp: (RemoteKey) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: Shape = CircleShape,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (pressed) container.copy(alpha = 0.55f) else container)
            .pointerInput(key) {
                detectTapGestures(onPress = {
                    pressed = true
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    down(key)
                    try {
                        awaitRelease()
                    } finally {
                        pressed = false
                        up(key)
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun IconKey(
    key: RemoteKey,
    icon: ImageVector,
    label: String,
    onDown: (RemoteKey) -> Unit,
    onUp: (RemoteKey) -> Unit,
    size: Dp = 56.dp,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    RemoteButton(key, onDown, onUp, size = size, container = container) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}

@Composable
private fun RemotePad(onDown: (RemoteKey) -> Unit, onUp: (RemoteKey) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconKey(
                    RemoteKey.POWER, Icons.Filled.PowerSettingsNew, "Power", onDown, onUp,
                    container = Color(0xFFC62828), tint = Color.White,
                )
                IconKey(RemoteKey.MUTE, Icons.Filled.VolumeOff, "Mute", onDown, onUp)
                IconKey(RemoteKey.SETTINGS, Icons.Filled.Settings, "Settings", onDown, onUp)
                IconKey(RemoteKey.MENU, Icons.Filled.Menu, "Menu", onDown, onUp)
            }

            DPad(onDown, onUp)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconKey(RemoteKey.BACK, Icons.Filled.ArrowBack, "Back", onDown, onUp, size = 64.dp)
                IconKey(RemoteKey.HOME, Icons.Filled.Home, "Home", onDown, onUp, size = 64.dp)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteButton(
                    RemoteKey.VOLUME_DOWN, onDown, onUp,
                    size = 64.dp, shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Filled.VolumeDown, contentDescription = "Volume down") }
                Text("VOL", style = MaterialTheme.typography.labelLarge)
                RemoteButton(
                    RemoteKey.VOLUME_UP, onDown, onUp,
                    size = 64.dp, shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Filled.VolumeUp, contentDescription = "Volume up") }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconKey(RemoteKey.PREVIOUS, Icons.Filled.SkipPrevious, "Previous", onDown, onUp, size = 48.dp)
                IconKey(RemoteKey.REWIND, Icons.Filled.FastRewind, "Rewind", onDown, onUp, size = 48.dp)
                IconKey(RemoteKey.PLAY_PAUSE, Icons.Filled.PlayArrow, "Play / pause", onDown, onUp, size = 48.dp)
                IconKey(RemoteKey.FAST_FORWARD, Icons.Filled.FastForward, "Fast forward", onDown, onUp, size = 48.dp)
                IconKey(RemoteKey.NEXT, Icons.Filled.SkipNext, "Next", onDown, onUp, size = 48.dp)
            }
        }
    }
}

@Composable
private fun DPad(onDown: (RemoteKey) -> Unit, onUp: (RemoteKey) -> Unit) {
    val ring = MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier.size(232.dp).clip(CircleShape).background(ring.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
            IconKey(RemoteKey.UP, Icons.Filled.KeyboardArrowUp, "Up", onDown, onUp, size = 64.dp, container = ring)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) {
            IconKey(RemoteKey.DOWN, Icons.Filled.KeyboardArrowDown, "Down", onDown, onUp, size = 64.dp, container = ring)
        }
        Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
            IconKey(RemoteKey.LEFT, Icons.Filled.KeyboardArrowLeft, "Left", onDown, onUp, size = 64.dp, container = ring)
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
            IconKey(RemoteKey.RIGHT, Icons.Filled.KeyboardArrowRight, "Right", onDown, onUp, size = 64.dp, container = ring)
        }
        RemoteButton(
            RemoteKey.OK, onDown, onUp,
            size = 84.dp,
            container = MaterialTheme.colorScheme.primary,
        ) {
            Text("OK", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------- text, touchpad, apps

@Composable
private fun TextInputCard(
    onSend: (String) -> Unit,
    onDown: (RemoteKey) -> Unit,
    onUp: (RemoteKey) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Keyboard", style = MaterialTheme.typography.titleMedium)
            Text(
                "Select a search box on the projector, then type here.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Type text to send") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        onSend(text)
                        text = ""
                    }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    onSend(text)
                    text = ""
                }) { Icon(Icons.Filled.Send, contentDescription = "Send text") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RemoteButton(RemoteKey.DELETE, onDown, onUp, size = 48.dp, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.Backspace, contentDescription = "Backspace")
                }
                RemoteButton(RemoteKey.ENTER, onDown, onUp, size = 48.dp, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.KeyboardReturn, contentDescription = "Enter")
                }
            }
        }
    }
}

@Composable
private fun TouchpadCard(
    enabled: Boolean,
    onMove: (Int, Int) -> Unit,
    onScroll: (Int) -> Unit,
    onClick: () -> Unit,
) {
    val move by rememberUpdatedState(onMove)
    val scroll by rememberUpdatedState(onScroll)
    val click by rememberUpdatedState(onClick)
    val remainder = remember { FloatArray(3) }
    val pad = MaterialTheme.colorScheme.surfaceVariant

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Mouse, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Touchpad", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                if (enabled) "Drag to move the pointer, tap to click, use the right strip to scroll."
                else "Connect over Bluetooth to use the touchpad.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(pad)
                        .pointerInput(enabled) {
                            if (!enabled) return@pointerInput
                            detectDragGestures { change, drag ->
                                change.consume()
                                val fx = drag.x * SENSITIVITY + remainder[0]
                                val fy = drag.y * SENSITIVITY + remainder[1]
                                val dx = fx.toInt()
                                val dy = fy.toInt()
                                remainder[0] = fx - dx
                                remainder[1] = fy - dy
                                if (dx != 0 || dy != 0) move(dx, dy)
                            }
                        }
                        .pointerInput(enabled) {
                            if (!enabled) return@pointerInput
                            detectTapGestures(onTap = { click() })
                        },
                )
                Box(
                    Modifier
                        .width(44.dp)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(pad.copy(alpha = 0.6f))
                        .pointerInput(enabled) {
                            if (!enabled) return@pointerInput
                            detectVerticalDragGestures { change, dy ->
                                change.consume()
                                val f = -dy / SCROLL_STEP_PX + remainder[2]
                                val steps = f.toInt()
                                remainder[2] = f - steps
                                if (steps != 0) scroll(steps)
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun ButtonSetupCard(
    selected: Map<RemoteKey, String>,
    onSelect: (RemoteKey, String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Button setup", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (open) "Hide" else "Show", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "If Power, Mute or Settings do nothing, try another option. Tapping an option sends it " +
                    "right away; keep the one the projector reacts to.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (open) {
                KeyVariants.options.forEach { (key, variants) ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (key) {
                            RemoteKey.POWER -> "Power"
                            RemoteKey.MUTE -> "Mute"
                            RemoteKey.SETTINGS -> "Settings"
                            RemoteKey.WAKE -> "Wake from standby (sent after reconnecting)"
                            else -> key.name
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    variants.forEach { variant ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(key, variant.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected[key] == variant.id,
                                onClick = { onSelect(key, variant.id) },
                            )
                            Text(variant.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiPowerCard(
    busy: Boolean,
    onToggle: () -> Unit,
    onShutDown: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Power", style = MaterialTheme.typography.titleMedium)
            Text(
                "Standby / Wake asks the projector's own power manager to sleep or wake and checks " +
                    "that it worked. Waking only works while the projector keeps Wi-Fi on in standby.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggle, enabled = !busy) {
                    Text(if (busy) "Working…" else "Standby / Wake")
                }
                OutlinedButton(onClick = { confirm = true }, enabled = !busy) { Text("Shut down") }
            }
            if (confirm) {
                Text(
                    "Shut down fully? You will need the projector's own remote or power button to turn it on again.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { confirm = false; onShutDown() }) { Text("Shut down") }
                    TextButton(onClick = { confirm = false }) { Text("Cancel") }
                }
            }
        }
    }
}

private const val SENSITIVITY = 0.8f
private const val SCROLL_STEP_PX = 24f

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppsCard(
    apps: List<InstalledApp>,
    onLaunch: (String) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Apps, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Apps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Open projector settings")
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload apps")
                }
            }
            if (apps.isEmpty()) {
                Text("No apps loaded yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    apps.forEach { app ->
                        AssistChip(onClick = { onLaunch(app.packageName) }, label = { Text(app.label) })
                    }
                }
            }
        }
    }
}
