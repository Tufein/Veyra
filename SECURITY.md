# Security and maintenance

Veyra is an experimental browser, not a stable security-reviewed product. Keep passwords, cookies, private URLs, sync recovery phrases, and signing material out of public issues and CI artifacts.

Use GitHub private vulnerability reporting **if the repository owner has enabled it**. No separate confidential contact is configured in this repository. If private reporting is unavailable, open a minimal issue requesting a private contact without publishing exploit details or personal data. Availability of a private reporting channel remains an owner action.

The maintained development line and latest published alpha are 0.5.0. No security-response SLA or stable support duration is promised. Bug reports should include Veyra/Android/Gecko versions, device model and a public minimal reproduction where possible.

## Maintenance procedure

- Review GeckoView security/stable releases at least weekly and urgently after relevant Mozilla security advisories. Update the pinned engine with migration, native extension, permission and privacy regression checks.
- Dependabot proposes weekly Gradle and GitHub Actions updates. Dependency automation does not update the bundled blocklist or prove security compatibility.
- Review Material Components and build-tool updates weekly; retain Android 9, Java/toolchain requirements, license notices and the minimum supported API behavior.
- Refresh the bundled domain list deliberately, retaining source/license notices and running the local protection tests. The app does not auto-download replacement lists.
- Build all supported APK ABIs, validate the pinned signing certificate and publish checksums and accurate release limitations. Test an actual same-key update before claiming stable updates.
- Re-run the device matrix when engine, extension APIs, permission handling, storage or signing changes. Do not disable certificate, extension-signature or Android permission checks to pass a test.

Ownership, advisory monitoring and real-device execution need a human maintainer; the existence of this procedure is not evidence those activities have occurred.
