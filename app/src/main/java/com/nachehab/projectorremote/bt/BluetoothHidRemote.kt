package com.nachehab.projectorremote.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.nachehab.projectorremote.data.Prefs
import com.nachehab.projectorremote.model.HidUsage
import com.nachehab.projectorremote.model.RemoteKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors

data class PairedDevice(val name: String, val address: String)

data class BluetoothState(
    val supported: Boolean = true,
    val enabled: Boolean = false,
    val registered: Boolean = false,
    val connecting: Boolean = false,
    val connectedName: String? = null,
    val connectedAddress: String? = null,
    val message: String? = null,
) {
    val connected: Boolean get() = connectedAddress != null
}

/**
 * Turns the phone into a Bluetooth HID combo device (keyboard + consumer remote + mouse)
 * using Android's BluetoothHidDevice profile. The projector pairs with the phone exactly as
 * it would with a Bluetooth remote or keyboard, so no app is needed on the projector.
 *
 * All permission checks happen in the Activity before [start] is called.
 */
@SuppressLint("MissingPermission")
class BluetoothHidRemote(
    private val context: Context,
    private val prefs: Prefs,
) {
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val executor = Executors.newSingleThreadExecutor()

    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var proxyRequested = false
    private var receiverRegistered = false

    // Current report state, so several keys can be held at once.
    private val pressedKeys = LinkedHashSet<Int>()
    private var modifiers = 0
    private var mouseButtons = 0
    private var systemBits = 0

    private val _state = MutableStateFlow(BluetoothState(supported = adapter != null))
    val state: StateFlow<BluetoothState> = _state.asStateFlow()

    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    _state.update { it.copy(enabled = true) }
                    requestProxy()
                }
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                    releaseProxy()
                    _state.update {
                        it.copy(
                            enabled = false, registered = false, connecting = false,
                            connectedName = null, connectedAddress = null,
                            message = "Bluetooth is off",
                        )
                    }
                }
            }
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged registered=$registered plugged=$pluggedDevice")
            _state.update {
                it.copy(
                    registered = registered,
                    message = if (registered) "Ready. Pair from the projector or pick a paired device."
                    else "Bluetooth remote not registered",
                )
            }
            if (registered) {
                val target = pluggedDevice ?: prefs.lastBluetoothAddress
                    ?.takeIf { BluetoothAdapter.checkBluetoothAddress(it) }
                    ?.let { adapter?.getRemoteDevice(it) }
                    ?.takeIf { it.bondState == BluetoothDevice.BOND_BONDED }
                if (target != null && host == null) connect(target.address)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "onConnectionStateChanged $device state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    prefs.lastBluetoothAddress = device.address
                    _state.update {
                        it.copy(
                            connecting = false,
                            connectedName = device.name ?: device.address,
                            connectedAddress = device.address,
                            message = "Connected",
                        )
                    }
                }
                BluetoothProfile.STATE_CONNECTING -> _state.update { it.copy(connecting = true) }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (host?.address == device.address || host == null) {
                        host = null
                        _state.update {
                            it.copy(
                                connecting = false, connectedName = null, connectedAddress = null,
                                message = "Disconnected",
                            )
                        }
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val size = when (id.toInt()) {
                HidDescriptor.ID_KEYBOARD -> HidDescriptor.KEYBOARD_REPORT_SIZE
                HidDescriptor.ID_CONSUMER -> HidDescriptor.CONSUMER_REPORT_SIZE
                HidDescriptor.ID_MOUSE -> HidDescriptor.MOUSE_REPORT_SIZE
                HidDescriptor.ID_SYSTEM -> HidDescriptor.SYSTEM_REPORT_SIZE
                else -> 0
            }
            if (size == 0) {
                hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
            } else {
                hid?.replyReport(device, type, id, ByteArray(size))
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            // Keyboard LED output reports etc. – acknowledge and ignore.
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            if (host?.address == device.address) host = null
            _state.update { it.copy(connectedName = null, connectedAddress = null, message = "Unplugged") }
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val device = proxy as BluetoothHidDevice
            hid = device
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "Goaiey Remote",
                "Phone remote control for projectors",
                "Goaiey Remote",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HidDescriptor.DESCRIPTOR,
            )
            val ok = device.registerApp(sdp, null, null, executor, callback)
            if (!ok) {
                _state.update { it.copy(message = "This phone refused to register a Bluetooth HID remote") }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = null
            host = null
            proxyRequested = false
            _state.update {
                it.copy(registered = false, connectedName = null, connectedAddress = null)
            }
        }
    }

    /** Call once Bluetooth permissions are granted. Safe to call repeatedly. */
    fun start() {
        val a = adapter
        if (a == null) {
            _state.update { it.copy(supported = false, message = "This phone has no Bluetooth") }
            return
        }
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                context, adapterReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        _state.update { it.copy(enabled = a.isEnabled) }
        if (a.isEnabled) requestProxy() else _state.update { it.copy(message = "Turn on Bluetooth") }
    }

    private fun requestProxy() {
        if (proxyRequested) return
        val ok = adapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE) ?: false
        proxyRequested = ok
        if (!ok) {
            _state.update {
                it.copy(supported = false, message = "This phone does not support acting as a Bluetooth remote (HID Device profile)")
            }
        }
    }

    private fun releaseProxy() {
        hid?.let { device ->
            runCatching { device.unregisterApp() }
            runCatching { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, device) }
        }
        hid = null
        host = null
        proxyRequested = false
    }

    fun stop() {
        releaseProxy()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(adapterReceiver) }
            receiverRegistered = false
        }
        executor.shutdown()
    }

    fun pairedDevices(): List<PairedDevice> = runCatching {
        adapter?.bondedDevices.orEmpty()
            .map { PairedDevice(it.name ?: it.address, it.address) }
            .sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())

    fun connect(address: String) {
        val device = hid ?: run {
            _state.update { it.copy(message = "Bluetooth remote not ready yet") }
            return
        }
        val target = adapter?.getRemoteDevice(address) ?: return
        host?.takeIf { it.address != address }?.let { device.disconnect(it) }
        _state.update { it.copy(connecting = true, message = "Connecting to ${target.name ?: address}…") }
        if (!device.connect(target)) {
            _state.update { it.copy(connecting = false, message = "Could not start connection") }
        }
    }

    fun disconnect() {
        val h = host ?: return
        hid?.disconnect(h)
    }

    // ---------------------------------------------------------------- reports

    private fun send(id: Int, data: ByteArray): Boolean {
        val h = host ?: return false
        val device = hid ?: return false
        return runCatching { device.sendReport(h, id, data) }.getOrDefault(false)
    }

    private fun sendKeyboardReport() {
        val report = ByteArray(HidDescriptor.KEYBOARD_REPORT_SIZE)
        report[0] = modifiers.toByte()
        pressedKeys.take(6).forEachIndexed { i, usage -> report[2 + i] = usage.toByte() }
        send(HidDescriptor.ID_KEYBOARD, report)
    }

    private fun sendConsumer(usage: Int) {
        send(
            HidDescriptor.ID_CONSUMER,
            byteArrayOf((usage and 0xFF).toByte(), ((usage shr 8) and 0xFF).toByte()),
        )
    }

    fun usageDown(usage: HidUsage) {
        when (usage) {
            is HidUsage.Keyboard -> {
                pressedKeys.add(usage.usage)
                sendKeyboardReport()
            }
            is HidUsage.Consumer -> sendConsumer(usage.usage)
            is HidUsage.System -> {
                systemBits = systemBits or usage.bit
                send(HidDescriptor.ID_SYSTEM, byteArrayOf(systemBits.toByte()))
            }
        }
    }

    fun usageUp(usage: HidUsage) {
        when (usage) {
            is HidUsage.Keyboard -> {
                pressedKeys.remove(usage.usage)
                sendKeyboardReport()
            }
            is HidUsage.Consumer -> sendConsumer(0)
            is HidUsage.System -> {
                systemBits = systemBits and usage.bit.inv()
                send(HidDescriptor.ID_SYSTEM, byteArrayOf(systemBits.toByte()))
            }
        }
    }

    fun keyDown(key: RemoteKey) = usageDown(key.hid)

    fun keyUp(key: RemoteKey) = usageUp(key.hid)

    /** Sends a complete press (tap, long press or repeated taps) of [variant]. */
    suspend fun perform(variant: KeyVariant) {
        repeat(variant.repeat) {
            usageDown(variant.usage)
            delay(variant.holdMs ?: KEY_HOLD_MS)
            usageUp(variant.usage)
            delay(KEY_HOLD_MS)
        }
    }

    /** Types text with the virtual keyboard. Unsupported characters are skipped. */
    suspend fun typeText(text: String): Int {
        var skipped = 0
        for (c in text) {
            val stroke = HidKeyMap.strokeFor(c)
            if (stroke == null) {
                skipped++
                continue
            }
            modifiers = stroke.modifier
            pressedKeys.clear()
            pressedKeys.add(stroke.usage)
            sendKeyboardReport()
            delay(KEY_GAP_MS)
            pressedKeys.clear()
            modifiers = 0
            sendKeyboardReport()
            delay(KEY_GAP_MS)
        }
        return skipped
    }

    fun mouseMove(dx: Int, dy: Int, wheel: Int = 0) {
        var remainingX = dx
        var remainingY = dy
        var remainingW = wheel
        // Split large moves into several reports (each axis is limited to -127..127).
        do {
            val x = remainingX.coerceIn(-127, 127)
            val y = remainingY.coerceIn(-127, 127)
            val w = remainingW.coerceIn(-127, 127)
            send(
                HidDescriptor.ID_MOUSE,
                byteArrayOf(mouseButtons.toByte(), x.toByte(), y.toByte(), w.toByte()),
            )
            remainingX -= x
            remainingY -= y
            remainingW -= w
        } while (remainingX != 0 || remainingY != 0 || remainingW != 0)
    }

    suspend fun mouseClick(button: Int = 1) {
        mouseButtons = button
        mouseMove(0, 0)
        delay(KEY_GAP_MS)
        mouseButtons = 0
        mouseMove(0, 0)
    }

    private companion object {
        const val TAG = "BluetoothHidRemote"
        const val KEY_GAP_MS = 25L
        const val KEY_HOLD_MS = 80L
    }
}
