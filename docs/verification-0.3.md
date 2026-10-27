# Verification: Material You and Firefox extensions (0.3.0)

## Automated checks

The following completed in the cloud workspace with JDK 21, Gradle 9.8.2, AGP 9.4.1, Android SDK Platform 37.2, and Build Tools 37.0.0:

```sh
./gradlew --no-daemon --max-workers=3 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
node --test tools/protection_test.cjs
```

- Debug APKs: built for ARM64, ARMv7, and x86_64.
- JVM tests: **12 passed**, zero failures/skips (7 navigation/blocklist tests; 5 Mozilla catalog/download-origin/compatibility tests).
- Protection extension tests: **6 passed**, zero failures/skips. These exercise the bundled snapshot, first-party/domain boundaries, actual background request interception, website exceptions inside third-party frames, global off, and HTTPS-only resource blocking.
- Android Lint: **zero errors and zero warnings**.
- Connected-test APK: compiled successfully. Compilation is not a device-test pass.
- Live Mozilla catalog: queried successfully, including Android version metadata. A ClearURLs test package was downloaded over verified HTTPS and matched the catalog's SHA-256. This alone does not establish app-side installation or runtime behavior.

## Device validation

The Android 35 software emulator booted, but its Android system services stalled: APK installation did not complete, including the small test APK, and screen capture stalled before Veyra could launch. Both streamed and non-streamed installation were attempted. A second Android 12 software-emulation image did not finish booting within the bounded readiness check. Neither image produced a completed app/device test run.

**Device tests and visual inspection remain unverified.** Do not infer that the settings screen, live Gecko browsing, popup handling, or extension installation passed on a device from a successful APK build. Run the checks below on a working emulator or isolated test phone before treating this engine migration as device-ready.

```sh
# Choose the APK matching the device ABI.
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
./gradlew :app:connectedDebugAndroidTest
python3 tools/smoke_test.py
```

The connected tests exercise Material settings and stored preference keys, light-theme recreation, bundled-protection startup, and rejection of an unsigned XPI. Settings screenshots are saved to the target app's cache as `settings-dark.png` and `settings-light.png`. The optional signed-package test is skipped unless a fixture is explicitly supplied; keep that distinction in test reports.

### Optional signed extension test

Use a fresh, isolated test installation. Obtain the current ClearURLs XPI through the official metadata endpoint `https://addons.mozilla.org/api/v5/addons/addon/clearurls/`, download `current_version.file.url` with TLS verification, and verify its SHA-256 against `current_version.file.hash`. Do not check the downloaded third-party package into this repository.

After installing the app, place that verified file in the debuggable target app's cache:

```sh
adb shell run-as nl.veyra.browser mkdir -p cache
adb shell "run-as nl.veyra.browser sh -c 'cat > cache/signed-test-addon.xpi'" < /path/to/verified-clearurls.xpi
./gradlew :app:connectedDebugAndroidTest
```

The signed-package test requires the real permission dialog, taps its consent button, installs the signed package, checks disable/enable, checks survival across activity recreation, and uninstalls its test extension. It skips if ClearURLs is already installed, preserving an existing user installation. This test does not claim persistence across a full process restart; check that separately by installing an extension through the manager, force-stopping the app, and reopening it.

Also check wallpaper colors on Android 12+, manual accents and dark/light modes, small screens and large font settings, browser-action popups/options, tab creation/update/close from an extension, website sign-in/Autofill, file upload, and the existing browsing smoke flow. Firefox extension API support varies; test the specific extensions intended for release.

## Migration notes

Preferences, shortcuts, bookmarks, and saved HTTP(S) tab addresses retain their existing storage keys. Gecko uses a separate cookie/storage profile. Existing WebView website sessions do not transfer and are not deleted. Debug APK updates require the same signing key as the installed app; the cloud debug key is not the earlier privately shared APK's signing key.

GeckoView and its dependencies require compiling against SDK 37.2. The target remains SDK 35 and the minimum remains SDK 28. Dependency source and license links are in `third-party.md`.
