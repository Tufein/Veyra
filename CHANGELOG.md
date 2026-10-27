# Changelog

## 0.4.1 — 2026-10-27 (alpha)

- Replace the long browser menu and tab dialog with scrollable Material bottom panels, named icon actions, clear tab selection, and separate close targets.
- Add one searchable library for bookmarks and start-page shortcuts, with pin/unpin, editing, confirmed deletion, and shared website rows.
- Keep linked names and addresses consistent using atomic writes to the existing JSON preferences; reject duplicates and unsafe addresses.
- Refine the home layout, compact settings categories, remove duplicate detail headings, and use the same website monograms throughout.
- Preserve library search, selected tab, and editor drafts across recreation; add storage and native-widget regression tests and UI renderings.

## 0.4.0 — 2026-10-27 (alpha)

- Turn settings into an independent searchable hub with dedicated detail screens and predictable Back navigation.
- Move shortcut management into a full settings editor with input validation and shared legacy preference storage.
- Retain navigation, scroll position, and unsaved name/shortcut input across recreation and theme changes.
- Refine Material typography, tonal surfaces, palette previews, accent selection, and the start page's use of the selected colors.
- Stack theme choices on small screens with large text, preserve reduced-motion preferences, and improve accessible labels and tap targets.
- Add Android 9/15 Robolectric regression tests and rendered UI previews; update connected-device settings tests.
- Document the larger product roadmap, release gates, and priorities for signing, stability, daily browsing, extensions, handhelds, and privacy.


## 0.3.0

- Replace the settings dialog with a full-screen Material 3 interface, wallpaper colors on Android 12+, light/dark/system selection, accent swatches, switches, and reading-size preview.
- Apply shared Material colors and Material dialogs to the browser chrome; retain existing settings keys and user bookmarks/shortcuts.
- Replace System WebView with GeckoView 158 to run signed Firefox WebExtensions. Website sessions use a separate profile and require signing in again.
- Add a Mozilla Add-ons Android catalog, explicit permission/collection consent, extension toggles, manual updates, uninstall, browser-action popups, and options pages.
- Port bundled ad blocking, cosmetic rules, exceptions, and HTTPS resource blocking into an app-owned built-in WebExtension.
- Update Android build tooling for GeckoView's SDK requirements and generate separate ARM64, ARMv7, and x86_64 APKs.
- Add catalog/protection regression tests and connected-device checks for Material settings and extension verification.


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
