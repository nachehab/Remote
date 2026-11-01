package com.nachehab.projectorremote.bt

import com.nachehab.projectorremote.model.HidUsage
import com.nachehab.projectorremote.model.RemoteKey

/**
 * One way of sending a button over Bluetooth.
 *
 * Projector firmwares often remap Power / Mute / Settings for their own remote, so the app
 * offers several standard encodings and lets the user keep whichever one the projector reacts to.
 *
 * @param holdMs  if set, the key is held this long (e.g. long-press Power opens the power menu).
 * @param repeat  number of taps to send (used for "volume to zero" instead of mute).
 */
data class KeyVariant(
    val id: String,
    val label: String,
    val usage: HidUsage,
    val holdMs: Long? = null,
    val repeat: Int = 1,
) {
    /** A plain press follows the finger (down on press, up on release). */
    val isPlainPress: Boolean get() = holdMs == null && repeat == 1
}

object KeyVariants {
    private val consumerPower = HidUsage.Consumer(0x0030)

    val options: Map<RemoteKey, List<KeyVariant>> = mapOf(
        RemoteKey.POWER to listOf(
            KeyVariant("consumer_power", "Power – consumer key (default)", consumerPower),
            KeyVariant("system_power", "Power – system control key", HidUsage.System(HidUsage.SYSTEM_POWER_DOWN)),
            KeyVariant("keyboard_power", "Power – keyboard power key", HidUsage.Keyboard(0x66)),
            KeyVariant("hold_power", "Long-press Power (power menu)", consumerPower, holdMs = 1500),
            KeyVariant("system_sleep", "Sleep – system control key", HidUsage.System(HidUsage.SYSTEM_SLEEP)),
            KeyVariant("consumer_sleep", "Sleep – consumer key", HidUsage.Consumer(0x0032)),
        ),
        RemoteKey.MUTE to listOf(
            KeyVariant("consumer_mute", "Mute – consumer key (default)", HidUsage.Consumer(0x00E2)),
            KeyVariant("keyboard_mute", "Mute – keyboard mute key", HidUsage.Keyboard(0x7F)),
            KeyVariant("volume_zero", "Turn volume all the way down", HidUsage.Consumer(0x00EA), repeat = 30),
        ),
        RemoteKey.SETTINGS to listOf(
            KeyVariant("consumer_control_panel", "Settings – control panel key (default)", HidUsage.Consumer(0x019F)),
            KeyVariant("consumer_properties", "Settings – properties key", HidUsage.Consumer(0x0209)),
            KeyVariant("consumer_menu", "Menu – consumer key", HidUsage.Consumer(0x0040)),
            KeyVariant("keyboard_menu", "Menu – keyboard menu key", HidUsage.Keyboard(0x65)),
            KeyVariant("hold_menu", "Long-press Menu", HidUsage.Consumer(0x0040), holdMs = 1200),
        ),
        RemoteKey.WAKE to listOf(
            KeyVariant("system_wake", "Wake-up key (default)", HidUsage.System(HidUsage.SYSTEM_WAKE_UP)),
            KeyVariant("consumer_power", "Power – consumer key", consumerPower),
            KeyVariant("system_power", "Power – system control key", HidUsage.System(HidUsage.SYSTEM_POWER_DOWN)),
            KeyVariant("keyboard_enter", "OK / Enter key", HidUsage.Keyboard(0x28)),
        ),
    )

    val configurableKeys: List<RemoteKey> = options.keys.toList()

    fun find(key: RemoteKey, id: String?): KeyVariant? =
        options[key]?.let { list -> list.firstOrNull { it.id == id } ?: list.first() }
}
