# User release readiness

Current checked state: 2026-09-18.

The local repository maintenance gate passes with 41 stable packages and no
testing publications. GitHub Pages and all five required production-signing
secrets are configured. The feature-branch workflow restored the production
keystore, verified its certificate fingerprint, built all 41 packages, and
passed the repository integrity gate. Its signed artifacts are now staged on
the feature branch. The secret values remain unreadable by design.

The published stable catalogue does not yet match the local package/version/hash
set, and the testing URL returns HTTP 404. Remote daily audits from 2026-09-12
through 2026-09-15 also failed before the gate because the retired Android SDK
package `tools` was requested by `android-actions/setup-android@v3`. The local
workflows now use the current Node 24 action generations and request only
`platform-tools` during SDK setup. A branch audit confirmed that requesting
`platforms;android-37` at setup time fails on GitHub's current SDK mirror, so
the build step must resolve its own compile SDK. The feature-branch maintenance
audit now passes. The workflow checks signing identity before compilation. The
repair and production-signed catalogue are not active on remote `main` until
reviewed and merged. A clean-emulator install/read pass and an isolated
development-to-production-signing migration pass are recorded in
`docs/EXTENSION_SIGNING_MIGRATION_SMOKE_2026-09-18.md` and the app repository's
`docs/EXTENSION_CLEAN_INSTALL_SMOKE_2026-09-17.md`. Public release remains
blocked pending separate publication approval and the stable URL's acceptance
pass after publication. Neither emulator pass covers all 41 packages or offline
reading.

Recheck the complete state at any time:

```powershell
.\scripts\check-release-readiness.ps1
```

Required order:

1. Preserve and verify the production keystore and offline backup described in
   `docs/PRODUCTION_SIGNING.md`.
2. Review the production-signed feature branch and test its build artifacts on
   a clean device and an isolated migration device.
3. After separate publication approval, merge the branch to `main` and run the
   normal build with key rotation disabled.
4. Confirm that the stable and testing catalogue URLs pass the readiness check.
5. Only then embed the stable `repo.json` URL in the user build and perform the
   install, trust, search, chapter, image, and offline-read acceptance pass.

Normal releases after the first production run must leave key rotation disabled.
The testing URL remains maintainer-only.
