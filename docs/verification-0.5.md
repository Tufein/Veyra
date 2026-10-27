# Verification — 0.5.0 candidate

Recorded 27 October 2026 for version name **0.5.0**, version code **7**, application ID **nl.veyra.browser**. This record describes a local candidate; no v0.5.0 GitHub release/tag has been published yet. The last published alpha remains 0.4.2.

## Completed checks

| Check | Actual result |
| --- | --- |
| Debug APK build | Passed; standalone ARM64, ARMv7 and x86_64 outputs |
| Android test APK build | Passed; compiled only, not executed on device |
| JVM unit/Robolectric suite | **104 tests, 0 failures/errors/skips** |
| Android Lint | **0 findings** after removing obsolete sync resources |
| Builtin protection Node suite | **8 passed** |
| Python migration/signing suite | **8 passed** (5 migration, 3 certificate-output cases) |
| Python helper syntax | Parsed successfully |
| Public release asset preparation | Passed all three APK certificates/ABIs/package/version checks |
| Test APK certificate | Verified; matches the same fixed certificate |
| Delivery checksums | `sha256sum --check SHA256SUMS.txt`: all three APKs passed |
| Signing-key loader | Missing secrets rejected; supplied fixed key accepted; temporary key mode 0600 |
| Official add-on fixtures | uBlock Origin 1.75.0, Dark Reader 4.9.133, ClearURLs 1.27.3 downloaded with matching AMO SHA-256; **not installed/tested** |

Validation used JDK 21, Gradle 9.8.2, AGP 9.4.1, SDK Platform 37.2/Build Tools 37.0.0, GeckoView 158.0.20261021001945 and Material Components 1.12.0. Gradle reports deprecated features for a future Gradle 10 migration; this is not a Lint failure.

The JVM suite covers storage/import validation and atomicity, linked names, history bounds/private exclusion, normal-only tab snapshots, search-template policy, permission scopes/approximate-location handling, crypto tampering/wrong phrases/bounds, TLS/redirect/ETag transport and three-way sync changes/deletions/conflicts. UI checks exercise native Material views, state preservation, NL/EN, tablet navigation, large fonts and RTL. Node checks include private exception nonpersistence and cookie-count-only normal-store responses. These are not full native-Gecko/device integration tests.

## Visual evidence

These images are **Robolectric native Android-widget test renderings**, not screenshots from a physical phone. Existing [settings](settings-design.md) and [library](library-design.md) design records remain available.

| Rendering | Image |
| --- | --- |
| Tablet settings, dark NL | [settings-tablet-dark.png](images/settings-tablet-dark.png) |
| History, dark EN | [history-english-dark.png](images/history-english-dark.png) |
| Sync form, dark EN | [sync-english-dark.png](images/sync-english-dark.png) |
| Data choices, dark EN | [privacy-english-dark.png](images/privacy-english-dark.png) |
| History, RTL / 200% text | [history-rtl-large-font.png](images/history-rtl-large-font.png) |

## Signing and publication

All three candidate APKs and the test APK match the pinned certificate:

```
0a35ce54abed29880b6380ac1eedfa6f9d332d7df923545d52d6de50a9bfdfbe
```

The Android 37 signing tool emits `V2 Signer: ...`; the parser also supports the older `Signer #1 ...` form and rejects output with missing or different certificate identities. This check caught an output-format mismatch before delivery; actual signing used the intended key.

The new private key is outside the repository, and no key/password is included in release assets. **Durable owner-controlled backup and repository-secret provisioning remain open.** Public release CI requires those secrets and fails closed; normal push builds can still create development-key artifacts.

GitHub authentication and source push were restored on 27 October 2026. The earlier HTTP 401 blocker is resolved. Reading the repository public key/listing Secrets still returns **HTTP 403 Resource not accessible by integration**; no signing secrets were stored. The 0.5 alpha is prepared for direct upload from the locally verified fixed-key APKs, rather than a successful automated release build. Each actual delivery APK was rechecked for package ID, version name/code, ABI, pinned certificate and SHA-256. Automated fixed-key release CI remains blocked until repository Secrets access is granted.

## Not completed

- No successful physical-device or usable-emulator installation/instrumentation run, no actual signed update, and no hardware cookie/profile-isolation proof.
- No authenticated/interrupted download acceptance, real camera/microphone/location check or Android document-provider matrix.
- No three-extension behavioral/update acceptance. Official packages and a compiled test runner are not compatibility evidence; see [extensions](extensions.md).
- No real two-client synchronization against an owner-managed server and no independent cryptographic audit.
- No TalkBack, controller/handheld acceptance, real-site/Autofill matrix or startup/page/memory/battery measurements.
- Public 0.5 upload/publication is in progress; a successful workflow or stable release is not implied.

Follow [device-validation.md](device-validation.md). All 1.0 release gates stay open until their actual evidence exists. Keep [migration instructions](signing-and-migration.md) with every first fixed-key APK handoff.
