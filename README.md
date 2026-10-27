# Veyra

**The web. At your pace.**

A customizable Android browser with a calm start page, bottom navigation, local ad blocking, and practical HTTPS controls. Built with Java, Material 3 components, and Mozilla GeckoView.

**Version:** 0.4.2 · **Stage:** early alpha · **Android:** 9+ (API 28)

[Download 0.4.2 alpha](https://github.com/Tufein/Veyra/releases/tag/v0.4.2) · [Product roadmap (Nederlands)](ROADMAP.md) · [Settings design](docs/settings-design.md)

**New in 0.4.2:** direct access to Android Autofill settings.

**Included from 0.4.1:** a shared bookmark/start-page library and redesigned browser panels. [Preview and behavior](docs/library-design.md).

[Nederlandse handleiding](docs/README.nl.md) · [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Verification notes](docs/verification-0.4.md)

## Features

### Browsing

- Search or enter an address from the bottom address bar.
- Back, forward, reload, bookmarks, page sharing, and find on page.
- A searchable library with bookmarks and start-page shortcuts, explicit pin/unpin actions, linked editing, and confirmed deletion.
- Scrollable Material browser and tab panels with clear selection and separate close controls.
- Up to twelve tabs, with at most three live Gecko sessions to limit memory usage.
- Restore tab addresses after reopening the app.
- Select files for website uploads.
- Open HTTP and HTTPS links from other Android apps.

### Make it yours

- An independent Material You settings hub with search and separate appearance, home, reading, privacy, shortcut, password-provider, and about pages.
- Rounded tonal cards, a live palette preview, adaptive theme selection, Material switches, and a reading-size preview.
- Edit shortcuts and your greeting inside settings without returning to the browser. Navigation and unsaved forms survive activity recreation.
- Wallpaper-derived colors on Android 12+, with manual accents available on every supported version.
- Dark, light, or system theme.
- Mint, lavender, sky blue, and peach accent colors.
- Spacious or compact start page and an optional personal greeting.
- Add, edit, or remove up to eight website shortcuts; pin bookmarks to the start page and keep linked website names and addresses in sync.
- Website text sizes from 85% to 150%.
- DuckDuckGo, Google, Brave Search, or Ecosia as your search provider.

### Ads and tracking

- A bundled list of 56,339 domains, plus the original baseline rules.
- Local request blocking and a small set of cosmetic rules for common ad containers.
- Global on/off control and exceptions for individual website hosts.
- A counter for blocked requests in the current browsing session.

The list is bundled with the app and is not updated automatically. Blocking does not cover every ad, first-party ad delivery, video ads, WebSockets, or all service-worker traffic. Source attribution and licenses are included in the APK and documented in [third-party notices](docs/third-party.md).

### Firefox extensions

- Discover and search the Mozilla Add-ons catalog for Firefox on Android directly in Veyra.
- Install signed extensions after reviewing their requested permissions, site access, and declared data collection.
- Enable, disable, update, or uninstall installed extensions. Open their browser-action popup or options page.
- Keep installed extensions in the Gecko profile across restarts. Updates are initiated manually; new permissions require consent.

Compatibility depends on the extension and the WebExtensions APIs supported by GeckoView and Veyra. Desktop-only extensions and catalog entries outside the engine's Android version range are excluded. Downloads and extension signatures are verified by GeckoView; arbitrary package URLs and unsigned packages are not offered by the catalog UI.

**Engine migration:** 0.3.0 uses GeckoView instead of Android System WebView. Existing preferences, bookmarks, shortcuts, and saved tab addresses are retained. Cookies and website storage use a separate engine profile, so websites require signing in again. Old WebView data is not deleted or copied.

### HTTPS, passwords, and passkeys

- **Prefer HTTPS:** typed domains use HTTPS; explicit HTTP links remain available. A failed HTTPS connection never silently falls back to HTTP.
- **HTTPS only:** refuse HTTP page navigation and ordinary intercepted HTTP resource requests.
- Use the connection indicator to try HTTPS or explicitly choose HTTP when your preference permits it.
- Offer website login fields to your existing Android Autofill provider.
- Request Autofill from the password menu and open Android settings to configure your provider.
- Open an HTTPS login page in another installed browser for external passkey sign-in.

**Passkey sign-in happens in the external browser. Its login session is not transferred back to Veyra.** Direct passkeys for arbitrary websites inside Veyra require credential-provider approval as a privileged browser; this alpha does not have that integration. Passwords and passkeys remain with your selected provider. Veyra does not maintain its own credential vault.

## Try Veyra

Use a phone running Android 9 or later. The GeckoView engine ships with Veyra and is updated with the app. [Download the ARM64 APK](https://github.com/Tufein/Veyra/releases/download/v0.4.2/Veyra-0.4.2-arm64-v8a-debug.apk) for most modern phones, or choose another architecture on the [0.4.2 release page](https://github.com/Tufein/Veyra/releases/tag/v0.4.2). These are debug-signed testing builds; live-device validation remains outstanding.

The previously shared 0.2.0 test APK uses the same signing certificate as the previously shared 0.1.0 APK, allowing an update that preserves local app data. An APK built on another machine or a CI runner normally has a different debug signing key. Keeping the same application ID, a compatible version code, and the same signing key is necessary to update an existing installation.

In-place update compatibility between the published alpha builds has not been established. Uninstalling the app deletes its local data; do not uninstall to resolve a signature conflict if you need to keep that data. See the [release notes](docs/releases/v0.4.2.md).

In the app:

- **Menu → Instellingen**, or the settings icon on the start page, opens the independent settings hub.
- **Instellingen → Uitstraling** controls the theme and palette; **Startpagina** and **Snelkoppelingen** manage your personal start page.
- **Menu → Extensies**, or **Instellingen → Extensies**, opens the catalog and installed extension manager.
- **Menu → Adblocker voor deze website** controls a website exception.
- **Menu → HTTPS en verbinding** opens connection and protocol choices.
- **Menu → Wachtwoorden en passkeys** opens the external credential options.

The current app interface is in Dutch. This README is the English project guide.

## Quick start

### Requirements

- Android Studio, or JDK 21 and the Android SDK.
- Android SDK Platform 37.2 and Build Tools 37.0.0. The target SDK remains 35 and Android 9 remains the minimum.
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

# Use app-x86_64-debug.apk for the x86_64 emulator, or app-armeabi-v7a-debug.apk for 32-bit ARM.
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

On Windows, use `gradlew.bat` and configure the Android SDK path through your environment or a local `local.properties` file. On networks requiring a proxy, configure your local Gradle proxy settings. The Gradle Wrapper is included and downloads Gradle 9.8.2 with SHA-256 verification.

## Checks and CI

```sh
# Build the debug APK, run unit tests, and run Android Lint
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

# Test the bundled Gecko extension's blocking, exceptions, and HTTPS policy (Node 22+)
node --test tools/protection_test.cjs

# The JVM suite also renders real Material widgets with Robolectric on Android 9/15.
# Device checks for settings, theme recreation, protection startup, and unsigned-package rejection
./gradlew :app:connectedDebugAndroidTest

# Optional device smoke test; requires Python 3 and one connected test device
python3 tools/smoke_test.py
```

The smoke test starts a local HTTP fixture through `adb reverse`, then checks typed navigation, JavaScript, links, back navigation, bookmarks, tabs, and request blocking. Enable the adblocker and select **Prefer HTTPS** before running it. It creates test tabs and a bookmark without clearing existing app data. Set `ADB` if `adb` is not on your PATH. Screenshots are written to `app/build/smoke/`.

The Android CI workflow runs the build, unit tests, bundled-protection tests, and Lint on pushes and pull requests. It saves the debug APK and available reports as workflow artifacts. Device tests, Autofill, and passkey sign-in are outside this workflow. Robolectric settings renderings are saved under `app/build/reports/settings-ui/` and included in the CI reports artifact; these are Android-widget test renderings, not phone screenshots.

See [library and browser-panel verification](docs/verification-library.md), [settings verification](docs/verification-0.4.md), and [engine verification](docs/verification-0.3.md). Physical-device and signed-update validation remain outstanding for this alpha.

For the earlier 0.2.0 snapshot, the local build, seven unit tests, and Android Lint passed with no Lint findings. **The 0.2.0 update has not been run on a working emulator or physical phone in the development environment.** See the [verification record](docs/verification-0.2.md) for the exact checks and remaining device tests.

## Project structure

```text
.
├── app/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── java/nl/veyra/browser/
│       │   │   ├── BrowserActivity.java   # Browser chrome, tabs, native integration
│       │   │   ├── BrowserView.java       # GeckoView tab/session adapter
│       │   │   ├── SettingsActivity.java  # Material You settings
│       │   │   ├── ExtensionsActivity.java # Mozilla catalog and installed add-ons
│       │   │   ├── VeyraRuntime.java      # Shared engine, consent, built-in protection
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

The app uses native Android views and Material 3 components with a shared light/dark palette and optional Material You dynamic colors. GeckoView supplies browsing and WebExtensions; the engine is pinned in `app/build.gradle` and is updated with the app. A bundled, app-owned WebExtension implements the existing local blocklist, cosmetic filtering, per-site exceptions, and HTTP request blocking in HTTPS-only mode. Only that built-in background extension has the protection configuration/counting native message channel; websites and downloaded extensions receive no native credential bridge. Some services restrict sign-in from embedded browsers.

Tab addresses, bookmarks, preferences, cookies, website storage, and the Gecko cache remain on the device. Veyra has no application backend or added analytics. Your selected search provider, websites, external credential provider, and Gecko engine handle their own requests; this may include Safe Browsing traffic.

Third-party cookies, mixed content, page access to local files, OS backup, and device transfer are disabled. Certificate errors are not bypassed. The app does not add a JavaScript-to-Java credential bridge. Phone, mail, and map links request confirmation before opening another app.

Changing HTTPS mode recreates existing Gecko sessions to remove old navigation state. Tab addresses and cookies remain, but per-tab navigation history starts again. Tabs evicted to save memory may reload and lose unsaved form state.

To remove all local data, use **Android Settings → Apps → Veyra → Storage → Clear data**. There is no in-app clear-all-data control yet.

## Current limitations and next steps

This is an early browser project. Private browsing, download management, synchronization, a built-in credential vault, direct in-browser passkeys, fullscreen video, camera, microphone, and location access are not implemented. There is no published Play Store release or production signing process in this repository.

The next priorities are device verification of 0.2.0, broader website and Autofill compatibility, accessibility checks, and measurements of startup time, page loading, memory, and battery use. Faster performance than Samsung Internet has **not** been demonstrated. The [benchmark procedure](docs/benchmark.md) describes how to compare browsers on the same phone.

## Contributing and licensing

Bug reports and focused improvements are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) for setup, checks, and reporting guidance.

No license has been selected for Veyra's original application code. Public source availability alone does not grant reuse or redistribution rights. Bundled third-party data and tooling retain their respective licenses; see [third-party notices](docs/third-party.md) and the license headers included with the Gradle Wrapper.
