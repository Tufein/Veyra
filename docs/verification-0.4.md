# Verification: independent settings and design (0.4.0 development)

This is a development candidate. The published GitHub alpha remains 0.3.0; this change does not create a new release tag.

## Build and automated checks

```sh
./gradlew --no-daemon --max-workers=3 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
node --test tools/protection_test.cjs
```

Local validation completed successfully with JDK 21, Gradle 9.8.2, AGP 9.4.1, SDK Platform 37.2, and Build Tools 37.0.0:

- All 31 JVM tests passed, with zero failures or skips.
- All 6 browser-protection tests passed, with zero failures or skips.
- Android Lint reported zero errors and zero warnings.
- Standalone debug APKs for ARM64, ARMv7, and x86_64 and the connected-test APK were built.

The JVM suite contains:

- 7 existing navigation/blocklist tests.
- 5 existing Mozilla catalog and download-origin tests.
- 13 settings tests using Robolectric and actual Material widgets: independent hub/detail navigation, search, theme recreation, accents, shortcut validation, unsaved form state, large-text layout, keyboard reading-size adjustments, extension-manager navigation, password-provider refresh after Android settings, and Android 9 fallback. Most use Android 15; the fallback test uses Android 9.
- 6 shared shortcut-storage tests: legacy defaults, add/edit/remove, host/port URLs, rejection of unsupported input, capacity, and preserving damaged storage on read.

The browser-protection suite contains 6 JavaScript tests. The connected-test APK contains the updated hub/detail device scenarios, but building that APK is not a device-test pass.

Native Robolectric renderings are generated under `app/build/reports/settings-ui/`. Reviewed copies appear in [the design document](settings-design.md). These are test renderings, not physical-device screenshots.

## Data compatibility and behavior

- Existing theme, accent, dynamic-color, compact-home, nickname, text-zoom, search-provider, blocking, HTTPS, exception, shortcut, bookmark, and tab preference keys are retained.
- Home and settings use a shared shortcut store. Reads do not rewrite preferences; explicit edits use the existing JSON shape. Invalid URLs and out-of-range edits are rejected.
- Settings now owns shortcut editing and password-provider configuration information. It does not close itself and request a browser dialog for those flows.
- Back history, current section, scroll positions, and unsaved name/shortcut drafts are saved in activity state.
- Browser engine behavior and extension installation are outside this change's scope. The remaining device-validation limits from [0.3.0](verification-0.3.md) still apply.

## Remaining device checks

Run the connected settings tests and manually verify Android 9 and Android 12+ with real wallpaper colors; hardware/predictive Back; keyboard/insets; 200% font scale; RTL; controller/keyboard focus; and returning to a live browser tab without losing its URL or session. Test a signed update path before publishing another installable release.
