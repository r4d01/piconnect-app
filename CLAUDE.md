# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A minimal Android app that opens the Raspberry Pi Connect web interface (`https://connect.raspberrypi.com`) in a fullscreen WebView — no native UI beyond the WebView itself.

## Build & Run

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Release build
./gradlew assembleRelease

# Clean
./gradlew clean
```

## Tests & Lint

```bash
# Unit tests
./gradlew test

# Single unit test
./gradlew test --tests "com.r4d01.piconnect.MainActivityTest#testMethod"

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

## Architecture

Single-activity Java app. `MainActivity.java` hosts a `WebView` configured for fullscreen with:
- JavaScript enabled
- `WebViewClient` to keep navigation within the app
- `WebChromeClient` for fullscreen video/media support
- Hardware back button mapped to WebView history navigation

The app targets the Raspberry Pi Connect URL directly — there is no navigation UI, settings screen, or local content.
