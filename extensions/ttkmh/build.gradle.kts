import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TTKMH"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "天天看漫画"
        lang = "zh"
        baseUrl = "https://www.ttkmh.com"
    }
}
