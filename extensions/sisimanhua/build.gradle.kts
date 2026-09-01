import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}
keiyoushi {
    name = "Sisi Manhua"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "思思漫画"
        lang = "zh"
        baseUrl = "https://m.sisimanhua.com"
    }
}
