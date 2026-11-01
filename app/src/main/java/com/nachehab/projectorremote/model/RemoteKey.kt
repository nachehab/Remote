package com.nachehab.projectorremote.model

/** How a key is delivered over Bluetooth HID. */
sealed interface HidUsage {
    /** Keyboard page (0x07) usage, sent in the keyboard report. */
    data class Keyboard(val usage: Int) : HidUsage

    /** Consumer page (0x0C) usage, sent in the consumer-control report. */
    data class Consumer(val usage: Int) : HidUsage

    /** Generic Desktop "System Control" bit (power down / sleep / wake up). */
    data class System(val bit: Int) : HidUsage

    companion object {
        const val SYSTEM_POWER_DOWN = 0x01 // usage 0x81 -> Linux KEY_POWER  -> Android POWER
        const val SYSTEM_SLEEP = 0x02      // usage 0x82 -> Linux KEY_SLEEP  -> Android SLEEP
        const val SYSTEM_WAKE_UP = 0x04    // usage 0x83 -> Linux KEY_WAKEUP -> Android WAKEUP
    }
}

/**
 * Every button on the remote, with its Android key code (used over Wi-Fi / ADB)
 * and the default HID usage that Android's input stack maps to the same key (used over Bluetooth).
 * Power, Mute, Settings and Wake can be re-mapped per projector, see [com.nachehab.projectorremote.bt.KeyVariants].
 */
enum class RemoteKey(
    val androidKeyCode: Int,
    val hid: HidUsage,
    val repeatable: Boolean = false,
) {
    POWER(26, HidUsage.Consumer(0x0030)),
    HOME(3, HidUsage.Consumer(0x0223)),
    BACK(4, HidUsage.Consumer(0x0224)),
    MENU(82, HidUsage.Consumer(0x0040)),
    SETTINGS(176, HidUsage.Consumer(0x019F)),

    UP(19, HidUsage.Keyboard(0x52), repeatable = true),
    DOWN(20, HidUsage.Keyboard(0x51), repeatable = true),
    LEFT(21, HidUsage.Keyboard(0x50), repeatable = true),
    RIGHT(22, HidUsage.Keyboard(0x4F), repeatable = true),
    OK(23, HidUsage.Keyboard(0x28)),

    VOLUME_UP(24, HidUsage.Consumer(0x00E9), repeatable = true),
    VOLUME_DOWN(25, HidUsage.Consumer(0x00EA), repeatable = true),
    MUTE(164, HidUsage.Consumer(0x00E2)),

    PLAY_PAUSE(85, HidUsage.Consumer(0x00CD)),
    NEXT(87, HidUsage.Consumer(0x00B5)),
    PREVIOUS(88, HidUsage.Consumer(0x00B6)),
    REWIND(89, HidUsage.Consumer(0x00B4), repeatable = true),
    FAST_FORWARD(90, HidUsage.Consumer(0x00B3), repeatable = true),

    DELETE(67, HidUsage.Keyboard(0x2A), repeatable = true),
    ENTER(66, HidUsage.Keyboard(0x28)),

    /** Not a visible button: sent after reconnecting to wake a projector in standby. */
    WAKE(224, HidUsage.System(HidUsage.SYSTEM_WAKE_UP)),
}
