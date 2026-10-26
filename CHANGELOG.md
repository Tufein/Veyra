# Changelog

## 0.2.0 — 2026-10-26

### Added

- Redesigned start page with soft gradients, rounded cards, and quick settings access.
- Dark, light, and system appearance with four accent colors.
- Compact start page, personal greeting, editable shortcuts, website text size, and search-provider preferences.
- Bundled domain blocking with 56,339 domains, baseline rules, cosmetic hiding, and per-host exceptions.
- Prefer-HTTPS and HTTPS-only modes, explicit protocol choice, and a blocked-HTTP screen with an HTTPS action.
- Android Autofill integration and an explicit external-browser route for passkey sign-in.
- HTTPS-policy, search-provider, and bundled-blocklist unit tests.

### Notes

- Native passkeys inside Veyra and a built-in password vault remain unimplemented.
- The local build, seven unit tests, and Android Lint passed. Device verification of this update remains outstanding.

## 0.1.0 — 2026-10-26

- Initial native Android browser using Android System WebView.
- Bottom address bar, search, navigation, tabs, bookmarks, sharing, and find on page.
- Local start page, starter tracker rules, file selection for uploads, and tab-address restoration.
- Address-policy unit tests and a connected-device smoke-test script.

## Repository setup

- Imported the delivered 0.2.0 project into `Tufein/Veyra`.
- Added an English project guide while retaining the Dutch guide.
- Added contribution guidance and an Android build/test/Lint workflow.
