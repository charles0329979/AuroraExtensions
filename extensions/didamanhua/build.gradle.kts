import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dida Manhua"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "滴答漫画"
        lang = "zh"
        baseUrl = "http://ddmanhua.com"
    }
}
