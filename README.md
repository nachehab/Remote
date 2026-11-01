# Goaiey Remote

An Android app that controls a Goaiey (Android‑based) projector from your phone over
**Bluetooth** or **Wi‑Fi**. Nothing needs to be installed on the projector.

| | Bluetooth mode | Wi‑Fi mode |
|---|---|---|
| How it works | The phone acts as a Bluetooth **remote + keyboard + mouse** (Bluetooth HID profile) | The phone sends key events over **network ADB** (port 5555) |
| Setup on projector | Pair the phone in the projector's Bluetooth settings | Turn on Developer options → USB / network debugging |
| D‑pad, OK, Back, Home, Menu, Power, Volume, Media | ✅ | ✅ |
| Type text | ✅ (US layout) | ✅ |
| Touchpad / mouse pointer | ✅ | — |
| Launch apps, open Settings | — | ✅ |
| Find projector automatically | via pairing | LAN scan for port 5555 |

## Requirements

- Phone on Android 9 (API 28) or newer. Bluetooth mode also needs the phone to support the
  Bluetooth "HID Device" profile (most phones do; a few vendor ROMs disable it — use Wi‑Fi mode then).
- A Goaiey projector running Android / Android TV. Projectors with a non‑Android "Linux"
  firmware only accept their bundled IR remote and cannot be controlled by this app.

## Setup

### Bluetooth
1. Open the app on the **Bluetooth** tab and tap **Make phone visible**.
2. On the projector: *Settings → Bluetooth* (or *Remotes & Accessories*) → *Search / Add accessory*.
3. Pick your phone and confirm pairing on both devices.
4. Use the remote. Next time, tap **Connect** next to the projector in *Paired devices*
   (the app also reconnects to the last projector automatically).

### Wi‑Fi
1. Put the phone and the projector on the same Wi‑Fi network.
2. On the projector: *Settings → About* → tap *Build number* 7 times to unlock Developer options.
3. In *Developer options* enable *USB debugging* and, if listed, *Network / ADB debugging*.
4. In the app's **Wi‑Fi** tab, tap **Find projector** or type its IP (Settings → Network on the projector).
5. Tap **Connect** and accept *"Allow debugging?"* on the projector (tick *Always allow*).

## Building

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and the Android SDK (API 35). Every push also builds the APK in GitHub Actions
(*Actions → Build APK → artifact `goaiey-remote-debug`*).

## Project layout

```
app/src/main/java/com/nachehab/projectorremote/
├── MainActivity.kt          permissions + system Bluetooth dialogs
├── RemoteViewModel.kt       mode switching, key repeat, glue
├── model/RemoteKey.kt       each button → Android keycode + HID usage
├── bt/                      Bluetooth HID device (descriptor, key map, connection)
├── wifi/                    ADB client wrapper (dadb) and LAN scanner
├── data/Prefs.kt            last projector / mode
└── ui/                      Jetpack Compose screens
```
