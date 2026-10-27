# Verification: library and browser panels

This development update follows the published 0.4.0 alpha. It does not replace the APKs attached to that release.

## Automated validation

```sh
./gradlew --no-daemon --max-workers=3 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
node --test tools/protection_test.cjs
```

Validation passed with **59 JVM tests, 6 browser-protection tests, zero failures/skips, and zero Lint findings**. Three standalone debug APKs and the connected-test APK were built using JDK 21, Gradle 9.8.2, AGP 9.4.1, SDK Platform 37.2, and Build Tools 37.0.0.

The suite covers shared bookmark/shortcut changes, legacy storage, preserving damaged storage on read, pin capacity without partial writes, duplicate/unsafe addresses, linked edit collisions, removing only a pin, confirmed bookmark deletion, library search/tab/editor restoration, returning an address to the existing browser, disabled menu actions, and independent tab selection/close actions. Native UI renderings cover light/dark palettes and small screens with large text.

The existing settings suite also covers the compact shortcut list and its library route. New shortcuts saved in settings are also bookmarked; existing legacy/default shortcuts are left untouched until an explicit edit.

## Data behavior

There is no eager data migration. Existing preference keys and the `{title,url}` JSON shape remain unchanged. Links between the two lists use normalized HTTP(S) URLs. Linked edits write both keys with one `SharedPreferences.Editor`. Removing a shortcut keeps an existing bookmark; removing a bookmark and its pin requires a confirmation. Invalid and duplicate inputs fail before any writes.

Browser menu/library returns use validated addresses. Launching a website from the library through settings uses the existing browser Activity with `CLEAR_TOP | SINGLE_TOP`; direct browser-library navigation returns a result to the current browser. Saved tab state and the Gecko profile are not rewritten by the saved-site store.

## Limits

The Robolectric renderings use the actual Android/Material view tree, not a physical device. They do not validate live Gecko browsing, native Back gestures, OEM wallpaper extraction, keyboard/inset behavior, or extension compatibility. The connected-test APK builds; no connected-device pass is claimed. Signing and data-preserving update limitations remain as documented for 0.4.0.
