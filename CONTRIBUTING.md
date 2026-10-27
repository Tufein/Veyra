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
# With a connected isolated test device:
./gradlew :app:connectedDebugAndroidTest
```

Add meaningful tests when changing URL parsing, HTTPS policy, blocking, or other behavior with a concrete regression risk. For UI work, check the affected flow on a device or emulator, including both appearance modes and a small phone screen. Record checks you could not complete.

The optional `tools/smoke_test.py` requires Python 3, `adb`, one connected test device, the adblocker enabled, and **Prefer HTTPS** mode. It creates test tabs and a bookmark. Use a test device or account for login and Autofill work.

CI validates the build, unit tests, and Lint. CI debug APKs normally use a different signing key from locally shared APKs; verify signing compatibility before an update that should retain existing app data.

## Report an issue

Include the Android version, device model, WebView version, Veyra version, relevant settings, reproduction steps, and expected/observed behavior. Share a public example URL when possible. Remove passwords, cookies, tokens, and personal account details from screenshots and logs.

For suspected credential or privacy vulnerabilities, avoid publishing sensitive details in a public issue. Use a private reporting channel offered by the repository owner, if available.

## Open a pull request

Describe the problem, resulting behavior, checks performed, and remaining limitations. Include real screenshots for visual changes when available; do not present mockups as verified app output.

Veyra's original code does not yet have a selected license. Do not add a root license or change third-party licensing terms as an incidental part of another change.
