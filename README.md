# Veyra

**The web. At your pace.**

A native Android browser built with Java, Material 3 and Mozilla GeckoView. A calm start page, independent settings and a shared bookmark/shortcut library keep everyday controls close at hand.

**Development:** 0.5.0 candidate · **Latest published alpha:** 0.4.2 · **Android:** 9+ (API 28).

[Published releases](https://github.com/Tufein/Veyra/releases) · [Nederlandse handleiding](docs/README.nl.md) · [Roadmap](ROADMAP.md) · [Changelog](CHANGELOG.md) · [0.5 verification](docs/verification-0.5.md)

The candidate is not a stable 1.0 release. Physical-device acceptance, actual signed-update validation, three-extension compatibility and live two-device sync remain open. The new fixed signing key requires [migration from old alpha installations](docs/signing-and-migration.md). **Export before uninstalling an existing app.**

## Candidate features

- Bottom address/navigation controls, up to twelve normal/private tabs, find on page, sharing, file uploads and external HTTP(S) links.
- A searchable bookmark/start-page library with linked names/URLs, pin/unpin, ordering and confirmed deletion; up to eight start-page sites. Large lists load in pages.
- Local searchable history with selective deletion, excluded private visits and a 2000-address bound.
- Downloads from the original Gecko response, preserving the website session without reissuing POST requests; Android document destinations, progress, cancel, interrupted-transfer recovery and opening files. Retry reopens the website.
- Independent Material You settings with search, dedicated pages, light/dark/system palettes, Android 12+ wallpaper colors, manual accents, live previews, tablet navigation and Dutch/English localization.
- Personal greeting, compact home, text zoom 85–150%, built-in search providers and a validated custom HTTPS search template. Android default-browser selection.
- Versioned local bookmark/shortcut backups with optional preferences and normal-tab addresses, validated before import. No cookies, passwords or private tabs.
- Website camera/microphone/location consent, exact-origin decisions, a recording-stop action, per-host reset, normal cookie counts and selective data clearing.
- Manual encrypted bookmark/start-page [synchronization with an own HTTPS/WebDAV server](docs/sync.md), conditional ETag writes, three-way conflict/deletion handling and optional Keystore-encrypted connection storage.
- Keyboard shortcuts, visible focus, fullscreen media, configurable 2–4 resident page sessions and explicit retry after renderer failure. Evicted pages can reload and lose form state.

### Firefox extensions and blocking

Search the Mozilla Add-ons Android catalog, inspect requested permissions/site access/declared data collection, and install signed packages after consent. Manage enable/disable/update/uninstall, supported browser-action popups and options; recovery can disable all user extensions. New update permissions require consent.

Compatibility depends on GeckoView and Veyra's implemented WebExtensions APIs. uBlock Origin, Dark Reader and ClearURLs are **device-test candidates**, not verified recommendations. Unsupported tab pinning, muting, discarded/reader/container creation is rejected. Private tabs do not enable user-installed extensions. Only the app-owned protection background receives the restricted native message bridge; websites and downloaded extensions do not.

Bundled blocking includes 56,339 domains plus baseline rules, limited cosmetic filtering, per-host exceptions and session counts. The list does not auto-update and does not cover all first-party/video ads, WebSockets or service-worker traffic. See [third-party notices](docs/third-party.md). Private exceptions stay in memory rather than extension storage.

### HTTPS, passwords and privacy

Prefer-HTTPS never silently downgrades after failure; explicit HTTP remains possible in that mode. HTTPS-only rejects HTTP navigation and ordinary intercepted HTTP resources. Certificate validation remains enabled.

Normal tabs integrate with Android Autofill and link directly to the provider settings. Veyra has no password vault. Passkey sign-in can open an external browser; **that login session stays in the external browser**. Direct arbitrary-website passkeys require a provider/browser integration that is not available here. Autofill is disabled in private tabs.

Private tabs use real Gecko private contexts and are excluded from persisted normal tab/history data. Intentionally saved files, bookmarks and copied text can persist. Cookie isolation after crashes/process termination still needs device validation. Read the [privacy/data guide](docs/privacy.md) before relying on alpha behavior.

## APKs and signing

The last published release is [0.4.2](https://github.com/Tufein/Veyra/releases/tag/v0.4.2), with an [ARM64 APK](https://github.com/Tufein/Veyra/releases/download/v0.4.2/Veyra-0.4.2-arm64-v8a-debug.apk). This link remains the old build until the candidate is actually released. Public builds are debuggable alpha APKs, not Play Store production packages.

The 0.5 candidate introduces a new fixed certificate because the earlier CI debug private keys were not retained. [Migration instructions](docs/signing-and-migration.md) explain a read-only USB export before removing an older installation. The release workflow requires the fixed key in GitHub Secrets and verifies every APK against [the public certificate pin](docs/signing-certificate-sha256.txt); it stops when secrets are missing. GitHub authentication and source push are restored. The repository Secrets API returns HTTP 403, so automated release-key provisioning remains blocked; a 0.5 draft release is prepared, but direct asset upload to `uploads.github.com` returns HTTP 401. The active environment reports no configured secret bindings; the existing secure `GH_TOKEN` requirement needs a working binding for the upload host. No public 0.5 APK is available yet. An independent owner-controlled backup of the private key is also required.

Release APKs are standalone per ABI: `arm64-v8a` for most modern phones, `armeabi-v7a` for older 32-bit ARM, `x86_64` for matching emulators/devices. Check version, certificate and `SHA256SUMS.txt` before installing. Never uninstall merely to dismiss a signature-conflict message if you need the old data.

## Build and check

Use Android Studio, or JDK 21 and Android SDK Platform 37.2 / Build Tools 37.0.0. Compile SDK is 37.2; target SDK remains 35, minimum 28. The included Gradle 9.8.2 wrapper verifies its distribution checksum. GeckoView is pinned in `app/build.gradle` and ships with the app.

```sh
git clone https://github.com/Tufein/Veyra.git
cd Veyra
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew --no-daemon --max-workers=3 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
node --test tools/protection_test.cjs
python3 -m unittest discover -s tools -p 'test_*.py'
```

On Windows use `gradlew.bat`. Keep SDK paths, proxy credentials and keystores out of Git. Developer builds without configured signing use a local debug key; they are not automatically compatible with published APKs. `tools/run_signed_build.py --help` describes file-based local signing without password command arguments.

Build outputs: `app/build/outputs/apk/debug/app-<ABI>-debug.apk`. CI runs build/JVM/Lint/protection/migration checks and retains APK/report artifacts. Push-build artifacts use a development key; only the versioned release workflow requires the pinned fixed certificate.

JVM tests render native Material widgets under Robolectric. Renderings in `app/build/reports/settings-ui/` and [the design notes](docs/settings-design.md) are test renderings, not phone screenshots. No completed physical-device result is claimed. Follow the [isolated-device acceptance guide](docs/device-validation.md) for instrumentation, optional official extension fixtures and browser smoke tests. See [benchmarking](docs/benchmark.md) for actual startup/page/memory/battery measurement; no speed advantage over another browser has been established.

## Project and maintenance

`BrowserActivity` owns browser chrome and tabs; `BrowserView` adapts Gecko sessions; `VeyraRuntime` owns the runtime, extension consent and builtin protection. Settings and data tools use independent native Material activities. Local store/backup/sync components validate data before replacing preferences.

Veyra adds no analytics or hosted account backend. Local history, prefs, cookies and cache stay on-device unless you explicitly export or use encrypted sync. Websites, search providers, external credential apps and Gecko services make their own network requests. OS app backup/transfer is disabled. Clear selected data in the app or use Android app-storage controls; external files and sync-server files need separate deletion.

[Contributing](CONTRIBUTING.md) · [Security and maintenance](SECURITY.md) · [Release candidate notes](docs/releases/v0.5.0.md)

No license has been selected for Veyra's original application code. Public source availability alone does not grant reuse/redistribution rights. Third-party data and tooling retain their own licenses.
