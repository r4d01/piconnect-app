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
- **Injected page JS (`PAGE_JS`, run in `onPageFinished`).** A MutationObserver spots the "Enter full screen" control (matched by visible text, aria-label or title, and only once it's enabled) when a session opens and calls the `PiConnectApp.tap()` JS bridge with its position; `tapWebView()` dispatches a synthetic `MotionEvent` there. A JS `click()` wouldn't work: `requestFullscreen()` needs user activation, which only real input (including injected MotionEvents) grants. The script also pins window scroll to the origin while a session is open. Both detect the session by finding the full screen button by its label text, so a Pi Connect UI change can break them.
- **Stand-in Alt key.** Android intercepts Alt+Tab (and Meta) before apps see them, and no app API prevents it. Right Ctrl and Menu act as Alt instead: `dispatchKeyEvent` injects a `KEYCODE_ALT_LEFT` event plus Alt-modified copies of other keys via `Window.superDispatchKeyEvent()` (the focused-view path; dispatching to the WebView directly drops events in fullscreen). Synthetic events must carry a Linux scan code, because Chromium derives the DOM `code` from it and Pi Connect ignores keys whose `code` is empty. `REMOTE_MOD_*` selects the modifier; Meta (keycode `KEYCODE_META_LEFT`, scan code 125) works the same way, but Raspberry Pi OS only binds Super alone, to open the menu.
- **System bars.** `hideSystemBars()` uses immersive mode (re-applied on every window focus gain) and lays content out edge-to-edge, so a transient bar swipe overlays the page instead of resizing it.
- **No IME with a physical keyboard.** A shown IME (even just a keyboard toolbar) keeps the tablet taskbar visible despite immersive mode (seen in Pi Connect's remote shell and screen sharing). `KeyboardWebView` returns no `InputConnection` while a hardware keyboard is attached, and because Chromium's fullscreen view takes focus in fullscreen, `MainActivity.updateImeBlock()` also sets `FLAG_ALT_FOCUSABLE_IM` on the window (updated by an `InputDeviceListener`), which keeps every view in it from connecting to the IME. Keyboard presence comes from enumerating `InputDevice`s: detachable tablet keyboards often don't set `Configuration.keyboard`. Hardware keys still type normally, translated with Android's active physical keyboard layout (not the IME's language), and dead keys don't compose (so layouts like US International break `'` and `"`; the README tells users to pick plain US). Keeping the IME connected and only hiding its window was tried: Gboard then flashes the taskbar on every keystroke, even with nothing visible.
- **Fullscreen resize nudge.** `notifyPageResized()` fires a JS `resize` event after entering/leaving fullscreen. The fullscreen view is the WebView's size, so no real resize happens, and without the nudge a stream started before fullscreen stays letterboxed to the area between Pi Connect's toolbars.
- **Navigation lock.** The app shows only Pi Connect's device list (`/devices`, the start page) and sessions (`/devices/<id>/screen-sharing-session`, `/devices/<id>/remote-shell-session`, opened by Pi Connect via `window.open()`, which loads in the same WebView), plus sign-in (`/sign-in`, `/auth/…`, `id.raspberrypi.com`). `allowInApp()` enforces this from `shouldOverrideUrlLoading` and, because Pi Connect uses Hotwired Turbo whose in-page visits bypass that callback, from a `turbo:before-visit` listener via the JS bridge. Other Pi Connect paths reload `/devices`; other sites open in the system browser. On `/devices`, injected CSS hides everything outside `<main>` (keyed on an `html.piapp-devices` class refreshed on `turbo:load`).
- **Load problems.** A WebView that hasn't drawn a page yet is black, so a failed or stalled load looked like a broken app. A failed main-frame load (`onReceivedError`) or one still unfinished after `SLOW_LOAD_MS` shows the `load_problem` overlay from the layout, with a Reload button; `onPageFinished` hides it unless the load failed.
- **Manifest.** The activity handles orientation, screen size and keyboard config changes itself and uses `adjustNothing`, so rotating or attaching a keyboard never reloads or pans the page.

The launcher icon is a vector: a tablet on its keyboard showing a Pi desktop (red top panel, a window, a cursor). It's an adaptive icon in `mipmap-anydpi-v26/` (foreground in `drawable/ic_launcher_foreground.xml`), with a duplicate of the same artwork for pre-Android 8 in `mipmap/ic_launcher.xml` and an outline variant in `drawable/ic_launcher_monochrome.xml` for Android 13+ themed icons (which use alpha only, so the filled screen would hide everything on it). Edit all three together. Don't use the official Raspberry Pi logo, which is a trademark.
