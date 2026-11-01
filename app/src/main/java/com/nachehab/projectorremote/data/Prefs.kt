package com.nachehab.projectorremote.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("remote_prefs", Context.MODE_PRIVATE)

    var lastHost: String
        get() = sp.getString(KEY_HOST, "") ?: ""
        set(value) = sp.edit().putString(KEY_HOST, value).apply()

    var lastPort: Int
        get() = sp.getInt(KEY_PORT, 5555)
        set(value) = sp.edit().putInt(KEY_PORT, value).apply()

    var lastBluetoothAddress: String?
        get() = sp.getString(KEY_BT, null)
        set(value) = sp.edit().putString(KEY_BT, value).apply()

    var lastMode: String
        get() = sp.getString(KEY_MODE, "BLUETOOTH") ?: "BLUETOOTH"
        set(value) = sp.edit().putString(KEY_MODE, value).apply()

    fun keyVariant(key: String): String? = sp.getString("variant_$key", null)

    fun setKeyVariant(key: String, id: String) = sp.edit().putString("variant_$key", id).apply()

    /** HID descriptor version the projector was last paired with (0 = unknown / never). */
    var pairedDescriptorVersion: Int
        get() = sp.getInt(KEY_DESCRIPTOR, 0)
        set(value) = sp.edit().putInt(KEY_DESCRIPTOR, value).apply()

    private companion object {
        const val KEY_DESCRIPTOR = "descriptor_version"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_BT = "bt_address"
        const val KEY_MODE = "mode"
    }
}
