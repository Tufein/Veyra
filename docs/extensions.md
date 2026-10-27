# Extension support and validation

The catalog uses Mozilla Add-ons Android metadata and offers signed public packages within the engine's declared Android compatibility range. The detail prompt shows version, source and required permissions; Gecko's actual install/update consent is authoritative and includes declared site/data access. Unsupported API behavior can still make an otherwise installable extension unusable.

## Compatibility matrix

Prepared on 27 October 2026. Official package downloads and SHA-256 hashes were checked against AMO; package signature acceptance and actual behavior require Gecko on a device. Exact versions/hashes/sources are recorded in [addon-fixtures-0.5.json](addon-fixtures-0.5.json).

| Candidate | Fixture version | Official download/hash | Install/manage on device | Popup/options/tabs/site behavior | Update with new rights |
| --- | --- | --- | --- | --- | --- |
| uBlock Origin | 1.75.0 | Checked | Not run | Not run | Not run |
| Dark Reader | 4.9.133 | Checked | Not run | Not run | Not run |
| ClearURLs | 1.27.3 | Checked | Not run | Not run | Not run |

No package in this table is declared fully compatible. Use [device acceptance](device-validation.md) to run the real scenarios; do not count a skipped fixture as passing.

## Implemented integration and limits

- Signed installation with consent, manual updates with additional-permission consent, enabled state, removal, browser actions/popups and options pages.
- Tab creation (including background creation and requested index), activation, supported URL updates, highlighting and closing through Gecko delegates. The normal limit remains twelve tabs and URL/HTTPS policy applies.
- Unsupported pinned/reader/discarded/container creation and pinned/muted/non-auto-discardable updates are rejected. Not all desktop browser APIs exist in GeckoView or this host.
- No user-extension access is enabled in private tabs. The built-in app-owned protection extension is enabled privately and isolates transient private exceptions.
- Native messaging is restricted to the built-in background's verified identity. Websites, content scripts and downloaded extensions do not receive a general native bridge.
- Disable all user extensions and recovery navigation keep the built-in protection separate and do not delete the saved-site library. Renderer failure requires an explicit retry rather than an automatic crash/reload loop.

Installing multiple blockers may duplicate work or break websites; test combined behavior separately. Recreating an activity does not prove profile recovery after process death. Both device tests and real extension updates remain release gates.
