<p align="center"><img src="docs/icon.png" width="96" alt="PiConnect app icon"></p>

# PiConnect

**Your Raspberry Pi lab, from anywhere.**

Pair an Android tablet with a physical keyboard and mouse, open this app, and you're at your Raspberry Pi's desktop — wherever your feet take you, as long as there's an internet connection. It's close to carrying your PC around with you, without carrying your PC around.

This is just a thin wrapper around [Raspberry Pi Connect](https://www.raspberrypi.com/software/connect/), Raspberry Pi's official remote access service, which does all the hard work. Raspberry Pi Connect is a great thing: secure screen sharing and a remote shell to your Pi from any browser, with no port forwarding or VPN to set up. All credit for that goes to the Raspberry Pi team. I made this app to fit my own requirements. It isn't perfect, but it does everything I need. It's also entirely vibe-coded, written with an AI coding assistant, because it's just a tool.

> This is an unofficial project and isn't affiliated with or endorsed by Raspberry Pi Ltd. Raspberry Pi is a trademark of Raspberry Pi Ltd.

## ✨ What it does

- 🖥️ **A sharp desktop that fills the screen.** A mobile browser tells websites its screen is much smaller than it really is, so Raspberry Pi Connect streams a small, blurry desktop. The app reports the real pixel resolution, opens screen sharing straight into fullscreen, and keeps the desktop sized to the whole screen.
- 🧹 **Nothing but your desktop.** No address bar or tabs, and Android's status bar, navigation bar and taskbar are hidden. Swipe in from a screen edge when you need them.
- 🎯 **Only what you need from Raspberry Pi Connect.** After you sign in, you see just your device list and the sessions you open from it. Menus and account pages are hidden, and links to other sites open in your regular browser.
- ⌨️ **Made for a physical keyboard.** One important catch: I couldn't get the real Alt key to switch windows on the Pi. Android grabs Alt+Tab before the app ever sees it, and there's no way for an app to stop that. The workaround is that **Right Ctrl acts as Alt**, so Right Ctrl + Tab switches windows on the Pi. Weird, but hell, it works. The on-screen keyboard also stays out of the way while a keyboard is attached.
- 🔍 **Adjustable size.** Make everything larger or smaller with the volume keys. Your choice is remembered.
- 🔁 **Stable sessions.** Rotating the tablet or attaching and detaching the keyboard doesn't reload the session.

## ⌨️ Controls

| Key | What it does |
|---|---|
| **Volume up / down** | Makes everything larger or smaller (0.5×–3×, in 0.25 steps; default 1.25×). The volume keys don't change the volume in this app, and a hint reminds you of that at start-up. |
| **Right Ctrl** or **Menu** | Acts as **Alt** on the Pi. Hold it and press Tab to switch windows (add Shift to go back), or use it for any other Alt shortcut, such as Right Ctrl + F4 to close a window. |
| **Back** | Leaves fullscreen, then goes back a page. |
| **Swipe in from a screen edge** | Shows Android's system bars briefly. |

All other keys, including Left Ctrl and shortcuts like Ctrl+C, go to the Pi as usual.

## 🚀 Getting started

You don't need SSH, a VPN, port forwarding or any direct network connection to your Pi. All you need is a Raspberry Pi with Raspberry Pi Connect installed, and voilà.

1. Set up [Raspberry Pi Connect](https://www.raspberrypi.com/documentation/services/connect.html) on your Raspberry Pi and sign in with your Raspberry Pi ID.
2. Download the APK:
   - from this repo's [Releases](https://github.com/r4d01/piconnect-app/releases) page, or
   - from the latest successful build on the [Actions](https://github.com/r4d01/piconnect-app/actions) page (the `piconnect-debug` artifact; you need to be signed in to GitHub).
3. Install it on your tablet. You'll need to allow installing apps from unknown sources for your browser or file manager.
4. Open **PiConnect**, sign in, and choose **Connect via** next to one of your devices.

Requires Android 5.0 or newer. Hiding the system bars and the on-screen keyboard works best on Android 11 or newer.

### 💡 Recommended setup

- **A tablet with a keyboard and a mouse or touchpad.** You can use touch, but a remote desktop is much nicer with real input devices.
- **Turn off the on-screen keyboard for physical keyboards** in Android's settings. It's usually under **Settings → System → Keyboard → Physical keyboard → Use on-screen keyboard**, but the exact path varies between manufacturers. The app already hides the on-screen keyboard, but with this setting off, the taskbar doesn't even flash when you click into a text field.
- **Nothing extra on the Pi.** Raspberry Pi OS already switches windows with Alt+Tab.

### 🔄 Upgrading

Uninstall the old app first, then install the new APK. Android won't install a new build over the old one, because each build is signed with a different key. Uninstalling removes the app's data, so you'll need to sign in again and set your preferred size again.

## ⚠️ Known limitations

- 🔀 **Real Alt+Tab and the Super key go to Android**, not to the Pi. Android doesn't let apps capture them. Use Right Ctrl + Tab to switch windows on the Pi. The Super key can't be passed through.
- 🎛️ **Right Ctrl is no longer a Ctrl key** in the app, because it stands in for Alt. Left Ctrl works as normal.
- 🚪 **No sign-out button.** Raspberry Pi Connect's menus are hidden. To sign out, clear the app's data: **Settings → Apps → PiConnect → Storage → Clear data**.
- ✍️ **Accented letters typed with dead keys** may not combine while a physical keyboard is attached (for example `ˇ` then `c` for `č`). Letters that are directly on a key work fine.
- 🧩 **It depends on Raspberry Pi Connect's web pages.** The app finds the "Enter full screen" button by its label, and the device list by its layout. If Raspberry Pi Connect changes these, automatic fullscreen or the trimmed device list may stop working until the app is updated.
- 🔇 **No sound.** Raspberry Pi Connect's screen sharing doesn't transfer audio from the Pi.

## 🛠️ Building

The APK is built by GitHub Actions ([`.github/workflows/build.yml`](.github/workflows/build.yml)):

- Every push to `main` builds a debug APK and attaches it to the workflow run.
- Pushing a tag like `v1.0` also publishes the APK on the Releases page.

To build locally, you need JDK 17, the Android SDK and Gradle 8.6 on an x86_64 machine. Run `gradle assembleDebug`, and the APK ends up in `app/build/outputs/apk/debug/`. The Android build tools don't run on a Raspberry Pi, which is why the project relies on CI.
