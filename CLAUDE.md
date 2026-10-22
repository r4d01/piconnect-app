# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A minimal Android app (Java, no AndroidX or other dependencies) that wraps Raspberry Pi Connect (`https://connect.raspberrypi.com`) in a fullscreen WebView. The target setup is an Android tablet with a physical keyboard and mouse used as a remote desktop into a Raspberry Pi.

## Building

There is no Gradle wrapper in the repo, and the Android SDK build tools don't run on the ARM64 Raspberry Pi this project is developed on. Builds happen in GitHub Actions (`.github/workflows/build.yml`), which installs Gradle 8.6 and runs `gradle assembleDebug`:

- Every push to `main` uploads the debug APK as a workflow artifact.
- Pushing a `v*` tag also attaches the APK to a GitHub Release.

On an x86_64 machine with JDK 17 and the Android SDK: `gradle assembleDebug`, `gradle installDebug`, `gradle lint`. There are no tests.

Changes can only be verified by installing the CI-built APK on a device, so say so when reporting changes as done.

## Architecture

Everything lives in `MainActivity.java`. The non-obvious parts:

- **Resolution scaling.** Pi Connect sizes the remote desktop to the page's CSS viewport. With `setUseWideViewPort(false)` that viewport equals the WebView's width in dp, so `applyScale()` lays the WebView out at `density / scale` times the container size and shrinks it back with a view transform. The page then sees `physicalWidth / scale` CSS px. The same transform is applied to the fullscreen custom view. `scale` is persisted in SharedPreferences and changed with the volume keys. Touch input is mapped through the transform by Android automatically.
- **Fullscreen.** Pi Connect's "Enter full screen" button calls `requestFullscreen()`, which WebView delivers as `onShowCustomView`. The WebView is hidden with `INVISIBLE`, not `GONE`: `GONE` resizes it to 0×0 and Pi Connect renegotiates a tiny resolution.
- **Injected page JS (`PAGE_JS`, run in `onPageFinished`).** A MutationObserver spots the "Enter full screen" button when a session opens and calls the `PiConnectApp.tap()` JS bridge with its position; `tapWebView()` dispatches a synthetic `MotionEvent` there. A JS `click()` wouldn't work: `requestFullscreen()` needs user activation, which only real input (including injected MotionEvents) grants. The script also pins window scroll to the origin while a session is open. Both detect the session by finding the full screen button by its label text, so a Pi Connect UI change can break them.
- **Super key.** Android consumes Meta before apps see it, so Right Ctrl and Menu stand in for it. `dispatchKeyEvent` re-dispatches a real `KEYCODE_META_LEFT` plus Meta-modified key events into the WebView, which Chromium turns into trusted JS key events. Alt+Tab and other system shortcuts can't be captured by any app API.
- **System bars.** `hideSystemBars()` uses immersive mode (re-applied on every window focus gain) and lays content out edge-to-edge, so a transient bar swipe overlays the page instead of resizing it.
- **Manifest.** The activity handles orientation, screen size and keyboard config changes itself and uses `adjustNothing`, so rotating or attaching a keyboard never reloads or pans the page.

The launcher icon is a vector: an adaptive icon in `mipmap-anydpi-v26/` (foreground in `drawable/ic_launcher_foreground.xml`) and a duplicate of the same artwork for pre-Android 8 in `mipmap/ic_launcher.xml`. Edit both together. It deliberately avoids the Raspberry Pi logo, which is a trademark.
