import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kanman"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "看漫画（免费章节）"
        lang = "zh"
        baseUrl = "https://m.kanman.com"
    }
}
