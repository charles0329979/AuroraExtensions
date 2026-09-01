import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YiciYuan Manhua"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "追漫画"
        lang = "zh"
        baseUrl = "https://www.ycymh.com"
    }
}
