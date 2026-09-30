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
        versionCode = 50
        versionName = "1.0"
    }

    signingConfigs {
        // Release key: ~/workspace/android-keystores/instaw-release.jks
        // (NOT in git). Passwords via env: INSTAW_KEYSTORE_PASSWORD.
        // Only wired up when the env var is present, so plain debug
        // builds never touch it.
        create("release") {
            storeFile = file(System.getenv("INSTAW_KEYSTORE_PATH")
                ?: "$rootDir/../android-keystores/instaw-release.jks")
            storePassword = System.getenv("INSTAW_KEYSTORE_PASSWORD")
            keyAlias = "instaw"
            keyPassword = System.getenv("INSTAW_KEYSTORE_PASSWORD")
        }
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
            if (System.getenv("INSTAW_KEYSTORE_PASSWORD") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    // Offline toolchain: lint-gradle isn't in the vendored cache, so the
    // release "vital lint" check can't run. Debug builds were already fine.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
