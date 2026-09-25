plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.deon.followwidget.live"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.deon.followwidget.live"
        minSdk = 26
        targetSdk = 34
        versionCode = 46
        versionName = "1.0"
    }

    signingConfigs {
        create("pinnedDebug") {
            storeFile = file("$rootDir/../android-toolchain/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            // Pinned signing key: the default ~/.android/debug.keystore lives
            // outside the persistent workspace (/root/.android) and can be
            // wiped, which changes the APK signature and breaks updates
            // ("package conflicts"). This key is the stable one.
            signingConfig = signingConfigs.getByName("pinnedDebug")
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

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
