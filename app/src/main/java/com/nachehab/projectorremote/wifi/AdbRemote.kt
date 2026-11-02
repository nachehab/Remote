package com.nachehab.projectorremote.wifi

import android.content.Context
import android.util.Log
import com.nachehab.projectorremote.model.RemoteKey
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

data class WifiState(
    val host: String? = null,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val deviceName: String? = null,
    val message: String? = null,
)

enum class PowerResult { ASLEEP, AWAKE, SENT, NO_EFFECT, NOT_CONNECTED }

data class InstalledApp(val packageName: String, val label: String)

/**
 * Controls an Android-based projector over Wi-Fi using the ADB protocol (port 5555).
 * Key presses are injected with `input keyevent`, which works on any Android projector
 * that has network debugging enabled — no companion app on the projector is required.
 *
 * All calls are serialised on a single background thread.
 */
class AdbRemote(private val context: Context) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var dadb: Dadb? = null
    private var target: Pair<String, Int>? = null

    private val _state = MutableStateFlow(WifiState())
    val state: StateFlow<WifiState> = _state.asStateFlow()

    private val keyPair: AdbKeyPair by lazy {
        val priv = File(context.filesDir, "adbkey")
        val pub = File(context.filesDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        AdbKeyPair.read(priv, pub)
    }

    suspend fun connect(host: String, port: Int = DEFAULT_PORT): Boolean = withContext(dispatcher) {
        closeQuietly()
        _state.value = WifiState(
            host = host, connecting = true,
            message = "Connecting to $host:$port… If the projector asks \"Allow debugging?\", tick \"Always allow\" and press OK.",
        )
        try {
            val d = open(host, port)
            val model = d.shell("getprop ro.product.model").output.trim()
            val brand = d.shell("getprop ro.product.brand").output.trim()
            dadb = d
            target = host to port
            val name = listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { host }
            _state.value = WifiState(host = host, connected = true, deviceName = name, message = "Connected")
            true
        } catch (e: Exception) {
            Log.w(TAG, "connect failed", e)
            closeQuietly()
            _state.value = WifiState(host = host, message = "Connection failed: ${describe(e)}")
            false
        }
    }

    private fun open(host: String, port: Int): Dadb =
        Dadb.create(host, port, keyPair, CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS, true)

    suspend fun disconnect() = withContext(dispatcher) {
        closeQuietly()
        target = null
        _state.update { WifiState(host = it.host, message = "Disconnected") }
    }

    private fun closeQuietly() {
        runCatching { dadb?.close() }
        dadb = null
    }

    /** Runs a shell command, transparently reconnecting once if the socket dropped. */
    suspend fun shell(command: String): String? = withContext(dispatcher) {
        val d = dadb ?: return@withContext null
        try {
            d.shell(command).allOutput
        } catch (first: Exception) {
            Log.w(TAG, "shell failed, reconnecting", first)
            val (host, port) = target ?: return@withContext null
            closeQuietly()
            try {
                val fresh = open(host, port)
                dadb = fresh
                fresh.shell(command).allOutput
            } catch (second: Exception) {
                Log.w(TAG, "reconnect failed", second)
                closeQuietly()
                _state.update { it.copy(connected = false, message = "Connection lost: ${describe(second)}") }
                null
            }
        }
    }

    suspend fun sendKey(key: RemoteKey) {
        shell("input keyevent ${key.androidKeyCode}")
    }

    suspend fun sendText(text: String) {
        if (text.isEmpty()) return
        // `input text` treats %s as a space; wrap in single quotes for the device shell.
        val escaped = text.replace(" ", "%s").replace("'", "'\\''")
        shell("input text '$escaped'")
    }

    /** "Awake", "Asleep", "Dozing", "Dreaming", or null if it can't be read. */
    suspend fun wakefulness(): String? {
        val out = shell("dumpsys power | grep -m1 mWakefulness=") ?: return null
        return out.substringAfter("mWakefulness=", "").trim().ifBlank { null }
    }

    /**
     * Toggles standby with the projector's own power manager. Tries the sleep / wake key first,
     * then the plain power key, and checks the result, because projector firmwares often
     * ignore one of them.
     */
    suspend fun togglePower(): PowerResult {
        val before = wakefulness()
        val goingToSleep = before == null || before == "Awake"
        val attempts = if (goingToSleep) listOf(KEY_SLEEP, KEY_POWER) else listOf(KEY_WAKEUP, KEY_POWER)
        for (code in attempts) {
            shell("input keyevent $code") ?: return PowerResult.NOT_CONNECTED
            delay(POWER_SETTLE_MS)
            val now = wakefulness() ?: return PowerResult.SENT // unknown; assume it worked
            if ((now == "Awake") != goingToSleep) {
                return if (goingToSleep) PowerResult.ASLEEP else PowerResult.AWAKE
            }
        }
        return PowerResult.NO_EFFECT
    }

    suspend fun wakeUp(): Boolean = shell("input keyevent $KEY_WAKEUP") != null

    /** Full shutdown. The projector then needs its own remote or power button to start again. */
    suspend fun shutDown(): Boolean =
        shell("reboot -p || svc power shutdown") != null || !(_state.value.connected)

    /** Reconnects to the last host if the socket dropped (e.g. projector went to standby). */
    suspend fun ensureConnected(fallbackHost: String, fallbackPort: Int): Boolean {
        if (_state.value.connected && shell("echo ok")?.contains("ok") == true) return true
        val (host, port) = target ?: (fallbackHost to fallbackPort)
        if (host.isBlank()) return false
        return connect(host, port)
    }

    suspend fun openSettings() {
        shell("am start -a android.settings.SETTINGS")
    }

    suspend fun launchApp(packageName: String) {
        require(PACKAGE_REGEX.matches(packageName)) { "Invalid package name" }
        shell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }

    /** Lists launchable apps (package names; labels are not exposed by the shell). */
    suspend fun listApps(): List<InstalledApp> {
        val out = shell("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER; " +
            "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER")
            ?: return emptyList()
        val fromQuery = out.lineSequence()
            .map { it.trim() }
            .filter { it.contains('/') && !it.startsWith("priority") }
            .map { it.substringBefore('/') }
            .filter { PACKAGE_REGEX.matches(it) }
            .toSet()
        val packages = fromQuery.ifEmpty {
            (shell("pm list packages -3") ?: "").lineSequence()
                .map { it.trim().removePrefix("package:") }
                .filter { PACKAGE_REGEX.matches(it) }
                .toSet()
        }
        return packages
            .map { InstalledApp(it, prettyLabel(it)) }
            .sortedBy { it.label.lowercase() }
    }

    fun close() {
        closeQuietly()
        dispatcher.close()
    }

    private fun prettyLabel(pkg: String): String {
        KNOWN_APPS[pkg]?.let { return it }
        val parts = pkg.split('.').filterNot { it in setOf("com", "android", "tv", "app", "org", "net", "google") }
        return (parts.lastOrNull() ?: pkg).replaceFirstChar { it.uppercase() }
    }

    private fun describe(e: Exception): String {
        // dadb wraps socket errors (e.g. AdbConnectException), so inspect the whole cause chain.
        val chain = generateSequence<Throwable>(e) { it.cause }.take(8).toList()
        return when {
            chain.any { it is java.net.SocketTimeoutException } ->
                "timed out. Check the IP, that both devices are on the same Wi-Fi, and accept the prompt on the projector."
            chain.any { it is java.net.ConnectException || it is java.net.NoRouteToHostException } ->
                "nothing is listening on that address. Enable network/ADB debugging on the projector."
            chain.any { it.javaClass.simpleName.contains("Auth", ignoreCase = true) } ->
                "the projector rejected the debugging request."
            else -> e.message ?: e.javaClass.simpleName
        }
    }

    companion object {
        const val DEFAULT_PORT = 5555
        private const val KEY_POWER = 26
        private const val KEY_SLEEP = 223
        private const val KEY_WAKEUP = 224
        private const val POWER_SETTLE_MS = 1_500L
        private const val TAG = "AdbRemote"
        private const val CONNECT_TIMEOUT_MS = 5_000
        // Long enough for the user to accept the "Allow debugging?" prompt on the projector.
        private const val SOCKET_TIMEOUT_MS = 45_000
        private val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

        private val KNOWN_APPS = mapOf(
            "com.google.android.youtube.tv" to "YouTube",
            "com.google.android.youtube" to "YouTube",
            "com.netflix.ninja" to "Netflix",
            "com.netflix.mediaclient" to "Netflix",
            "com.amazon.amazonvideo.livingroom" to "Prime Video",
            "com.disney.disneyplus" to "Disney+",
            "com.android.chrome" to "Chrome",
            "com.android.vending" to "Play Store",
            "com.android.tv.settings" to "TV Settings",
            "com.android.settings" to "Settings",
            "org.videolan.vlc" to "VLC",
            "com.spotify.tv.android" to "Spotify",
            "com.plexapp.android" to "Plex",
            "org.xbmc.kodi" to "Kodi",
        )
    }
}
