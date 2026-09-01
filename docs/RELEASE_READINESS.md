# User release readiness

Current checked state: 2026-09-01.

The local repository maintenance gate passes with 40 stable packages. GitHub
Pages is enabled with enforced HTTPS, but its current `gh-pages` snapshot only
contains the old one-package development catalogue. The testing URL is not yet
present, and the repository has none of the five required production signing
secrets. Public release remains intentionally blocked until the permanent
signing identity exists and a production workflow publishes the current local
catalogue. The reader must not embed the stable URL until all checks pass.

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
