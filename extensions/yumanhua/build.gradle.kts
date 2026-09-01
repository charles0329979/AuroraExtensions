import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yumanhua"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mmlook"

    source {
        name = "漫画客"
        lang = "zh"
        baseUrl = "http://yumanhua.com"
    }
}
