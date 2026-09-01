import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manquanzi"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "漫圈子"
        lang = "zh"
        baseUrl = "https://www.9mqz.com"
    }
}
