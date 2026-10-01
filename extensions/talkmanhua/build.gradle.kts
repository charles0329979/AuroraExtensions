import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TalkManhua"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "沐沐漫画"
        lang = "zh"
        baseUrl = "https://www.talkmanhua.com"
    }
}
