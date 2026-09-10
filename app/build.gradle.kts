plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ccwu.orbitime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ccwu.orbitime"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "0.12.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}
