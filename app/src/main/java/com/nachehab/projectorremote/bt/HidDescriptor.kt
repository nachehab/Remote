package com.nachehab.projectorremote.bt

/**
 * HID report descriptor describing a combo device:
 *  - Report 1: boot-style keyboard (modifiers + 6 key array)
 *  - Report 2: consumer control (one 16-bit usage: home, back, volume, media, power...)
 *  - Report 3: relative mouse (3 buttons, X, Y, wheel)
 *  - Report 4: system control (power down, sleep, wake up)
 *
 * Android-based projectors accept this as a standard Bluetooth remote/keyboard/mouse.
 */
object HidDescriptor {
    const val ID_KEYBOARD = 1
    const val ID_CONSUMER = 2
    const val ID_MOUSE = 3
    const val ID_SYSTEM = 4

    /**
     * Bump whenever [DESCRIPTOR] changes: the projector caches the descriptor at pairing time,
     * so the user has to un-pair and pair again to pick up the change.
     */
    const val VERSION = 2

    const val KEYBOARD_REPORT_SIZE = 8
    const val CONSUMER_REPORT_SIZE = 2
    const val MOUSE_REPORT_SIZE = 4
    const val SYSTEM_REPORT_SIZE = 1

    val DESCRIPTOR: ByteArray = bytes(
        // ---- Keyboard ----
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x06,       // Usage (Keyboard)
        0xA1, 0x01,       // Collection (Application)
        0x85, ID_KEYBOARD, //   Report ID (1)
        0x05, 0x07,       //   Usage Page (Keyboard/Keypad)
        0x19, 0xE0,       //   Usage Minimum (Left Control)
        0x29, 0xE7,       //   Usage Maximum (Right GUI)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x01,       //   Logical Maximum (1)
        0x75, 0x01,       //   Report Size (1)
        0x95, 0x08,       //   Report Count (8)
        0x81, 0x02,       //   Input (Data, Variable, Absolute)  ; modifier byte
        0x95, 0x01,       //   Report Count (1)
        0x75, 0x08,       //   Report Size (8)
        0x81, 0x01,       //   Input (Constant)                  ; reserved byte
        0x95, 0x06,       //   Report Count (6)
        0x75, 0x08,       //   Report Size (8)
        0x15, 0x00,       //   Logical Minimum (0)
        0x26, 0xFF, 0x00, //   Logical Maximum (255)
        0x05, 0x07,       //   Usage Page (Keyboard/Keypad)
        0x19, 0x00,       //   Usage Minimum (0)
        0x29, 0xFF,       //   Usage Maximum (255)  ; includes Power 0x66 and Mute 0x7F
        0x81, 0x00,       //   Input (Data, Array)               ; key array
        0xC0,             // End Collection

        // ---- Consumer control ----
        0x05, 0x0C,       // Usage Page (Consumer)
        0x09, 0x01,       // Usage (Consumer Control)
        0xA1, 0x01,       // Collection (Application)
        0x85, ID_CONSUMER, //   Report ID (2)
        0x15, 0x00,       //   Logical Minimum (0)
        0x26, 0xFF, 0x03, //   Logical Maximum (0x03FF)
        0x19, 0x00,       //   Usage Minimum (0)
        0x2A, 0xFF, 0x03, //   Usage Maximum (0x03FF)
        0x75, 0x10,       //   Report Size (16)
        0x95, 0x01,       //   Report Count (1)
        0x81, 0x00,       //   Input (Data, Array)
        0xC0,             // End Collection

        // ---- Mouse ----
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x02,       // Usage (Mouse)
        0xA1, 0x01,       // Collection (Application)
        0x85, ID_MOUSE,   //   Report ID (3)
        0x09, 0x01,       //   Usage (Pointer)
        0xA1, 0x00,       //   Collection (Physical)
        0x05, 0x09,       //     Usage Page (Buttons)
        0x19, 0x01,       //     Usage Minimum (1)
        0x29, 0x03,       //     Usage Maximum (3)
        0x15, 0x00,       //     Logical Minimum (0)
        0x25, 0x01,       //     Logical Maximum (1)
        0x95, 0x03,       //     Report Count (3)
        0x75, 0x01,       //     Report Size (1)
        0x81, 0x02,       //     Input (Data, Variable, Absolute)
        0x95, 0x01,       //     Report Count (1)
        0x75, 0x05,       //     Report Size (5)
        0x81, 0x01,       //     Input (Constant)                ; padding
        0x05, 0x01,       //     Usage Page (Generic Desktop)
        0x09, 0x30,       //     Usage (X)
        0x09, 0x31,       //     Usage (Y)
        0x09, 0x38,       //     Usage (Wheel)
        0x15, 0x81,       //     Logical Minimum (-127)
        0x25, 0x7F,       //     Logical Maximum (127)
        0x75, 0x08,       //     Report Size (8)
        0x95, 0x03,       //     Report Count (3)
        0x81, 0x06,       //     Input (Data, Variable, Relative)
        0xC0,             //   End Collection
        0xC0,             // End Collection

        // ---- System control (power down / sleep / wake up) ----
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x80,       // Usage (System Control)
        0xA1, 0x01,       // Collection (Application)
        0x85, ID_SYSTEM,  //   Report ID (4)
        0x19, 0x81,       //   Usage Minimum (System Power Down)
        0x29, 0x83,       //   Usage Maximum (System Wake Up)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x01,       //   Logical Maximum (1)
        0x75, 0x01,       //   Report Size (1)
        0x95, 0x03,       //   Report Count (3)
        0x81, 0x02,       //   Input (Data, Variable, Absolute)
        0x95, 0x01,       //   Report Count (1)
        0x75, 0x05,       //   Report Size (5)
        0x81, 0x01,       //   Input (Constant)                ; padding
        0xC0,             // End Collection
    )

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
