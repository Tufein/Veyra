# Veyra

**The web. At your pace.**

A customizable Android browser with a calm start page, bottom navigation, local ad blocking, and practical HTTPS controls. Built with Java and native Android views, powered by the Android System WebView installed on your device.

**Version:** 0.2.0 · **Stage:** early alpha · **Android:** 9+ (API 28)

[Nederlandse handleiding](docs/README.nl.md) · [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Verification notes](docs/verification-0.2.md)

## Features

### Browsing

- Search or enter an address from the bottom address bar.
- Back, forward, reload, bookmarks, page sharing, and find on page.
- Up to twelve tabs, with at most three live WebViews to limit memory usage.
- Restore tab addresses after reopening the app.
- Select files for website uploads.
- Open HTTP and HTTPS links from other Android apps.

### Make it yours

- Dark, light, or system theme.
- Mint, lavender, sky blue, and peach accent colors.
- Spacious or compact start page and an optional personal greeting.
- Add, edit, or remove up to eight website shortcuts.
- Website text sizes from 85% to 150%.
- DuckDuckGo, Google, Brave Search, or Ecosia as your search provider.

### Ads and tracking

- A bundled list of 56,339 domains, plus the original baseline rules.
- Local request blocking and a small set of cosmetic rules for common ad containers.
- Global on/off control and exceptions for individual website hosts.
- A counter for blocked requests in open tabs.

The list is bundled with the app and is not updated automatically. Blocking does not cover every ad, first-party ad delivery, video ads, WebSockets, or all service-worker traffic. Source attribution and licenses are included in the APK and documented in [third-party notices](docs/third-party.md).

### HTTPS, passwords, and passkeys

- **Prefer HTTPS:** typed domains use HTTPS; explicit HTTP links remain available. A failed HTTPS connection never silently falls back to HTTP.
- **HTTPS only:** refuse HTTP page navigation and ordinary intercepted HTTP resource requests.
- Use the connection indicator to try HTTPS or explicitly choose HTTP when your preference permits it.
- Offer website login fields to your existing Android Autofill provider.
- Request Autofill from the password menu and open Android settings to configure your provider.
- Open an HTTPS login page in another installed browser for external passkey sign-in.

**Passkey sign-in happens in the external browser. Its login session is not transferred back to Veyra.** Direct passkeys for arbitrary websites inside Veyra require credential-provider approval as a privileged browser; this alpha does not have that integration. Passwords and passkeys remain with your selected provider. Veyra does not maintain its own credential vault.

## Try Veyra

