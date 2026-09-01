import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Gufeng Manhua"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "古风漫画"
        lang = "zh"
        baseUrl = "https://www.gfmh.app"
    }
}
