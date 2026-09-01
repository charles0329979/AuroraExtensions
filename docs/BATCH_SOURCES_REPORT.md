# Audited source batch report

Validated on 2026-08-24 against the source list visible in the connected Pipi
device and public, auditable Keiyoushi-compatible implementations.

| Source | Language/type | Package | Validation |
|---|---|---|---|
| Webtoons.com | multilingual, official platform | `all.webtoons` | catalogue loaded on device |
| Rawkuma | Japanese raw-manga web source | `ja.rawkuma` | catalogue loaded on device |
| Sen Manga | Japanese web source | `ja.senmanga` | catalogue loaded on device |
| Dongman Manhua | Simplified Chinese, official platform | `zh.dongmanmanhua` | catalogue loaded on device |
| 快看漫画 | Simplified Chinese, official platform | `zh.kuaikanmanhua` | 20-item catalogue, live search, detail, 126 chapters, and a 14-page reader chapter rendered on device |
| 漫画1234 | Chinese web source | `zh.mh1234` | catalogue loaded on device |
| 喵趣漫画 | Chinese web source | `zh.miaoqu` | catalogue loaded on device |
| 腾讯动漫 | Simplified Chinese, official platform | `zh.tencentcomics` | catalogue loaded on device |
| 泰拉记事社 | Chinese, official Hypergryph platform | `zh.terrahistoricus` | catalogue loaded on device |
| 再漫画 | Chinese web source | `zh.zaimanhua` | catalogue loaded on device |
| 漫蛙(雫) | Chinese, mixed/adult content | `zh.manwashizuku` | live API/detail/chapter/image chain verified; APK built and signed |
| CCC追漫台 | Traditional Chinese official platform | `zh.creativecomic` | Pipi catalogue returned items; APK built and signed |
| 哔哩轻漫画 | Chinese web source | `zh.bilimanga` | Pipi catalogue returned items; APK built and signed |
| zero搬运网 | Chinese web source | `zh.zerobyw` | catalogue, detail, 5 chapters, and 25-page reader loaded on device |
| GoDa漫画 / G站漫画 / ManhuaScans | Chinese web source family with mirrors | `zh.baozimhorg` | catalogue, detail, 3875 chapters, and 75-page reader loaded on device; audited `manhuascans.org` mirror added to the selectable mirror set |
| 古风漫画 | Chinese web source | `zh.gufengmh` | catalogue, detail, 120 chapters, and 19 image URLs resolved; both current image hosts return Cloudflare HTTP 403 on the connected device, so it is marked degraded rather than fully verified |
| 读漫屋 | Chinese web source | `zh.dumanwu` | current domain rule independently updated; catalogue, detail, 127 chapters, and a 9-page reader chapter loaded on device |
| 滴答漫画 | Chinese web source | `zh.didamanhua` | catalogue, detail, 220 chapters, and a 22-page reader chapter loaded with rendered images on device; HTTP entry point follows the site's actual redirect behavior |
| 追漫画 / 异次元漫画 | Chinese mixed-content web source | `zh.ycymh` | catalogue, detail, 916 chapters, and an 89-page reader chapter loaded with rendered WebP images on device |
| 漫圈子 / 漫集市 | Chinese mixed-content web source | `zh.manquanzi` | catalogue, detail, 168 chapters, and 20 image URLs resolved; the legacy image host times out on the connected device, so it is marked degraded |
| 漫士多 | Chinese mixed-content web source | `zh.manshiduo` | catalogue, detail, 175 chapters, and a 169-page reader chapter loaded with rendered WebP images on device |
| 搜漫 | Chinese mixed-content search source | `zh.soman` | reader-safe route verified on device: 21 popular items, live search, 20 latest items, 73 chapters, and a 33-page chapter rendered; paid and unreadable external routes are deliberately excluded |

## 2026-08-31 quality-source pass

快看漫画 was added from pinned upstream commit
`4453d308244ddcf44abaf9f839558dc5ccc23b46`. Its rule requires QuickJS to
decode the site's Nuxt payload. Aurora's host now provides the legacy
`app.cash.quickjs` extension ABI through its existing isolated QuickJS runtime,
avoiding two incompatible native libraries with the same filename.

The connected-device test covered the full user path: popular catalogue,
`DOLO` search, manga details, 126 chapter entries, and rendered image page 1 of
14. This is an official, safe-content source, but paid chapters remain subject
to the platform's own access rules.

## Manwa decision

The older `zh.manwa` package displays `https://manwa.me`, but its mirror update
endpoint and configured domains no longer produce a usable catalogue. It was
therefore removed from the index instead of being counted as a working source.

The replacement is structurally a different site. It uses JSON catalogue and
chapter APIs, three mirror domains, selectable image hosts, and AES-CBC image
decryption. Validation covered:

- catalogue request: HTTP 200, 36 items returned;
- manga detail: HTTP 200, 129 chapter links detected on the sampled item;
- chapter image API: HTTP 200 with image entries;
- encrypted image download: HTTP 200 and successful decryption to WebP data.

Its source is pinned separately in `vendor/BATCH_PIN.txt` because the proposal
commit has not yet landed on the Keiyoushi default branch. Treat it as an
Aurora-maintained rule: rerun live smoke tests before publishing each update.

## What this proves—and what it does not

Build/sign/install validation proves package compatibility and repository
integrity. A website can still change its domain, HTML, API, anti-bot policy, or
availability after release. Stable operation therefore requires scheduled
smoke tests and fast rule updates; no static batch can guarantee permanent
availability.

The first candidate pass also tested and rejected currently failing sources:
Mangabat and Mangakakalot (dead DNS), Comicabc (HTTP 403), Komiic (connection
reset), ManHuaGui (device timeout), and the older Manwa package (dead mirror
chain). The second pass rejected MyComic: Pipi could list it, but the actual
Aurora extension stopped at Cloudflare and returned no catalogue. These packages
are deliberately absent from the generated index.
