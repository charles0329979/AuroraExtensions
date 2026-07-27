plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "eu.kanade.tachiyomi.extension.all.aurorastub"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.all.aurorastub"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.6.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf("META-INF/**", "kotlin/**", "DebugProbesKt.bin")
        }
    }
}

dependencies {
    // Mirrors com.github.mihonapp:tachiyomix:1.6 (tag 1.6.0) stub surface for compileOnly.
    // Host app provides kotlin-stdlib at runtime; packaging it causes LinkageError.
    compileOnly(project(":tachiyomix"))
    compileOnly(kotlin("stdlib"))
}