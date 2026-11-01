package com.nachehab.projectorremote.wifi

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

/** Finds devices on the local /24 subnet that accept connections on the ADB port. */
class NetworkScanner(private val context: Context) {

    fun localIpv4(): Inet4Address? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
    }

    suspend fun scan(
        port: Int = AdbRemote.DEFAULT_PORT,
        onProgress: (Float) -> Unit = {},
    ): List<String> = withContext(Dispatchers.IO) {
        val self = localIpv4() ?: return@withContext emptyList()
        val b = self.address
        val prefix = "${b[0].toInt() and 0xFF}.${b[1].toInt() and 0xFF}.${b[2].toInt() and 0xFF}"
        val selfLast = b[3].toInt() and 0xFF
        val gate = Semaphore(PARALLELISM)
        var done = 0
        val lock = Any()
        coroutineScope {
            (1..254).filter { it != selfLast }.map { last ->
                async {
                    val ip = "$prefix.$last"
                    val open = gate.withPermit { isOpen(ip, port) }
                    synchronized(lock) {
                        done++
                        onProgress(done / 253f)
                    }
                    if (open) ip else null
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun isOpen(ip: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), TIMEOUT_MS); true }
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val PARALLELISM = 48
        const val TIMEOUT_MS = 400
    }
}
