# AGENTS.md — Veyra

Instructions for coding agents (OpenCode, Claude Code, Codex and similar). `CONTRIBUTING.md` is
loaded alongside this file through `opencode.json` and contains the full release and signing rules.

## Project

Native Android browser built with Java, Material 3 and Mozilla **GeckoView**: tabs and private tabs,
start page, bookmark/shortcut library, history, downloads, Material You settings, Dutch/English.

- Package / application id: `nl.veyra.browser`
- Version: `app/build.gradle`, currently 1.0.0-rc.6 / 13 (release candidate, not stable 1.0)
- compileSdk 37.2, minSdk 28, targetSdk 35, **JDK 21**, Android SDK Platform 37.2 and Build Tools 37.0.0
- Language: **Java**, Groovy Gradle; helper tools in Python 3 and Node

## Layout

| Path | What lives there |
| --- | --- |
| `app/src/main/java/nl/veyra/browser/` | App sources |
| `app/src/test/` | JVM/Robolectric tests (incl. `SettingsActivityTest` renderings) |
| `app/src/androidTest/` | Connected-device tests |
| `tools/` | Release readiness, signing, publishing, smoke and protection tests (`test_*.py`, `protection_test.cjs`) |
| `docs/` | Verification reports per version, signing/migration, privacy, sync, Dutch guide |

## Build and test

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:testReleaseUnitTest \
  :app:syncHttpsIntegrationTest :app:lintDebug :app:lintRelease
node --test tools/protection_test.cjs
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew :app:connectedDebugAndroidTest   # only with an isolated test device
```

## Rules

- Update the English README, the Dutch guide (`docs/README.nl.md`) and `CHANGELOG.md` when behaviour changes.
- Robolectric renderings are test renderings, never call them phone screenshots.
- Keep SDK paths, build output, keystores, credentials and device data out of commits.
- Public releases use the fixed key from repository secrets and must pass the pinned-certificate check
  (`docs/signing-certificate-sha256.txt`). Never fall back to a debug key.
- Mark implementation, automated checks and device verification separately in `ROADMAP.md`.

## Release process

Push a tag `vX.Y.Z[-rc.N]`; `.github/workflows/release.yml` checks readiness
(`tools/release_readiness.py`), builds and signs, verifies `SHA256SUMS.txt` and publishes the GitHub
release. `codex/publish-*` branches use `publish-prebuilt.yml`. Per CONTRIBUTING, every completed app
change gets a new versioned GitHub release with notes, checksums and a direct ARM64 link; never
overwrite an existing release.
