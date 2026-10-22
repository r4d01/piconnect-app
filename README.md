# PiConnect App

**Your Raspberry Pi lab, from anywhere.**

Pair an Android tablet with a physical keyboard and mouse, open this app, and you're at your Raspberry Pi's desktop — from the couch, a café, or the other side of the world. It's close to carrying your PC around with you, without carrying your PC around.

The app is a lightweight fullscreen wrapper around [Raspberry Pi Connect](https://www.raspberrypi.com/software/connect/), Raspberry Pi's official browser-based remote access service. Raspberry Pi Connect does the hard part (secure remote screen sharing with no port forwarding or VPN); this app makes it feel like a native desktop on an Android device instead of a website squeezed into a browser tab.

## Why not just use the browser?

A mobile browser gets in the way when you're trying to use a remote desktop:

- **Resolution.** Phones and tablets report a small "CSS" screen size to websites, so Raspberry Pi Connect streams a tiny, blurry desktop. This app reports the real pixel resolution, so you get a sharp desktop sized to your screen.
- **Screen space.** No address bar, no tabs, no status bar — just the desktop.
- **Text size.** Adjustable with the volume keys, and remembered between sessions.
- **The Super key.** Android swallows the Super/Windows key. The app gives you a stand-in so desktop shortcuts still work.

## Features

- Fullscreen Raspberry Pi Connect, entered automatically on your first tap or key press in a session
- Desktop resolution matched to your device's real screen
- **Volume up / down** — make everything larger or smaller (0.5×–3×, default 1.5×)
- **Right Ctrl or Menu** — acts as the **Super** key on the remote desktop (alone, or in combos like Super + E)
- **Back** — leaves fullscreen, then navigates back
- Handles rotation and attaching/detaching a keyboard without reloading the session

## Getting started

1. Set up [Raspberry Pi Connect](https://www.raspberrypi.com/documentation/services/connect.html) on your Raspberry Pi and sign in with your Raspberry Pi ID.
2. Download the latest `app-debug.apk` from this repo's [Releases](https://github.com/r4d01/piconnect-app/releases) page.
3. Install it on your Android device. You'll need to allow installing apps from unknown sources for your browser or file manager.
4. Open **PiConnect**, sign in, pick your Pi, and start screen sharing.

Requires Android 5.0 or newer.

## Known limitations

- **Alt+Tab and other system shortcuts** go to Android, not to the Pi. Android doesn't let apps capture them.
- **The real Super key** opens Android's launcher. Use Right Ctrl or Menu instead. While held as Super, Right Ctrl can't be used as an ordinary Ctrl key; Left Ctrl is unaffected.
- **Auto-fullscreen** works by pressing Raspberry Pi Connect's own "Enter full screen" button. If Raspberry Pi Connect changes that button, auto-fullscreen may stop working until the app is updated.

## Building

The APK is built by GitHub Actions ([`.github/workflows/build.yml`](.github/workflows/build.yml)):

- Every push to `main` builds a debug APK and attaches it to the workflow run.
- Pushing a tag like `v1.0` also publishes the APK on the Releases page.

To build locally you need a JDK 17, the Android SDK, and Gradle 8.6 on an x86_64 machine, then run `gradle assembleDebug`. The APK ends up in `app/build/outputs/apk/debug/`.

---

This is an unofficial project and isn't affiliated with or endorsed by Raspberry Pi Ltd. Raspberry Pi is a trademark of Raspberry Pi Ltd.
