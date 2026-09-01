import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}
keiyoushi {
    name = "Kaixinman"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "开心漫画"
        lang = "zh"
        baseUrl = "https://www.kaixinman.com"
    }
}
