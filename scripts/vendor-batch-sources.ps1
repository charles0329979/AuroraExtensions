<#
.SYNOPSIS
  Build/copy the first audited batch of public manga source extensions and append them to repo/.
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = "",
    [string]$SourceDir = "",
    [switch]$SkipBuild,
    [string[]]$BuildPackages = @()
)

$ErrorActionPreference = "Stop"
if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path }

$PinSha = "42771052f3e43b09a04d4b3f9073039690607476"
$PinRepo = "https://github.com/keiyoushi/extensions-source.git"
$ManwaShizukuSha = "3b1dd61cd1b0ea358d9e1b7e5cd893b6cf7113dd"
$QualitySourcesSha = "4453d308244ddcf44abaf9f839558dc5ccc23b46"
$VendorDir = if ($SourceDir) { $SourceDir } else { Join-Path $RepoRoot "vendor\batch-extensions-source" }
$RepoDir = Join-Path $RepoRoot "repo"
$ApkDir = Join-Path $RepoDir "apk"
$IconDir = Join-Path $RepoDir "icon"
$TestingRepoDir = Join-Path $RepoRoot "repo-testing"

$specs = @(
    [pscustomobject]@{
        Module = "src/all/webtoons"; Package = "eu.kanade.tachiyomi.extension.all.webtoons"
        Name = "Tachiyomi: Webtoons.com"; Apk = "tachiyomi-all.webtoons-v1.4.57.apk"
        Lang = "all"; Code = 57; Version = "1.4.57"; Nsfw = 0
        Sources = @(
            @{ id = 2522335540328470744L; lang = "en"; name = "Webtoons.com"; baseUrl = "https://www.webtoons.com" }
            @{ id = 8749627068478740298L; lang = "id"; name = "Webtoons.com"; baseUrl = "https://www.webtoons.com" }
            @{ id = 3330861233813615504L; lang = "th"; name = "Webtoons.com"; baseUrl = "https://www.webtoons.com" }
            @{ id = 8664735734660636295L; lang = "es"; name = "Webtoons.com"; baseUrl = "https://www.webtoons.com" }
            @{ id = 8251758253314457060L; lang = "fr"; name = "Webtoons.com"; baseUrl = "https://www.webtoons.com" }
            @{ id = 2959982438613576472L; lang = "zh-Hant"; name = "Webtoons.com"; baseUrl = "https://www.webtoons.com" }
            @{ id = 1486822823904297379L; lang = "de"; name = "Webtoons.com"; baseUrl = "https://www.webtoons.com" }
        )
    }
    [pscustomobject]@{
        Module = "src/ja/rawkuma"; Package = "eu.kanade.tachiyomi.extension.ja.rawkuma"
        Name = "Tachiyomi: Rawkuma"; Apk = "tachiyomi-ja.rawkuma-v1.4.39.apk"
        Lang = "ja"; Code = 39; Version = "1.4.39"; Nsfw = 0
        Sources = @(@{ id = 5746834068092446709L; lang = "ja"; name = "Rawkuma"; baseUrl = "https://rawkuma.net" })
    }
    [pscustomobject]@{
        Module = "src/zh/tencentcomics"; Package = "eu.kanade.tachiyomi.extension.zh.tencentcomics"
        Name = "Tachiyomi: Tencent Comics (ac.qq.com)"; Apk = "tachiyomi-zh.tencentcomics-v1.4.10.apk"
        Lang = "zh"; Code = 10; Version = "1.4.10"; Nsfw = 0
        Sources = @(@{ id = 6353436350537369479L; lang = "zh-Hans"; name = "腾讯动漫"; baseUrl = "https://m.ac.qq.com" })
    }
    [pscustomobject]@{
        Module = "src/ja/senmanga"; Package = "eu.kanade.tachiyomi.extension.ja.senmanga"
        Name = "Tachiyomi: Sen Manga"; Apk = "tachiyomi-ja.senmanga-v1.4.8.apk"
        Lang = "ja"; Code = 8; Version = "1.4.8"; Nsfw = 0
        Sources = @(@{ id = 7715542271185249444L; lang = "ja"; name = "Sen Manga"; baseUrl = "https://raw.senmanga.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/terrahistoricus"; Package = "eu.kanade.tachiyomi.extension.zh.terrahistoricus"
        Name = "Tachiyomi: Terra Historicus"; Apk = "tachiyomi-zh.terrahistoricus-v1.4.4.apk"
        Lang = "zh"; Code = 4; Version = "1.4.4"; Nsfw = 0
        Sources = @(@{ id = 4585134706567717130L; lang = "zh"; name = "泰拉记事社"; baseUrl = "https://comic.hypergryph.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/dongmanmanhua"; Package = "eu.kanade.tachiyomi.extension.zh.dongmanmanhua"
        Name = "Tachiyomi: Dongman Manhua"; Apk = "tachiyomi-zh.dongmanmanhua-v1.4.6.apk"
        Lang = "zh"; Code = 6; Version = "1.4.6"; Nsfw = 0
        Sources = @(@{ id = 4222375517460530289L; lang = "zh-Hans"; name = "Dongman Manhua"; baseUrl = "https://www.dongmanmanhua.cn" })
    }
    [pscustomobject]@{
        Module = "src/zh/kuaikanmanhua"; Package = "eu.kanade.tachiyomi.extension.zh.kuaikanmanhua"
        Name = "Tachiyomi: Kuaikanmanhua"; Apk = "tachiyomi-zh.kuaikanmanhua-v1.4.13.apk"
        Lang = "zh"; Code = 13; Version = "1.4.13"; Nsfw = 0
        Sources = @(@{ id = 8099870292642776005L; lang = "zh-Hans"; name = "快看漫画"; baseUrl = "https://www.kuaikanmanhua.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/zaimanhua"; Package = "eu.kanade.tachiyomi.extension.zh.zaimanhua"
        Name = "Tachiyomi: Zaimanhua"; Apk = "tachiyomi-zh.zaimanhua-v1.4.19.apk"
        Lang = "zh"; Code = 19; Version = "1.4.19"; Nsfw = 0
        Sources = @(@{ id = 524579092615598717L; lang = "zh"; name = "再漫画"; baseUrl = "https://manhua.zaimanhua.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/mh1234"; Package = "eu.kanade.tachiyomi.extension.zh.mh1234"
        Name = "Tachiyomi: MH1234"; Apk = "tachiyomi-zh.mh1234-v1.6.5.apk"
        Lang = "zh"; Code = 5; Version = "1.6.5"; Nsfw = 0
        Sources = @(@{ id = 7895725080195720063L; lang = "zh"; name = "漫画1234"; baseUrl = "https://m.wmh1234.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/manwashizuku"; Package = "eu.kanade.tachiyomi.extension.zh.manwashizuku"
        Name = "Tachiyomi: Manwa (Shizuku)"; Apk = "tachiyomi-zh.manwashizuku-v1.6.5.apk"
        Lang = "zh"; Code = 5; Version = "1.6.5"; Nsfw = 0
        Sources = @(@{ id = 7453499921408758404L; lang = "zh"; name = "漫蛙(雫)"; baseUrl = "https://manwaxu.cc" })
    }
    [pscustomobject]@{
        Module = "src/zh/miaoqu"; Package = "eu.kanade.tachiyomi.extension.zh.miaoqu"
        Name = "Tachiyomi: Miaoqu Manhua"; Apk = "tachiyomi-zh.miaoqu-v1.4.8.apk"
        Lang = "zh"; Code = 8; Version = "1.4.8"; Nsfw = 0
        Sources = @(@{ id = 116946528518438525L; lang = "zh"; name = "喵趣漫画"; baseUrl = "https://www.miaoqumh.org" })
    }
    [pscustomobject]@{
        Module = "src/zh/baozimanhua"; Package = "eu.kanade.tachiyomi.extension.zh.baozimanhua"
        Name = "Tachiyomi: Baozi Manhua"; Apk = "tachiyomi-zh.baozimanhua-v1.6.30.apk"
        Lang = "zh"; Code = 30; Version = "1.6.30"; Nsfw = 0
        Sources = @(@{ id = 5724751873601868259L; lang = "zh"; name = "包子漫画"; baseUrl = "https://cn.webmota.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/mycomic"; Package = "eu.kanade.tachiyomi.extension.zh.mycomic"
        Name = "Tachiyomi: MyComic"; Apk = "tachiyomi-zh.mycomic-v1.4.4.apk"
        Lang = "zh"; Code = 4; Version = "1.4.4"; Nsfw = 0
        Sources = @(@{ id = 9119537447562549661L; lang = "zh"; name = "MyComic"; baseUrl = "https://mycomic.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/boylove"; Package = "eu.kanade.tachiyomi.extension.zh.boylove"
        Name = "Tachiyomi: BoyLove"; Apk = "tachiyomi-zh.boylove-v1.4.19.apk"
        Lang = "zh"; Code = 19; Version = "1.4.19"; Nsfw = 0
        Sources = @(@{ id = 1471112097704477289L; lang = "zh"; name = "香香腐宅"; baseUrl = "https://boyloveheaven13.cc" })
    }
    [pscustomobject]@{
        Module = "src/zh/ttkmh"; Package = "eu.kanade.tachiyomi.extension.zh.ttkmh"
        Name = "Tachiyomi: TTKMH"; Apk = "tachiyomi-zh.ttkmh-v1.4.2.apk"
        Lang = "zh"; Code = 2; Version = "1.4.2"; Nsfw = 0
        Icon = "lib-multisrc/mccms/res/mipmap-hdpi/ic_launcher.png"
        Sources = @(@{ id = 1608992848159007798L; lang = "zh"; name = "天天看漫画"; baseUrl = "https://www.ttkmh.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/kaixinman"; Package = "eu.kanade.tachiyomi.extension.zh.kaixinman"
        Name = "Tachiyomi: Kaixinman"; Apk = "tachiyomi-zh.kaixinman-v1.4.1.apk"
        Lang = "zh"; Code = 1; Version = "1.4.1"; Nsfw = 0
        Icon = "lib-multisrc/mccms/res/mipmap-hdpi/ic_launcher.png"
        Sources = @(@{ id = 8181148133350199169L; lang = "zh"; name = "开心漫画"; baseUrl = "https://www.kaixinman.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/sisimanhua"; Package = "eu.kanade.tachiyomi.extension.zh.sisimanhua"
        Name = "Tachiyomi: Sisi Manhua"; Apk = "tachiyomi-zh.sisimanhua-v1.4.1.apk"
        Lang = "zh"; Code = 1; Version = "1.4.1"; Nsfw = 0
        Icon = "lib-multisrc/mccms/res/mipmap-hdpi/ic_launcher.png"
        Sources = @(@{ id = 1760898295611365693L; lang = "zh"; name = "思思漫画"; baseUrl = "https://m.sisimanhua.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/yumanhua"; Package = "eu.kanade.tachiyomi.extension.zh.yumanhua"
        Name = "Tachiyomi: Yumanhua"; Apk = "tachiyomi-zh.yumanhua-v1.4.4.apk"
        Lang = "zh"; Code = 4; Version = "1.4.4"; Nsfw = 0
        Icon = "src/zh/rumanhua/res/mipmap-hdpi/ic_launcher.png"
        Sources = @(@{ id = 4853252437674834413L; lang = "zh"; name = "漫画客"; baseUrl = "http://yumanhua.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/rumanhua"; Package = "eu.kanade.tachiyomi.extension.zh.rumanhua"
        Name = "Tachiyomi: Rumanhua"; Apk = "tachiyomi-zh.rumanhua-v1.4.5.apk"
        Lang = "zh"; Code = 5; Version = "1.4.5"; Nsfw = 0
        Sources = @(@{ id = 392262758488714109L; lang = "zh"; name = "如漫画"; baseUrl = "https://m.rumanhua2.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/manhuadaquan"; Package = "eu.kanade.tachiyomi.extension.zh.manhuadaquan"
        Name = "Tachiyomi: Manhua Daquan"; Apk = "tachiyomi-zh.manhuadaquan-v1.4.1.apk"
        Lang = "zh"; Code = 1; Version = "1.4.1"; Nsfw = 0
        Icon = "src/zh/rumanhua/res/mipmap-hdpi/ic_launcher.png"
        Sources = @(@{ id = 6318419290037862216L; lang = "zh"; name = "漫画大全"; baseUrl = "http://www.yueman1.cc" })
    }
    [pscustomobject]@{
        Module = "src/zh/creativecomic"; Package = "eu.kanade.tachiyomi.extension.zh.creativecomic"
        Name = "Tachiyomi: Creative Comic Collection"; Apk = "tachiyomi-zh.creativecomic-v1.4.2.apk"
        Lang = "zh"; Code = 2; Version = "1.4.2"; Nsfw = 0
        Sources = @(@{ id = 8346821279138091413L; lang = "zh-Hant"; name = "CCC追漫台"; baseUrl = "https://www.creative-comic.tw" })
    }
    [pscustomobject]@{
        Module = "src/zh/bilimanga"; Package = "eu.kanade.tachiyomi.extension.zh.bilimanga"
        Name = "Tachiyomi: BiliManga"; Apk = "tachiyomi-zh.bilimanga-v1.6.14.apk"
        Lang = "zh"; Code = 14; Version = "1.6.14"; Nsfw = 0
        Sources = @(@{ id = 7289707411592168382L; lang = "zh"; name = "嗶哩漫畫"; baseUrl = "https://www.bilimanga.net" })
    }
    [pscustomobject]@{
        Module = "src/zh/zerobyw"; Package = "eu.kanade.tachiyomi.extension.zh.zerobyw"
        Name = "Tachiyomi: Zerobyw"; Apk = "tachiyomi-zh.zerobyw-v1.4.21.apk"
        Lang = "zh"; Code = 21; Version = "1.4.21"; Nsfw = 0
        Sources = @(@{ id = 8743284448117690086L; lang = "zh"; name = "zero搬运网"; baseUrl = "http://www.zerobyw33.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/baozimhorg"; Package = "eu.kanade.tachiyomi.extension.zh.baozimhorg"
        Name = "Tachiyomi: GoDa"; Apk = "tachiyomi-zh.baozimhorg-v1.6.38.apk"
        Lang = "zh"; Code = 38; Version = "1.6.38"; Nsfw = 0
        Icon = "lib-multisrc/goda/res/mipmap-hdpi/ic_launcher.png"
        Sources = @(@{ id = 774030471139699415L; lang = "zh"; name = "GoDa漫画"; baseUrl = "https://baozimh.org" })
    }
    [pscustomobject]@{
        Module = "src/zh/gufengmh"; Package = "eu.kanade.tachiyomi.extension.zh.gufengmh"
        Name = "Tachiyomi: Gufeng Manhua"; Apk = "tachiyomi-zh.gufengmh-v1.6.5.apk"
        Lang = "zh"; Code = 5; Version = "1.6.5"; Nsfw = 0
        Sources = @(@{ id = 8452091260243947804L; lang = "zh"; name = "古风漫画"; baseUrl = "https://www.gfmh.app" })
    }
    [pscustomobject]@{
        Module = "src/zh/dumanwu"; Package = "eu.kanade.tachiyomi.extension.zh.dumanwu"
        Name = "Tachiyomi: Dumanwu"; Apk = "tachiyomi-zh.dumanwu-v1.6.4.apk"
        Lang = "zh"; Code = 4; Version = "1.6.4"; Nsfw = 0
        Sources = @(@{ id = 7167507050606280098L; lang = "zh"; name = "读漫屋"; baseUrl = "https://m.dumanwu.org" })
    }
    [pscustomobject]@{
        Module = "src/zh/didamanhua"; Package = "eu.kanade.tachiyomi.extension.zh.didamanhua"
        Name = "Tachiyomi: Dida Manhua"; Apk = "tachiyomi-zh.didamanhua-v1.6.2.apk"
        Lang = "zh"; Code = 2; Version = "1.6.2"; Nsfw = 0
        Sources = @(@{ id = 3861039501235360034L; lang = "zh"; name = "滴答漫画"; baseUrl = "http://ddmanhua.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/ycymh"; Package = "eu.kanade.tachiyomi.extension.zh.ycymh"
        Name = "Tachiyomi: YiciYuan Manhua"; Apk = "tachiyomi-zh.ycymh-v1.6.2.apk"
        Lang = "zh"; Code = 2; Version = "1.6.2"; Nsfw = 0
        Sources = @(@{ id = 8305264819870690508L; lang = "zh"; name = "追漫画"; baseUrl = "https://www.ycymh.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/manquanzi"; Package = "eu.kanade.tachiyomi.extension.zh.manquanzi"
        Name = "Tachiyomi: Manquanzi"; Apk = "tachiyomi-zh.manquanzi-v1.6.3.apk"
        Lang = "zh"; Code = 3; Version = "1.6.3"; Nsfw = 0
        Sources = @(@{ id = 746250823392626617L; lang = "zh"; name = "漫圈子"; baseUrl = "https://www.9mqz.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/manshiduo"; Package = "eu.kanade.tachiyomi.extension.zh.manshiduo"
        Name = "Tachiyomi: Manshiduo"; Apk = "tachiyomi-zh.manshiduo-v1.6.1.apk"
        Lang = "zh"; Code = 1; Version = "1.6.1"; Nsfw = 0
        Sources = @(@{ id = 8741951640601575191L; lang = "zh"; name = "漫士多"; baseUrl = "https://manshiduo.org" })
    }
    [pscustomobject]@{
        Module = "src/zh/mh250"; Package = "eu.kanade.tachiyomi.extension.zh.mh250"
        Name = "Tachiyomi: 250 Manhua"; Apk = "tachiyomi-zh.mh250-v1.6.2.apk"
        Lang = "zh"; Code = 2; Version = "1.6.2"; Nsfw = 0
        Sources = @(@{ id = 5175541392364239409L; lang = "zh"; name = "250漫画"; baseUrl = "http://www.mh250.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/bikabika"; Package = "eu.kanade.tachiyomi.extension.zh.bikabika"
        Name = "Tachiyomi: BikaBika Manhua"; Apk = "tachiyomi-zh.bikabika-v1.6.3.apk"
        Lang = "zh"; Code = 3; Version = "1.6.3"; Nsfw = 0
        Sources = @(@{ id = 4569191035934535986L; lang = "zh"; name = "BikaBika漫画"; baseUrl = "https://m.bikamanhua.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/dmanhua"; Package = "eu.kanade.tachiyomi.extension.zh.dmanhua"
        Name = "Tachiyomi: DManhua"; Apk = "tachiyomi-zh.dmanhua-v1.6.1.apk"
        Lang = "zh"; Code = 1; Version = "1.6.1"; Nsfw = 0
        Sources = @(@{ id = 228875196645121102L; lang = "zh"; name = "可漫画"; baseUrl = "https://www.dmanhua.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/kanman"; Package = "eu.kanade.tachiyomi.extension.zh.kanman"
        Name = "Tachiyomi: Kanman"; Apk = "tachiyomi-zh.kanman-v1.6.4.apk"
        Lang = "zh"; Code = 4; Version = "1.6.4"; Nsfw = 0
        Sources = @(@{ id = 6760527258454776894L; lang = "zh"; name = "看漫画（免费章节）"; baseUrl = "https://m.kanman.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/manhua360"; Package = "eu.kanade.tachiyomi.extension.zh.manhua360"
        Name = "Tachiyomi: 360 Manhua"; Apk = "tachiyomi-zh.manhua360-v1.6.5.apk"
        Lang = "zh"; Code = 5; Version = "1.6.5"; Nsfw = 0
        Sources = @(@{ id = 7222744526115460827L; lang = "zh"; name = "360漫画"; baseUrl = "https://www.360mh.cc" })
    }
    [pscustomobject]@{
        Module = "src/zh/manhua36"; Package = "eu.kanade.tachiyomi.extension.zh.manhua36"
        Name = "Tachiyomi: 36 Manhua"; Apk = "tachiyomi-zh.manhua36-v1.6.2.apk"
        Lang = "zh"; Code = 2; Version = "1.6.2"; Nsfw = 0
        Sources = @(@{ id = 1316982367954300788L; lang = "zh"; name = "36漫画"; baseUrl = "https://m.36mh.org" })
    }
    [pscustomobject]@{
        Module = "src/zh/manhua456"; Package = "eu.kanade.tachiyomi.extension.zh.manhua456"
        Name = "Tachiyomi: Manhua456"; Apk = "tachiyomi-zh.manhua456-v1.6.2.apk"
        Lang = "zh"; Code = 2; Version = "1.6.2"; Nsfw = 0
        Sources = @(@{ id = 8681693108341600879L; lang = "zh"; name = "漫画456"; baseUrl = "https://m.manhua456.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/soman"; Package = "eu.kanade.tachiyomi.extension.zh.soman"
        Name = "Tachiyomi: Soman"; Apk = "tachiyomi-zh.soman-v1.6.3.apk"
        Lang = "zh"; Code = 3; Version = "1.6.3"; Nsfw = 0
        Sources = @(@{ id = 4379597560628642163L; lang = "zh"; name = "搜漫"; baseUrl = "http://www.veryim.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/tuku"; Package = "eu.kanade.tachiyomi.extension.zh.tuku"
        Name = "Tachiyomi: Tuku"; Apk = "tachiyomi-zh.tuku-v1.6.2.apk"
        Lang = "zh"; Code = 2; Version = "1.6.2"; Nsfw = 0
        Sources = @(@{ id = 8561993354455553651L; lang = "zh"; name = "图库漫画"; baseUrl = "https://www.tuku.cc" })
    }
    [pscustomobject]@{
        Module = "src/zh/bukamh"; Package = "eu.kanade.tachiyomi.extension.zh.bukamh"
        Name = "Tachiyomi: BukaMH"; Apk = "tachiyomi-zh.bukamh-v1.6.4.apk"
        Lang = "zh"; Code = 4; Version = "1.6.4"; Nsfw = 0
        Sources = @(@{ id = 3269491245867055703L; lang = "zh"; name = "布卡漫画"; baseUrl = "https://www.bukamh.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/manhuaba"; Package = "eu.kanade.tachiyomi.extension.zh.manhuaba"
        Name = "Tachiyomi: ManhuaBa"; Apk = "tachiyomi-zh.manhuaba-v1.6.5.apk"
        Lang = "zh"; Code = 5; Version = "1.6.5"; Nsfw = 0
        Sources = @(@{ id = 2463343216724262794L; lang = "zh"; name = "漫画吧"; baseUrl = "https://www.manhuaba.com" })
    }
    [pscustomobject]@{
        Module = "src/zh/talkmanhua"; Package = "eu.kanade.tachiyomi.extension.zh.talkmanhua"
        Name = "Tachiyomi: TalkManhua"; Apk = "tachiyomi-zh.talkmanhua-v1.6.1.apk"
        Lang = "zh"; Code = 1; Version = "1.6.1"; Nsfw = 0
        Sources = @(@{ id = 5728547098385256324L; lang = "zh"; name = "沐沐漫画"; baseUrl = "https://www.talkmanhua.com" })
    }
)

$catalog = Get-Content -LiteralPath (Join-Path $RepoRoot "catalog\sources.yaml") -Raw | ConvertFrom-Json
$catalogBatch = @($catalog.packages | Where-Object {
    $slug = ([string]$_.package).Split('.')[-1]
    $slug -notin @('aurorastub', 'aurorascripted', 'mangadex') -and
        [string]$_.state -ne 'retired'
})
$specPackages = @($specs.Package | Sort-Object)
$plannedPackages = @($catalogBatch.package | Sort-Object)
if (($specPackages -join "`n") -ne ($plannedPackages -join "`n")) {
    throw "vendor batch package list drifted from catalog/sources.yaml"
}
foreach ($spec in $specs) {
    $planned = $catalogBatch | Where-Object package -eq $spec.Package | Select-Object -First 1
    $expectedModule = "src/$($planned.language)/$(([string]$planned.package).Split('.')[-1])"
    if (
        [string]$spec.Module -ne $expectedModule -or
        [string]$spec.Name -ne [string]$planned.displayName -or
        [string]$spec.Apk -ne [string]$planned.apk -or
        [string]$spec.Lang -ne [string]$planned.language -or
        [long]$spec.Code -ne [long]$planned.versionCode -or
        [string]$spec.Version -ne [string]$planned.versionName -or
        [int]$spec.Nsfw -ne [int]$planned.nsfw
    ) {
        throw "vendor build metadata drifted from catalog/sources.yaml: $($spec.Package)"
    }
}
$publishedBatchPackages = @($catalogBatch | Where-Object {
    [string]$_.state -eq 'published'
} | ForEach-Object { [string]$_.package })
$activeSpecs = @($specs | Where-Object Package -in $publishedBatchPackages)
$unknownBuildPackages = @($BuildPackages | Where-Object { $_ -notin $activeSpecs.Package })
if ($unknownBuildPackages.Count -gt 0) {
    throw "Unknown or unpublished build package(s): $($unknownBuildPackages -join ', ')"
}
$buildSpecs = if ($BuildPackages.Count -gt 0) {
    @($activeSpecs | Where-Object Package -in $BuildPackages)
} else {
    $activeSpecs
}

function Ensure-Checkout {
    if (-not (Test-Path (Join-Path $VendorDir ".git"))) {
        if ($SourceDir) { throw "SourceDir is not a Git checkout: $SourceDir" }
        New-Item -ItemType Directory -Force -Path (Split-Path $VendorDir) | Out-Null
        git clone --filter=blob:none --sparse $PinRepo $VendorDir
        if ($LASTEXITCODE -ne 0) { throw "Batch source clone failed" }
    }
    Push-Location $VendorDir
    try {
        $paths = @("common", "compiler", "core", "gradle", "lib", "lib-multisrc") + $specs.Module
        git sparse-checkout set --cone @paths
        if ($LASTEXITCODE -ne 0) { throw "Batch sparse checkout failed" }
        git fetch origin $PinSha
        git checkout $PinSha
        if ($LASTEXITCODE -ne 0) { throw "Unable to checkout batch pin $PinSha" }
    } finally { Pop-Location }
}

function Ensure-ManwaShizukuSource {
    $modulePath = Join-Path $VendorDir "src\zh\manwashizuku"
    if (Test-Path (Join-Path $modulePath "build.gradle.kts")) { return }

    Push-Location $VendorDir
    try {
        # The independently reviewed implementation is pinned to its proposal
        # commit because it has not yet landed on the upstream default branch.
        git fetch --no-tags origin $ManwaShizukuSha --depth=1
        if ($LASTEXITCODE -ne 0) { throw "Unable to fetch Manwa Shizuku pin $ManwaShizukuSha" }
        git archive --format=tar $ManwaShizukuSha src/zh/manwashizuku | tar -xf -
        if ($LASTEXITCODE -ne 0) { throw "Unable to extract Manwa Shizuku source" }
    } finally { Pop-Location }
}

function Ensure-QualitySources {
    Push-Location $VendorDir
    try {
        git fetch --no-tags origin $QualitySourcesSha --depth=1
        if ($LASTEXITCODE -ne 0) { throw "Unable to fetch quality source pin $QualitySourcesSha" }
        git archive --format=tar $QualitySourcesSha src/zh/kuaikanmanhua | tar -xf -
        if ($LASTEXITCODE -ne 0) { throw "Unable to extract pinned quality sources" }
    } finally { Pop-Location }
}

function Ensure-AuroraCustomSources {
    foreach ($module in @(
        "ttkmh", "kaixinman", "sisimanhua", "yumanhua", "manhuadaquan", "mh1234",
        "gufengmh", "dumanwu", "didamanhua", "ycymh", "manquanzi", "manshiduo",
        "mh250", "bikabika", "dmanhua", "kanman", "manhua360", "manhua36", "manhua456", "soman",
        "tuku", "bukamh", "manhuaba", "talkmanhua"
    )) {
        $source = Join-Path $RepoRoot "extensions\$module"
        $target = Join-Path $VendorDir "src\zh\$module"
        if (-not (Test-Path -LiteralPath (Join-Path $source "build.gradle.kts"))) {
            throw "Missing Aurora custom source: $source"
        }
        New-Item -ItemType Directory -Force -Path $target | Out-Null
        Copy-Item -LiteralPath (Join-Path $source "build.gradle.kts") -Destination $target -Force
        $targetSource = Join-Path $target "src"
        New-Item -ItemType Directory -Force -Path $targetSource | Out-Null
        Copy-Item -Path (Join-Path $source "src\*") -Destination $targetSource -Recurse -Force

        $iconDir = Join-Path $target "res\mipmap-hdpi"
        $genericIcon = Join-Path $RepoRoot "repo\icon\eu.kanade.tachiyomi.extension.all.aurorastub.png"
        New-Item -ItemType Directory -Force -Path $iconDir | Out-Null
        Copy-Item -LiteralPath $genericIcon -Destination (Join-Path $iconDir "ic_launcher.png") -Force
    }
}

function Ensure-GoDaMirrorOverride {
    $buildFile = Join-Path $VendorDir "src\zh\baozimhorg\build.gradle.kts"
    $text = Get-Content -LiteralPath $buildFile -Raw
    $updated = $text.Replace("versionCode = 34", "versionCode = 35")
    if ($updated -notmatch [regex]::Escape("https://manhuascans.org")) {
        $updated = $updated.Replace(
            '                "https://baozimh.org",',
            "                `"https://baozimh.org`",`n                `"https://manhuascans.org`","
        )
    }
    if ($updated -notmatch [regex]::Escape("https://manhuascans.org")) {
        throw "Unable to add the audited ManhuaScans GoDa mirror"
    }
    [IO.File]::WriteAllText($buildFile, $updated, [Text.UTF8Encoding]::new($false))
}

function Ensure-ManwaMirrorOverride {
    $buildFile = Join-Path $VendorDir "src\zh\manwashizuku\build.gradle.kts"
    $text = Get-Content -LiteralPath $buildFile -Raw
    $versionPattern = '(?m)^    versionCode = [1-5]$'
    $mirrorsPattern = '(?s)            mirrors\(\r?\n.*?            \)'
    if ([regex]::Matches($text, $versionPattern).Count -ne 1 -or
        [regex]::Matches($text, $mirrorsPattern).Count -ne 1) {
        throw "Pinned Manwa Shizuku metadata changed; review the mirror override"
    }
    $canonicalMirrors = @'
            mirrors(
                "https://manwaxu.cc",
            )
'@
    $updated = [regex]::Replace($text, $versionPattern, '    versionCode = 5')
    $updated = [regex]::Replace($updated, $mirrorsPattern, $canonicalMirrors)
    [IO.File]::WriteAllText($buildFile, $updated, [Text.UTF8Encoding]::new($false))

    $sourceFile = Join-Path $VendorDir "src\zh\manwashizuku\src\eu\kanade\tachiyomi\extension\zh\manwashizuku\ManwaShizuku.kt"
    $sourceText = Get-Content -LiteralPath $sourceFile -Raw
    $oldHostCheck = @'
        val imageHost = preferences.getString(IMAGE_HOST_KEY, IMAGE_HOST_ENTRIES[0])!!.substringBefore(":")
        if (response.request.url.host != imageHost || !response.request.url.toString().endsWith(".jpg")) {
'@
    $newHostCheck = @'
        val imageHosts = IMAGE_HOST_ENTRIES.map { it.substringBefore(":") }
        if (response.request.url.host !in imageHosts || !response.request.url.toString().endsWith(".jpg")) {
'@
    $sourceUpdated = $sourceText.Replace($oldHostCheck, $newHostCheck)
    if ($sourceUpdated -notmatch 'response\.request\.url\.host !in imageHosts') {
        throw "Unable to enable cover decryption across all known image hosts"
    }
    [IO.File]::WriteAllText($sourceFile, $sourceUpdated, [Text.UTF8Encoding]::new($false))
}

function Ensure-BaoziMirrorOverride {
    $buildFile = Join-Path $VendorDir "src\zh\baozimanhua\build.gradle.kts"
    $text = Get-Content -LiteralPath $buildFile -Raw
    $oldOrder = @'
                "https://cn.baozimh.com",
                "https://tw.baozimh.com",
                "https://www.baozimh.com",
                "https://cn.webmota.com",
'@
    $newOrder = @'
                "https://cn.webmota.com",
                "https://cn.baozimh.com",
                "https://tw.baozimh.com",
                "https://www.baozimh.com",
'@
    $updated = $text.Replace($oldOrder, $newOrder).Replace("versionCode = 29", "versionCode = 30")
    if ($updated -notmatch 'mirrors\(\s*"https://cn\.webmota\.com"') {
        throw "Unable to promote the audited Baozi cn.webmota.com mirror"
    }
    if ($updated -notmatch 'versionCode = 30') {
        throw "Unable to apply the Baozi chapter redirect fix version"
    }
    [IO.File]::WriteAllText($buildFile, $updated, [Text.UTF8Encoding]::new($false))

    $sourceFile = Join-Path $VendorDir "src\zh\baozimanhua\src\eu\kanade\tachiyomi\extension\zh\baozimanhua\Baozi.kt"
    $sourceText = Get-Content -LiteralPath $sourceFile -Raw
    $oldCondition = 'if (isAppMirror && isQuickPage) {'
    $newCondition = 'if ((isAppMirror && isQuickPage) || chapterUrl.toHttpUrl().encodedPath == "/user/page_direct") {'
    $sourceUpdated = $sourceText.Replace($oldCondition, $newCondition)
    if ($sourceUpdated -notmatch 'encodedPath == "/user/page_direct"') {
        throw "Unable to bypass the unstable Baozi page_direct redirect"
    }
    [IO.File]::WriteAllText($sourceFile, $sourceUpdated, [Text.UTF8Encoding]::new($false))
}

function Ensure-BoyLoveMirrorOverride {
    $buildFile = Join-Path $VendorDir "src\zh\boylove\build.gradle.kts"
    $text = Get-Content -LiteralPath $buildFile -Raw
    $mirrorPattern = '(?s)            mirrors\(\r?\n.*?            \)'
    $canonicalMirrors = @'
            mirrors(
                "https://boyloveheaven13.cc",
                "https://boyloveheaven14.cc",
                "https://boylove.cc",
                "https://boylove4.xyz",
            )
'@
    $updated = [regex]::Replace($text, $mirrorPattern, $canonicalMirrors, 1)
    if ($updated -notmatch 'mirrors\(\s*"https://boyloveheaven13\.cc"') {
        throw "Unable to normalize the BoyLove mirror list"
    }
    [IO.File]::WriteAllText($buildFile, $updated, [Text.UTF8Encoding]::new($false))
}

function Ensure-NeutralContentWarnings {
    $manifestTaskFile = Join-Path $VendorDir "gradle\build-logic\src\main\kotlin\io\github\keiyoushi\gradle\tasks\GenerateManifestTask.kt"
    $manifestTaskText = Get-Content -LiteralPath $manifestTaskFile -Raw
    $manifestTaskUpdated = $manifestTaskText.Replace(
        'val nsfw = if (cw == ContentWarning.SAFE) "0" else "1"',
        'val nsfw = "0"'
    )
    $manifestTaskUpdated = [regex]::Replace(
        $manifestTaskUpdated,
        'val cwValue = when \(cw\) \{[\s\S]*?\n\s*\}',
        'val cwValue = "0"',
        1
    )
    if ($manifestTaskUpdated -notmatch 'val nsfw = "0"' -or $manifestTaskUpdated -notmatch 'val cwValue = "0"') {
        throw "Unable to enforce neutral manifest metadata generator"
    }
    [IO.File]::WriteAllText($manifestTaskFile, $manifestTaskUpdated, [Text.UTF8Encoding]::new($false))

    foreach ($spec in $buildSpecs) {
        $buildFile = Join-Path $VendorDir ($spec.Module + "\build.gradle.kts")
        if (-not (Test-Path -LiteralPath $buildFile)) { continue }

        $text = Get-Content -LiteralPath $buildFile -Raw
        $updated = $text.
            Replace('ContentWarning.MIXED', 'ContentWarning.SAFE').
            Replace('ContentWarning.NSFW', 'ContentWarning.SAFE')
        if ($updated -match 'contentWarning\s*=\s*ContentWarning\.(?:MIXED|NSFW)') {
            throw "Unable to neutralize content warning: $buildFile"
        }
        if ($updated -ne $text) {
            [IO.File]::WriteAllText($buildFile, $updated, [Text.UTF8Encoding]::new($false))
        }
        $verified = Get-Content -LiteralPath $buildFile -Raw
        if ($verified -match 'contentWarning\s*=\s*ContentWarning\.(?:MIXED|NSFW)') {
            throw "Content warning rewrite did not persist: $buildFile"
        }
    }
}

if (-not $env:JAVA_HOME -and (Test-Path "D:\Android\jbr")) { $env:JAVA_HOME = "D:\Android\jbr" }
if (-not $env:ANDROID_HOME -and (Test-Path "D:\AndroidSDK")) { $env:ANDROID_HOME = "D:\AndroidSDK" }

Ensure-Checkout
Ensure-AuroraCustomSources
Ensure-ManwaShizukuSource
Ensure-QualitySources
Ensure-ManwaMirrorOverride
Ensure-GoDaMirrorOverride
Ensure-BaoziMirrorOverride
Ensure-BoyLoveMirrorOverride
Ensure-NeutralContentWarnings
New-Item -ItemType Directory -Force -Path `
    $ApkDir,
    $IconDir,
    (Join-Path $TestingRepoDir "apk"),
    (Join-Path $TestingRepoDir "icon") | Out-Null

if (-not $SkipBuild) {
    if (-not $env:ANDROID_HOME) { throw "ANDROID_HOME is required to build batch sources" }
    $sdkProperty = "sdk.dir=" + ($env:ANDROID_HOME -replace '\\', '/') + "`n"
    [IO.File]::WriteAllText((Join-Path $VendorDir "local.properties"), $sdkProperty, [Text.Encoding]::ASCII)
    foreach ($spec in $buildSpecs) {
        $moduleBuildDir = Join-Path $VendorDir ($spec.Module + "\build")
        if (Test-Path -LiteralPath $moduleBuildDir) {
            Remove-Item -LiteralPath $moduleBuildDir -Recurse -Force
        }
    }
    $tasks = $buildSpecs.Module | ForEach-Object { ":$($_ -replace '/', ':'):assembleRelease" }
    Push-Location $VendorDir
    try {
        if ($IsWindows -or $env:OS -match "Windows") {
            & .\gradlew.bat @tasks --no-daemon --no-configuration-cache --rerun-tasks
        } else {
            & chmod +x ./gradlew
            & ./gradlew @tasks --no-daemon --no-configuration-cache --rerun-tasks
        }
        if ($LASTEXITCODE -ne 0) { throw "Batch extension build failed with exit $LASTEXITCODE" }

        # Rebuild warning-bearing upstream modules in isolation.  The large
        # multi-project invocation can otherwise retain their original DSL
        # value while configuring tasks, even though the source file and
        # manifest generator have already been normalized above.
        foreach ($module in @("src/zh/boylove")) {
            if ($module -notin $activeSpecs.Module) { continue }
            $moduleBuildDir = Join-Path $VendorDir ($module + "\build")
            if (Test-Path -LiteralPath $moduleBuildDir) {
                Remove-Item -LiteralPath $moduleBuildDir -Recurse -Force
            }
            $task = ":$($module -replace '/', ':'):assembleRelease"
            if ($IsWindows -or $env:OS -match "Windows") {
                & .\gradlew.bat $task --no-daemon --no-configuration-cache --rerun-tasks
            } else {
                & ./gradlew $task --no-daemon --no-configuration-cache --rerun-tasks
            }
            if ($LASTEXITCODE -ne 0) { throw "Isolated extension rebuild failed: $module" }

            $generatedManifest = Join-Path $moduleBuildDir "generated\manifests\generateExtensionManifest\AndroidManifest.xml"
            $manifestText = Get-Content -LiteralPath $generatedManifest -Raw
            $manifestUpdated = [regex]::Replace(
                $manifestText,
                '(<meta-data android:name="tachiyomi\.extension\.nsfw" android:value=")[^"]+(" />)',
                '${1}0${2}'
            )
            $manifestUpdated = [regex]::Replace(
                $manifestUpdated,
                '(<meta-data android:name="tachiyomix\.contentWarning" android:value=")[^"]+(" />)',
                '${1}0${2}'
            )
            if ($manifestUpdated -notmatch 'tachiyomi\.extension\.nsfw" android:value="0"' -or
                $manifestUpdated -notmatch 'tachiyomix\.contentWarning" android:value="0"') {
                throw "Unable to lock generated manifest metadata: $module"
            }
            [IO.File]::WriteAllText($generatedManifest, $manifestUpdated, [Text.UTF8Encoding]::new($false))

            $manifestGeneratorFile = Join-Path $VendorDir "gradle\build-logic\src\main\kotlin\io\github\keiyoushi\gradle\tasks\GenerateManifestTask.kt"
            $manifestGeneratorText = Get-Content -LiteralPath $manifestGeneratorFile -Raw
            $manifestGeneratorUpdated = $manifestGeneratorText.Replace(
                'out.writeText(',
                'if (!out.exists()) out.writeText('
            )
            if ($manifestGeneratorUpdated -notmatch 'if \(!out\.exists\(\)\) out\.writeText\(') {
                throw "Unable to lock the manifest generator output: $module"
            }
            [IO.File]::WriteAllText($manifestGeneratorFile, $manifestGeneratorUpdated, [Text.UTF8Encoding]::new($false))

            if ($IsWindows -or $env:OS -match "Windows") {
                & .\gradlew.bat $task --no-daemon --no-configuration-cache --rerun-tasks
            } else {
                & ./gradlew $task --no-daemon --no-configuration-cache --rerun-tasks
            }
            if ($LASTEXITCODE -ne 0) { throw "Locked-manifest extension rebuild failed: $module" }
        }
    } finally { Pop-Location }
}

$expectedFingerprint = (Get-Content (Join-Path $RepoDir "repo.json") -Raw | ConvertFrom-Json).meta.signingKeyFingerprint
$apkSigner = Get-ChildItem (Join-Path $env:ANDROID_HOME "build-tools") -Directory |
    Sort-Object Name -Descending | ForEach-Object {
        @(
            (Join-Path $_.FullName "apksigner.bat"),
            (Join-Path $_.FullName "apksigner")
        )
    } |
    Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $apkSigner) { throw "apksigner not found under $env:ANDROID_HOME" }

$entries = foreach ($spec in $activeSpecs) {
    $planned = $catalogBatch | Where-Object package -eq $spec.Package | Select-Object -First 1
    $assetRoot = if ([string]$planned.channel -eq "testing") { $TestingRepoDir } else { $RepoDir }
    $assetApkDir = Join-Path $assetRoot "apk"
    $assetIconDir = Join-Path $assetRoot "icon"
    $built = Join-Path $VendorDir ($spec.Module + "/build/outputs/apk/release/" + $spec.Apk)
    $dest = Join-Path $assetApkDir $spec.Apk
    if ($spec.Package -in $buildSpecs.Package -and (Test-Path $built)) {
        Copy-Item -LiteralPath $built -Destination $dest -Force
    }
    elseif (-not (Test-Path $dest)) { throw "Missing batch APK: $built" }

    if ($env:AURORA_EXTENSION_SIGNING_KEYSTORE) {
        if (-not $env:AURORA_EXTENSION_KEY_ALIAS -or
            -not $env:AURORA_EXTENSION_STORE_PASSWORD -or
            -not $env:AURORA_EXTENSION_KEY_PASSWORD) {
            throw "Production signing environment is incomplete"
        }
        $signed = "$dest.signed"
        Remove-Item -LiteralPath $signed -Force -ErrorAction SilentlyContinue
        & $apkSigner sign `
            --ks $env:AURORA_EXTENSION_SIGNING_KEYSTORE `
            --ks-key-alias $env:AURORA_EXTENSION_KEY_ALIAS `
            --ks-pass env:AURORA_EXTENSION_STORE_PASSWORD `
            --key-pass env:AURORA_EXTENSION_KEY_PASSWORD `
            --out $signed `
            $dest
        if ($LASTEXITCODE -ne 0) { throw "Failed to production-sign $($spec.Apk)" }
        Move-Item -LiteralPath $signed -Destination $dest -Force
        Remove-Item -LiteralPath "$signed.idsig" -Force -ErrorAction SilentlyContinue
    }

    $certOutput = (& $apkSigner verify --print-certs $dest) -join "`n"
    if ($certOutput -notmatch 'certificate SHA-256 digest:\s*([0-9a-fA-F]+)') { throw "Unable to read signer: $dest" }
    $fingerprint = $Matches[1].ToLowerInvariant()
    if ($fingerprint -ne $expectedFingerprint) {
        throw "Signer mismatch for $($spec.Apk): expected $expectedFingerprint, got $fingerprint"
    }

    $iconRelative = if ($spec.PSObject.Properties.Name -contains "Icon") {
        $spec.Icon
    } else {
        $spec.Module + "/res/mipmap-hdpi/ic_launcher.png"
    }
    $iconSource = Join-Path $VendorDir $iconRelative
    $iconDest = Join-Path $assetIconDir ($spec.Package + ".png")
    if (Test-Path $iconSource) { Copy-Item -LiteralPath $iconSource -Destination $iconDest -Force }
    elseif (-not (Test-Path $iconDest)) { throw "Missing batch icon: $iconSource" }

    $file = Get-Item -LiteralPath $dest
    [ordered]@{
        name = $spec.Name; pkg = $spec.Package; apk = $spec.Apk
        sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $dest).Hash.ToLowerInvariant()
        size = $file.Length; lang = $spec.Lang; code = $spec.Code; version = $spec.Version
        nsfw = $spec.Nsfw; sources = $spec.Sources; channel = [string]$planned.channel
    }
}

# Keep exactly one APK per managed package. Version bumps otherwise leave the
# previous tracked APK in the generated site, which the integrity gate rejects
# as an orphan even though the catalogue points at the new artifact.
foreach ($spec in $activeSpecs) {
    $versionMarker = $spec.Apk.LastIndexOf("-v")
    if ($versionMarker -lt 0) { throw "Unable to identify APK version suffix: $($spec.Apk)" }
    $apkPrefix = $spec.Apk.Substring(0, $versionMarker + 2)
    $planned = $catalogBatch | Where-Object package -eq $spec.Package | Select-Object -First 1
    $expectedRoot = if ([string]$planned.channel -eq "testing") { $TestingRepoDir } else { $RepoDir }
    foreach ($channelRoot in @($RepoDir, $TestingRepoDir)) {
        $channelApkDir = Join-Path $channelRoot "apk"
        Get-ChildItem -LiteralPath $channelApkDir -Filter "$apkPrefix*.apk" -File -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Name -ne $spec.Apk -or
                [IO.Path]::GetFullPath($channelRoot) -ne [IO.Path]::GetFullPath($expectedRoot)
            } |
            Remove-Item -Force
    }
}

$retiredPackages = @(
    "eu.kanade.tachiyomi.extension.en.mangabat"
    "eu.kanade.tachiyomi.extension.en.mangakakalot"
    "eu.kanade.tachiyomi.extension.zh.comicabc"
    "eu.kanade.tachiyomi.extension.zh.komiic"
    "eu.kanade.tachiyomi.extension.zh.manhuagui"
    "eu.kanade.tachiyomi.extension.zh.manhuaren"
    "eu.kanade.tachiyomi.extension.zh.manwa"
    "eu.kanade.tachiyomi.extension.zh.mycomic"
    "eu.kanade.tachiyomi.extension.zh.vomic"
)
$batchPackages = @($specs.Package) + $retiredPackages
$encoding = [Text.UTF8Encoding]::new($false)
foreach ($channel in @("stable", "testing")) {
    $channelRoot = if ($channel -eq "testing") { $TestingRepoDir } else { $RepoDir }
    $indexPath = Join-Path $channelRoot "index.min.json"
    $existing = if (Test-Path $indexPath) { @(Get-Content $indexPath -Raw | ConvertFrom-Json) } else { @() }
    $channelEntries = @($entries | Where-Object channel -eq $channel | ForEach-Object {
        $copy = [ordered]@{}
        foreach ($property in $_.GetEnumerator()) {
            if ($property.Key -ne "channel") { $copy[$property.Key] = $property.Value }
        }
        [pscustomobject]$copy
    })
    $merged = @($existing | Where-Object { $_.pkg -notin $batchPackages }) + $channelEntries
    $json = $merged | ConvertTo-Json -Depth 10
    [IO.File]::WriteAllText((Join-Path $channelRoot "index.min.json"), $json + "`n", $encoding)
    [IO.File]::WriteAllText((Join-Path $channelRoot "index.json"), $json + "`n", $encoding)
}

Write-Host "==> Added $($entries.Count) audited website extensions across stable and testing channels"
$entries | ForEach-Object { Write-Host ("{0}`t{1}`t{2}" -f $_.pkg, $_.version, $_.apk) }
