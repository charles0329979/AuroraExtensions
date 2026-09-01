# Healthy source import — 2026-08-26

## Input

- Connected-device Pipi catalogue: 287 sources.
- Final entry-point grading: 81 usable, 115 partially usable, 90 dead, 1 login required.
- A Pipi URL was not treated as an Aurora plugin. Inclusion also required public,
  auditable source code, a reproducible APK build, matching embedded metadata,
  the repository signer, and an Aurora device smoke test.

## Added in this pass

| Pipi source | Aurora package | Version | Device result |
|---|---|---:|---|
| CCC追漫台 | `eu.kanade.tachiyomi.extension.zh.creativecomic` | 1.4.2 | catalogue, details, 42-chapter list, and a 10-page reader chapter loaded |
| 哔哩轻漫画 / 嗶哩漫畫 | `eu.kanade.tachiyomi.extension.zh.bilimanga` | 1.6.14 | catalogue, details, 703-chapter list, and a 27-page reader chapter loaded |
| zero搬运网 | `eu.kanade.tachiyomi.extension.zh.zerobyw` | 1.4.21 | catalogue, details, 5-chapter list, and a 25-page reader chapter loaded |
| G站漫画 / GoDa漫画 / GodaManga | `eu.kanade.tachiyomi.extension.zh.baozimhorg` | 1.6.38 | catalogue, details, 3875-chapter list, and a 75-page reader chapter loaded; `manhuascans.org` added as an audited family mirror |
| 古风漫画 | `eu.kanade.tachiyomi.extension.zh.gufengmh` | 1.6.2 | catalogue, details, 120-chapter list, and image URLs load; search-card compatibility was updated, but the site search backend is currently intermittent and both image proxies return Cloudflare HTTP 403, so the source remains explicitly degraded |
| 读漫屋网 | `eu.kanade.tachiyomi.extension.zh.dumanwu` | 1.6.3 | current successor domain; catalogue, details, 127-chapter list, and a 9-page reader chapter loaded |
| 滴答漫画 | `eu.kanade.tachiyomi.extension.zh.didamanhua` | 1.6.2 | catalogue returned 20 visible titles; details, 220-chapter list, and a 22-page reader chapter loaded with rendered images |
| 追漫画 / 异次元漫画 | `eu.kanade.tachiyomi.extension.zh.ycymh` | 1.6.2 | mixed-content catalogue, details, 916-chapter list, and an 89-page reader chapter loaded with rendered WebP images |
| 漫圈子 / 漫集市 | `eu.kanade.tachiyomi.extension.zh.manquanzi` | 1.6.3 | search, details, 168 chapters, and 20 image URLs resolve on the connected device; the search-result template and pagination were repaired, but the legacy image host remains unreachable, so this source is explicitly degraded |
| 漫士多 | `eu.kanade.tachiyomi.extension.zh.manshiduo` | 1.6.1 | catalogue returned 20 visible titles; sampled details had 175 chapters, and a 169-page reader chapter loaded with rendered WebP images |

The upstream APKs are built from the pinned Keiyoushi source revision recorded by the
batch vendor script. 古风漫画、读漫屋、滴答漫画、追漫画、漫圈子 and 漫士多 are independently
maintained Aurora rules based on their public website protocols. Their APKs'
SHA-256 hashes, sizes, source IDs, and signing
certificate are recorded in `repo/index.json` and verified by
`scripts/verify-repo-integrity.ps1`.

## Rejected in this pass

| Candidate | Reason |
|---|---|
| MyComic | Pipi returned catalogue items, but the real Aurora extension stopped at “无法绕过 Cloudflare” and returned no catalogue. Its test APK and index entry were removed. |
| Old Manwa (`manwa.me`) | Pipi's current rule can search it, but the available public extension uses a failed mirror-update chain. The separately verified Manwa (Shizuku) implementation remains in the repository. |

## Repository result

The repository now contains 23 APK packages: 20 curated website extensions,
MangaDex, and 2 Aurora framework/test extensions. Repository integrity is
fail-closed: an absent APK, changed byte, duplicate package, unsafe path, or
signer mismatch fails verification.

All 81 usable audit rows are now retained in `catalog/healthy-sources-20260826.json`
and its CSV companion. Seventeen rows currently map to a device-verified active
implementation (including three Manwa-family entry points), two map to explicitly
degraded implementations, and 62 remain explicit
implementation work instead of being exposed as fake URL-only plugins.

The remaining 62 usable Pipi URLs are not inserted as placeholders. They stay in
the rule-development backlog until a real implementation passes the same
catalogue → detail → chapter → image chain.
