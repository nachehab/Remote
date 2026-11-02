package com.nachehab.projectorremote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nachehab.projectorremote.bt.BluetoothHidRemote
import com.nachehab.projectorremote.bt.HidDescriptor
import com.nachehab.projectorremote.bt.KeyVariant
import com.nachehab.projectorremote.bt.KeyVariants
import com.nachehab.projectorremote.model.HidUsage
import com.nachehab.projectorremote.bt.PairedDevice
import com.nachehab.projectorremote.data.Prefs
import com.nachehab.projectorremote.model.RemoteKey
import com.nachehab.projectorremote.wifi.AdbRemote
import com.nachehab.projectorremote.wifi.InstalledApp
import com.nachehab.projectorremote.wifi.NetworkScanner
import com.nachehab.projectorremote.wifi.PowerResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class Mode { BLUETOOTH, WIFI }

data class ScanState(
    val scanning: Boolean = false,
    val progress: Float = 0f,
    val results: List<String> = emptyList(),
    val message: String? = null,
)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    val bluetooth = BluetoothHidRemote(app, prefs)
    val wifi = AdbRemote(app)
    private val scanner = NetworkScanner(app)

    private val _mode = MutableStateFlow(
        runCatching { Mode.valueOf(prefs.lastMode) }.getOrDefault(Mode.BLUETOOTH)
    )
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _paired = MutableStateFlow<List<PairedDevice>>(emptyList())
    val paired: StateFlow<List<PairedDevice>> = _paired.asStateFlow()

    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    val savedHost: String get() = prefs.lastHost
    val savedPort: Int get() = prefs.lastPort

    private val _variants = MutableStateFlow(
        KeyVariants.configurableKeys.associateWith { key ->
            KeyVariants.find(key, prefs.keyVariant(key.name))!!.id
        }
    )
    /** Selected Bluetooth encoding (variant id) for Power / Mute / Settings / Wake. */
    val variants: StateFlow<Map<RemoteKey, String>> = _variants.asStateFlow()

    private val _needsRepair = MutableStateFlow(
        prefs.lastBluetoothAddress != null && prefs.pairedDescriptorVersion < HidDescriptor.VERSION
    )
    /** True when the projector was paired with an older HID descriptor and must pair again. */
    val needsRepair: StateFlow<Boolean> = _needsRepair.asStateFlow()

    private val _waking = MutableStateFlow(false)
    val waking: StateFlow<Boolean> = _waking.asStateFlow()

    private val repeatJobs = mutableMapOf<RemoteKey, Job>()
    private val heldBtUsages = mutableMapOf<RemoteKey, HidUsage>()
    private var bluetoothStarted = false
    private var autoConnectTried = false

    init {
        if (_mode.value == Mode.WIFI) autoConnectWifi()
        viewModelScope.launch {
            // A fresh pairing made with the current descriptor needs no re-pair notice later.
            bluetooth.state.collect {
                if (it.connected && !_needsRepair.value) prefs.pairedDescriptorVersion = HidDescriptor.VERSION
            }
        }
    }

    fun setMode(mode: Mode) {
        _mode.value = mode
        prefs.lastMode = mode.name
        if (mode == Mode.WIFI) autoConnectWifi()
    }

    fun consumeToast() {
        _toast.value = null
    }

    // ------------------------------------------------------------ Bluetooth

    fun onBluetoothPermissionsGranted() {
        if (!bluetoothStarted) {
            bluetooth.start()
            bluetoothStarted = true
        }
        refreshPaired()
    }

    fun refreshPaired() {
        _paired.value = bluetooth.pairedDevices()
    }

    fun connectBluetooth(address: String) = bluetooth.connect(address)

    fun disconnectBluetooth() = bluetooth.disconnect()

    fun dismissRepairNotice() {
        prefs.pairedDescriptorVersion = HidDescriptor.VERSION
        _needsRepair.value = false
    }

    private fun variantFor(key: RemoteKey): KeyVariant? =
        if (key in KeyVariants.options) KeyVariants.find(key, _variants.value[key]) else null

    /** Saves the chosen encoding and immediately sends it so the user can see if it works. */
    fun selectVariant(key: RemoteKey, id: String) {
        prefs.setKeyVariant(key.name, id)
        _variants.value = _variants.value + (key to id)
        if (key == RemoteKey.WAKE) {
            wakeProjector()
            return
        }
        if (!bluetooth.state.value.connected) {
            _toast.value = "Saved. Connect over Bluetooth to try it."
            return
        }
        val variant = variantFor(key) ?: return
        viewModelScope.launch { bluetooth.perform(variant) }
    }

    /**
     * The projector drops the Bluetooth link when it goes to standby. Like a hardware remote,
     * reconnect first and then send the wake signal.
     */
    fun wakeProjector() {
        if (_waking.value) return
        val address = prefs.lastBluetoothAddress
        if (address == null) {
            _toast.value = "Pair the projector over Bluetooth first"
            return
        }
        _waking.value = true
        viewModelScope.launch {
            try {
                if (!bluetooth.state.value.connected) {
                    val registered = withTimeoutOrNull(5_000) { bluetooth.state.first { it.registered } }
                    if (registered == null) {
                        _toast.value = "Bluetooth remote is not ready. Is Bluetooth on?"
                        return@launch
                    }
                    var connected = false
                    for (attempt in 1..WAKE_CONNECT_ATTEMPTS) {
                        bluetooth.connect(address)
                        connected = withTimeoutOrNull(WAKE_CONNECT_TIMEOUT_MS) {
                            bluetooth.state.first { it.connected }
                        } != null
                        if (connected) break
                    }
                    if (!connected) {
                        _toast.value = "The projector did not answer over Bluetooth. When it is fully " +
                            "off its Bluetooth is off too; use its own remote or power button."
                        return@launch
                    }
                    delay(400) // let the projector finish setting up the HID channel
                }
                variantFor(RemoteKey.WAKE)?.let { bluetooth.perform(it) }
            } finally {
                _waking.value = false
            }
        }
    }

    fun mouseMove(dx: Int, dy: Int) = bluetooth.mouseMove(dx, dy)

    fun mouseScroll(amount: Int) = bluetooth.mouseMove(0, 0, amount)

    fun mouseClick() {
        viewModelScope.launch { bluetooth.mouseClick() }
    }

    // ------------------------------------------------------------ Wi-Fi

    private fun autoConnectWifi() {
        if (autoConnectTried) return
        autoConnectTried = true
        val host = prefs.lastHost
        if (host.isNotBlank() && !wifi.state.value.connected && !wifi.state.value.connecting) {
            connectWifi(host, prefs.lastPort)
        }
    }

    fun connectWifi(hostInput: String, port: Int = AdbRemote.DEFAULT_PORT) {
        val host = hostInput.trim()
        if (host.isEmpty()) {
            _toast.value = "Enter the projector's IP address"
            return
        }
        prefs.lastHost = host
        prefs.lastPort = port
        viewModelScope.launch {
            if (wifi.connect(host, port)) loadApps()
        }
    }

    fun disconnectWifi() {
        viewModelScope.launch { wifi.disconnect() }
        _apps.value = emptyList()
    }

    fun scanNetwork() {
        if (_scan.value.scanning) return
        viewModelScope.launch {
            if (scanner.localIpv4() == null) {
                _scan.value = ScanState(message = "Not connected to Wi-Fi")
                return@launch
            }
            _scan.value = ScanState(scanning = true)
            val found = scanner.scan { p -> _scan.value = _scan.value.copy(progress = p) }
            _scan.value = ScanState(
                results = found,
                message = if (found.isEmpty())
                    "No device with network debugging found. Enable it on the projector, or enter its IP manually."
                else "Found ${found.size} device(s)",
            )
        }
    }

    fun loadApps() {
        viewModelScope.launch { _apps.value = wifi.listApps() }
    }

    fun launchApp(pkg: String) {
        viewModelScope.launch {
            runCatching { wifi.launchApp(pkg) }.onFailure { _toast.value = it.message }
        }
    }

    private val _powerBusy = MutableStateFlow(false)
    val powerBusy: StateFlow<Boolean> = _powerBusy.asStateFlow()

    private fun runPower(block: suspend () -> Unit) {
        if (_powerBusy.value) return
        _powerBusy.value = true
        viewModelScope.launch {
            try { block() } finally { _powerBusy.value = false }
        }
    }

    /** Power over Wi-Fi: the projector's own power manager switches standby on or off. */
    fun wifiTogglePower() = runPower {
        if (!wifi.ensureConnected(prefs.lastHost, prefs.lastPort)) {
            _toast.value = "Can't reach the projector over Wi-Fi. In deep standby or fully off its " +
                "Wi-Fi is off too; use its own remote or power button."
            return@runPower
        }
        _toast.value = when (wifi.togglePower()) {
            PowerResult.ASLEEP -> "Projector is in standby"
            PowerResult.AWAKE -> "Projector is on"
            PowerResult.SENT -> "Power command sent"
            PowerResult.NO_EFFECT -> "The projector ignored the power keys. Try \"Shut down\"."
            PowerResult.NOT_CONNECTED -> "Wi-Fi remote is not connected"
        }
    }

    fun wifiShutDown() = runPower {
        if (!wifi.ensureConnected(prefs.lastHost, prefs.lastPort)) {
            _toast.value = "Wi-Fi remote is not connected"
            return@runPower
        }
        wifi.shutDown()
        _toast.value = "Shutting down. Turn it back on with its own remote or power button."
    }

    fun openProjectorSettings() {
        viewModelScope.launch { wifi.openSettings() }
    }

    // ------------------------------------------------------------ Keys & text

    fun onKeyDown(key: RemoteKey) {
        when (_mode.value) {
            Mode.BLUETOOTH -> {
                if (!bluetooth.state.value.connected) {
                    if (key == RemoteKey.POWER) wakeProjector()
                    else _toast.value = "Bluetooth remote is not connected"
                    return
                }
                val variant = variantFor(key)
                if (variant == null || variant.isPlainPress) {
                    val usage = variant?.usage ?: key.hid
                    heldBtUsages[key] = usage
                    bluetooth.usageDown(usage)
                } else {
                    // Long-press / repeated variants run on their own, independent of the finger.
                    viewModelScope.launch { bluetooth.perform(variant) }
                }
            }
            Mode.WIFI -> {
                if (key == RemoteKey.POWER) {
                    wifiTogglePower()
                    return
                }
                if (!wifi.state.value.connected) {
                    _toast.value = "Wi-Fi remote is not connected"
                    return
                }
                if (key == RemoteKey.SETTINGS) {
                    // Starting the Settings activity is more reliable than KEYCODE_SETTINGS.
                    openProjectorSettings()
                    return
                }
                repeatJobs.remove(key)?.cancel()
                repeatJobs[key] = viewModelScope.launch {
                    // The first press must always reach the projector, even for very quick taps.
                    withContext(NonCancellable) { wifi.sendKey(key) }
                    if (key.repeatable) {
                        delay(REPEAT_DELAY_MS)
                        while (isActive) {
                            wifi.sendKey(key)
                            delay(REPEAT_INTERVAL_MS)
                        }
                    }
                }
            }
        }
    }

    fun onKeyUp(key: RemoteKey) {
        when (_mode.value) {
            // Android on the projector handles key repeat itself while a HID key is held.
            Mode.BLUETOOTH -> heldBtUsages.remove(key)?.let { bluetooth.usageUp(it) }
            Mode.WIFI -> repeatJobs.remove(key)?.cancel()
        }
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        viewModelScope.launch {
            when (_mode.value) {
                Mode.BLUETOOTH -> {
                    if (!bluetooth.state.value.connected) {
                        _toast.value = "Bluetooth remote is not connected"
                        return@launch
                    }
                    val skipped = bluetooth.typeText(text)
                    if (skipped > 0) _toast.value = "$skipped character(s) can't be typed over Bluetooth"
                }
                Mode.WIFI -> {
                    if (!wifi.state.value.connected) {
                        _toast.value = "Wi-Fi remote is not connected"
                        return@launch
                    }
                    wifi.sendText(text)
                }
            }
        }
    }

    override fun onCleared() {
        repeatJobs.values.forEach { it.cancel() }
        bluetooth.stop()
        wifi.close()
    }

    private companion object {
        const val REPEAT_DELAY_MS = 450L
        const val REPEAT_INTERVAL_MS = 120L
        const val WAKE_CONNECT_ATTEMPTS = 3
        const val WAKE_CONNECT_TIMEOUT_MS = 6_000L
    }
}
