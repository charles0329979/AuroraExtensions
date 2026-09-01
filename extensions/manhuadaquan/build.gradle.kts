import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}
keiyoushi {
    name = "Manhua Daquan"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "漫画大全"
        lang = "zh"
        baseUrl = "http://www.yueman1.cc"
    }
}
