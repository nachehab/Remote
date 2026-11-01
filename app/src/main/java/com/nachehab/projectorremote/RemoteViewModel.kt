package com.nachehab.projectorremote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nachehab.projectorremote.bt.BluetoothHidRemote
import com.nachehab.projectorremote.bt.PairedDevice
import com.nachehab.projectorremote.data.Prefs
import com.nachehab.projectorremote.model.RemoteKey
import com.nachehab.projectorremote.wifi.AdbRemote
import com.nachehab.projectorremote.wifi.InstalledApp
import com.nachehab.projectorremote.wifi.NetworkScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val repeatJobs = mutableMapOf<RemoteKey, Job>()
    private var bluetoothStarted = false
    private var autoConnectTried = false

    init {
        if (_mode.value == Mode.WIFI) autoConnectWifi()
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

    fun openProjectorSettings() {
        viewModelScope.launch { wifi.openSettings() }
    }

    // ------------------------------------------------------------ Keys & text

    fun onKeyDown(key: RemoteKey) {
        when (_mode.value) {
            Mode.BLUETOOTH -> {
                if (!bluetooth.state.value.connected) {
                    _toast.value = "Bluetooth remote is not connected"
                    return
                }
                bluetooth.keyDown(key)
            }
            Mode.WIFI -> {
                if (!wifi.state.value.connected) {
                    _toast.value = "Wi-Fi remote is not connected"
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
            Mode.BLUETOOTH -> bluetooth.keyUp(key)
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
    }
}
