plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "eu.kanade.tachiyomi.extension.all.aurorascripted"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.all.aurorascripted"
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

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.1.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:2.1.10")
    }
}

dependencies {
    compileOnly(project(":tachiyomix"))
    compileOnly(kotlin("stdlib"))
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    // Host provides kotlin-stdlib + okhttp; package Rhino (+ scripted-core).
    implementation(project(":scripted-core")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "com.squareup.okhttp3")
        exclude(group = "com.squareup.okio")
    }
}
