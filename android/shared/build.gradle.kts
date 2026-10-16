// The store itself: catalog, pricing, checkout, the cart and orders, and the
// bug switch. No screens. The phone app (:app) and the TV app (:tv) are two
// front doors on this one module, so a price or a bug is the same in both.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.mobium.shop.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 25
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.all {
            val spec = rootProject.file("../spec")
            it.systemProperty("spec.dir", spec.absolutePath)
            // An input, or Gradle keeps a pass from before a case changed.
            it.inputs.dir(spec).withPropertyName("spec")
        }
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
    // Shop's state is Compose state, so either front door recomposes on it.
    api("androidx.compose.runtime:runtime")

    testImplementation("junit:junit:4.13.2")
    // android.jar's org.json is a stub off the device; the real one reads the spec's cases.
    testImplementation("org.json:json:20240303")
}
