# Contributing to Veyra

Veyra is an early Android browser project. Keep changes focused and distinguish verified behavior from planned features.

## Set up

Use Android Studio or JDK 21 with Android SDK Platform 37.2 and Build Tools 37.0.0. Open the repository root and let Gradle sync. The [README](README.md#quick-start) contains command-line instructions.

Create a branch for your change. Keep local SDK paths, build output, signing keys, credentials, and device data out of commits. The supplied `.gitignore` covers standard generated files and keystores.

## Make a change

- Follow the existing Java and native Android view implementation.
- Preserve tab restoration, keyboard behavior, bottom navigation, and Android 9 compatibility.
- Validate URLs before navigating. Preserve certificate checks and the selected HTTPS policy.
- Delegate credentials to Android and the user's chosen provider. Do not add a generic JavaScript bridge for arbitrary websites to request credentials.
- Keep extension signature/TLS verification and permission prompts enabled. Do not expose native messaging to websites or downloaded extensions.
- Keep blocklist attribution and license notices with any list changes.
- Update the English README, Dutch guide where applicable, and changelog when behavior changes.

## Check your work

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
node --test tools/protection_test.cjs
python3 -m unittest discover -s tools -p 'test_*.py'
# With a connected isolated test device:
./gradlew :app:connectedDebugAndroidTest
```

Add meaningful tests when changing URL parsing, HTTPS policy, blocking, or other behavior with a concrete regression risk. For UI work, run the Robolectric settings regression tests and inspect their native-widget renderings, then check the affected flow on a device or emulator, including both appearance modes and a small phone screen. Record checks you could not complete.

The optional `tools/smoke_test.py` requires Python 3, `adb`, an explicit `--serial` and `--confirm-isolated-test-device`, Dutch app language, the adblocker enabled, and **Prefer HTTPS** mode. It creates test tabs and a bookmark. Use a test device or account for login and Autofill work.

CI validates the build, unit tests, and Lint. Push-build artifacts use a development key. Public versioned releases must use the fixed key from repository Secrets and pass the pinned-certificate check; never fall back to a random debug key for a public release. Keep an independent owner-controlled encrypted key backup. See [signing and migration](docs/signing-and-migration.md).

## Report an issue

Include the Android version, device model, GeckoView version, Veyra version, relevant settings, reproduction steps, and expected/observed behavior. Share a public example URL when possible. Remove passwords, cookies, tokens, and personal account details from screenshots and logs.

For suspected credential or privacy vulnerabilities, avoid publishing sensitive details in a public issue. Use a private reporting channel offered by the repository owner, if available.

## Open a pull request

Describe the problem, resulting behavior, checks performed, and remaining limitations. Include real screenshots for visual changes when available; do not present mockups as verified app output.

Veyra's original code does not yet have a selected license. Do not add a root license or change third-party licensing terms as an incidental part of another change.


## Product direction and UI evidence

[ROADMAP.md](ROADMAP.md) records priorities, dependencies, and release gates. Mark implementation, automated checks, and device verification separately. A development version bump does not authorize or establish a published release.

The settings hub is a separate activity with internal detail navigation. Keep edits inside this activity, retain the existing preference keys, and preserve current section, back stack, and unsaved form input during recreation. Home, bookmarks and shortcuts share `SavedSitesStore`.

`SettingsActivityTest` renders real Material widgets with Robolectric native graphics. Generated images live in `app/build/reports/settings-ui/` and may be used for UI review when labeled as test renderings. Do not describe them as screenshots captured on a phone. Keep the connected-device test scenarios aligned with the hub and detail pages.


## APK delivery

The repository owner requests a downloadable APK for every completed app change. Build and validate the APKs, publish a new versioned GitHub alpha release with accurate notes and checksums, and verify the public download links before handing off. Include the direct ARM64 download link. Do not finish with source changes alone or overwrite an existing release. Preserve signing/update limitations until stable signing is configured. If an external publication step is blocked, deliver the locally verified APKs, preserve the source on GitHub and state the actual blocker; do not substitute an incompatible key or claim a release that does not exist. Follow the [device acceptance matrix](docs/device-validation.md) and [maintenance procedure](SECURITY.md).
