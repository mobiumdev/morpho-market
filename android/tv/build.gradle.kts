// Morpho Market on a TV: Fire TV, Android TV and Google TV. The same store and
// screens as the phone app (:shared, :ui), in a shell for a remote: tabs across
// the top, a strong focus highlight, and layouts for a 1080p screen.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.mobium.shop.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.mobium.shop.tv"
        // Fire OS 6 is Android 7.1 (API 25); Fire OS 7 is Android 9, Fire OS 8 Android 11.
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key: an app under test, installed by hand, never published.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(project(":ui"))
}
