plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.focuslock.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.focuslock.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "3.1"
    }

    buildTypes {
        release {
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
}
