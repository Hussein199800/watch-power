import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hussein.watchpower.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hussein.watchpower.wear"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // مفتاح تجريبي علني ثابت حتى تُثبَّت التحديثات فوق النسخة القديمة. ليس مفتاح نشر.
    signingConfigs {
        create("shared") {
            storeFile = file("../keystore/wear-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("shared") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("androidx.wear.tiles:tiles:1.5.0")
    implementation("androidx.wear.protolayout:protolayout:1.3.0")
    implementation("androidx.wear.protolayout:protolayout-expression:1.3.0")
    implementation("androidx.work:work-runtime:2.10.5")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("com.google.guava:guava:33.4.8-android")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
