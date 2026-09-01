import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dumanwu"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "读漫屋"
        lang = "zh"
        baseUrl = "https://m.dumanwu.org"
    }
}