Use a phone running Android 9 or later with an up-to-date Android System WebView. Build the debug APK using the instructions below, or download the debug APK artifact from a successful [Android CI run](https://github.com/Tufein/Veyra/actions/workflows/android.yml) once available.

The previously shared 0.2.0 test APK uses the same signing certificate as the previously shared 0.1.0 APK, allowing an update that preserves local app data. An APK built on another machine or a CI runner normally has a different debug signing key. Keeping the same application ID, a compatible version code, and the same signing key is necessary to update an existing installation.

In the app:

- **Menu → Veyra aanpassen** opens appearance, search, text size, and protection settings.
- **Menu → Adblocker voor deze website** controls a website exception.
- **Menu → HTTPS en verbinding** opens connection and protocol choices.
- **Menu → Wachtwoorden en passkeys** opens the external credential options.

The current app interface is in Dutch. This README is the English project guide.

## Quick start

### Requirements

- Android Studio, or JDK 17/21 and the Android SDK.
- Android SDK Platform 35 and Build Tools 35.0.0.
- An Android phone or emulator running API 28 or later.
- Network access for the first Gradle dependency download.

### Android Studio

1. Clone `https://github.com/Tufein/Veyra.git` and open the project root.
2. Allow Gradle to sync and install any requested SDK components.
3. Select your Android device or emulator and run the `app` configuration.

### Command line

```sh
git clone https://github.com/Tufein/Veyra.git
cd Veyra

export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat` and configure the Android SDK path through your environment or a local `local.properties` file. On networks requiring a proxy, configure your local Gradle proxy settings. The Gradle Wrapper is included and downloads Gradle 8.11.1 with SHA-256 verification.

## Checks and CI

```sh
# Build the debug APK, run unit tests, and run Android Lint
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

# Optional device smoke test; requires Python 3 and one connected test device
python3 tools/smoke_test.py
```

The smoke test starts a local HTTP fixture through `adb reverse`, then checks typed navigation, JavaScript, links, back navigation, bookmarks, tabs, and request blocking. Enable the adblocker and select **Prefer HTTPS** before running it. It creates test tabs and a bookmark without clearing existing app data. Set `ADB` if `adb` is not on your PATH. Screenshots are written to `app/build/smoke/`.

The Android CI workflow runs the build, unit tests, and Lint on pushes and pull requests. It saves the debug APK and available reports as workflow artifacts. Device tests, Autofill, and passkey sign-in are outside this workflow.

For the delivered 0.2.0 snapshot, the local build, seven unit tests, and Android Lint passed with no Lint findings. **The 0.2.0 update has not been run on a working emulator or physical phone in the development environment.** See the [verification record](docs/verification-0.2.md) for the exact checks and remaining device tests.

## Project structure

```text
.
├── app/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── java/nl/veyra/browser/
│       │   │   ├── BrowserActivity.java   # Browser UI, tabs, settings, integration
│       │   │   ├── TrackerBlocker.java    # Local domain matching and list loading
│       │   │   └── UrlPolicy.java         # Address resolution and HTTPS policy
│       │   ├── assets/                   # Blocklist, attribution, license texts
│       │   └── res/                      # Theme, icon, and backup rules
│       └── test/                         # URL and blocking unit tests
├── docs/                                 # Verification, benchmark, Dutch guide
├── tools/smoke_test.py                    # Optional connected-device test
├── .github/workflows/android.yml          # Build, unit tests, Lint, artifacts
├── gradle/wrapper/                        # Pinned Gradle distribution
├── CHANGELOG.md
└── CONTRIBUTING.md
```

## Architecture and privacy

The app uses native Android views and the platform WebView with no UI framework dependency. Engine capabilities and updates follow the installed WebView provider. Some services restrict sign-in from embedded browsers.

Tab addresses, bookmarks, preferences, cookies, website storage, and the WebView cache remain on the device. Veyra has no application backend or added analytics. Your selected search provider, websites, external credential provider, and WebView provider handle their own requests; this may include Safe Browsing traffic.

Third-party cookies, mixed content, page access to local files, OS backup, and device transfer are disabled. Certificate errors are not bypassed. The app does not add a JavaScript-to-Java credential bridge. Phone, mail, and map links request confirmation before opening another app.

Changing HTTPS mode recreates existing WebViews to remove old navigation state. Tab addresses and cookies remain, but per-tab navigation history starts again. Tabs evicted to save memory may reload and lose unsaved form state.

To remove all local data, use **Android Settings → Apps → Veyra → Storage → Clear data**. There is no in-app clear-all-data control yet.

## Current limitations and next steps

This is an early browser project. Private browsing, download management, synchronization, a built-in credential vault, direct in-browser passkeys, fullscreen video, camera, microphone, and location access are not implemented. There is no published Play Store release or production signing process in this repository.

The next priorities are device verification of 0.2.0, broader website and Autofill compatibility, accessibility checks, and measurements of startup time, page loading, memory, and battery use. Faster performance than Samsung Internet has **not** been demonstrated. The [benchmark procedure](docs/benchmark.md) describes how to compare browsers on the same phone.

## Contributing and licensing

Bug reports and focused improvements are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) for setup, checks, and reporting guidance.

No license has been selected for Veyra's original application code. Public source availability alone does not grant reuse or redistribution rights. Bundled third-party data and tooling retain their respective licenses; see [third-party notices](docs/third-party.md) and the license headers included with the Gradle Wrapper.
