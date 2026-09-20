# Extension health refresh — 2026-09-20

## Result

- Stable catalogue: 35 packages.
- Reading-source grades: 31 A, 2 B, 0 C/D/Q/U.
- Non-reading containers: 2.
- Unassessed stable packages: 0.
- Repository verification: passed, including index consistency, APK presence, and production signer verification.

## Revalidated

- Webtoons: popular listing, details, 68 chapters, 96-page reader chapter, and image rendering passed on the connected Android device.
- Sen Manga: popular listing, details, 274 chapters, 22-page reader chapter, and image rendering passed on the connected Android device.
- Kuaikan Manhua: production-signed v1.4.13 reopened an existing library entry; details, 254 chapters, a 31-page chapter, and first-image rendering passed in the isolated Android migration test.

## Retained with declared limitations

- Manhua360: complete reading chain works, but recent chapters can contain site promotion or placeholder images.
- Soman: complete reading chain works after filtering paid and unreadable external routes; the upstream HTTPS certificate is expired, so the source currently uses its plain-HTTP fallback.

## Quarantined from stable

The following signed APKs and icons were moved to `quarantine/stable/`. They were not deleted and can be restored after a complete successful recheck.

- Dida Manhua: the current device run reached browse, details, and 220 chapters, but the device disconnected before page-list and image verification.
- Dongman Manhua: the previous complete attestation is outside the publishable window and no current full-chain recheck is available.
- Rumanhua: the previous attestation expired and the current canonical site probe fails.
- Yumanhua: no current complete attestation is available and its HTTP endpoint is not suitable for the stable catalogue.
- Gufeng Manhua: image proxies return HTTP 403 and search remains intermittent.
- Manquanzi: the legacy image host is unreachable and HTTPS negotiation fails.

## Evidence boundary

`reports/device-inventory-20260920.json` records the connected-device inventory before quarantine. The device disconnected during the Dida Manhua reader-stage check, so that source was not promoted on partial evidence.

The latest bulk audit snapshot remains `healthy-sources-20260911.json`. At nine days old it is marked stale by the seven-day freshness target, but it is still inside the fourteen-day publication window. This refresh does not rewrite or falsely re-date that snapshot.
