import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "360 Manhua"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "360漫画"
        lang = "zh"
        baseUrl = "https://www.360mh.cc"
    }
}
