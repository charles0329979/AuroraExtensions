plugins {
    id("com.android.application")
}

// Override: ./gradlew :app:assembleDebug -PdemoBaseUrl=http://192.168.x.x:8765
val demoBaseUrl: String =
    (project.findProperty("demoBaseUrl") as String?) ?: "http://127.0.0.1:8765"
val demoHost: String = demoBaseUrl
    .removePrefix("http://")
    .removePrefix("https://")
    .substringBefore('/')
    .substringBefore(':')
    .ifBlank { "127.0.0.1" }

android {
    namespace = "eu.kanade.tachiyomi.extension.all.aurorascripteclan"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.all.aurorascripteclan"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.6.1"
    }

    buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf("META-INF/**", "kotlin/**", "DebugProbesKt.bin")
        }
    }
}

val generateLanManifest = tasks.register("generateLanManifest") {
    val outFile = layout.projectDirectory.file("src/main/assets/aurora/scripted/manifest.json")
    inputs.property("demoBaseUrl", demoBaseUrl)
    inputs.property("demoHost", demoHost)
    outputs.file(outFile)
    doLast {
        val json = """
            {
              "protocolVersion": 2,
              "sourceId": "aurora.scripted.lan",
              "name": "Aurora Scripted LAN",
              "lang": "en",
              "baseUrl": "$demoBaseUrl",
              "capabilities": {
                "operations": ["POPULAR","LATEST","SEARCH","DETAILS","CHAPTERS","PAGES"],
                "allowedHosts": ["$demoHost"],
                "allowedImageHosts": ["$demoHost"],
                "allowHttp": true,
                "endpoints": {
                  "popular": "/popular?page={page}",
                  "latest": "/latest?page={page}",
                  "search": "/search?q={query}&page={page}",
                  "details": "{mangaUrl}",
                  "chapters": "{mangaUrl}/chapters",
                  "pages": "{chapterUrl}/pages"
                }
              },
              "scriptEntry": "main.js"
            }
        """.trimIndent()
        outFile.asFile.parentFile.mkdirs()
        outFile.asFile.writeText(json + "\n")
        logger.lifecycle("Wrote LAN scripted manifest baseUrl=$demoBaseUrl host=$demoHost")
    }
}

tasks.named("preBuild").configure { dependsOn(generateLanManifest) }
