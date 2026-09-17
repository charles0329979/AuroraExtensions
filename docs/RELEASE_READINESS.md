# User release readiness

Current checked state: 2026-09-17.

The local repository maintenance gate passes with 41 stable packages and no
testing publications. GitHub Pages and all five required production-signing
secret names are configured. Their secret values remain unreadable by design
and will only be proven when the production workflow restores the keystore and
verifies its certificate fingerprint.

The published stable catalogue does not yet match the local package/version/hash
set, and the testing URL returns HTTP 404. Remote daily audits from 2026-09-12
through 2026-09-15 also failed before the gate because the retired Android SDK
package `tools` was requested by `android-actions/setup-android@v3`. The local
workflows now use the current Node 24 action generations and request only
`platform-tools` during SDK setup. A branch audit confirmed that requesting
`platforms;android-37` at setup time fails on GitHub's current SDK mirror, so
the build step must resolve its own compile SDK. The feature-branch maintenance
audit now passes. A feature-branch build restored the production keystore and
built source modules, then stopped because the current development-signed
catalogue has a different signing identity. The workflow now checks this before
compilation. The repair is not active on the remote `main` branch until reviewed
and merged. Public release therefore remains blocked pending explicit approval
for the initial production-signing migration, a clean-device install test, and
an upgrade test on a device with the existing plugins installed.

Recheck the complete state at any time:

```powershell
.\scripts\check-release-readiness.ps1
```

Required order:

1. Create and verify the primary and offline copies described in
   `docs/PRODUCTION_SIGNING.md`.
2. Configure the five repository secrets without committing their values.
3. Enable GitHub Pages for the repository and use the `gh-pages` branch.
4. Run `Build extension repo` once with explicit key rotation enabled.
5. Confirm that the stable and testing catalogue URLs pass the readiness check.
6. Only then embed the stable `repo.json` URL in the user build and perform a
   clean-device install, update, trust, search, chapter, image, and offline-read
   acceptance pass.

Normal releases after the first production run must leave key rotation disabled.
The testing URL remains maintainer-only.
