plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cosmicindustries.umbra"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.cosmicindustries.umbra"
        // VpnService.Builder.addAllowedApplication/addDisallowedApplication and the
        // Shizuku API both require API 26+; hev-socks5-tunnel and byedpi build cleanly
        // back to API 21 but there is no reason to support anything pre-Oreo here.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        getByName("debug") {
            // Pinned so every build — CI or local — is signed with the same debug
            // key. Without this, AGP falls back to auto-generating a fresh
            // ~/.android/debug.keystore per machine; since CI runs on a clean VM
            // every time, each GitHub Actions build got a different random debug
            // key, so installing a newer release APK over an older one silently
            // failed with a signature mismatch instead of updating — the actual
            // cause behind "the new buttons aren't showing up," since the device
            // was still running whatever build installed first. Not a secret:
            // Android debug keys are never used for anything but local/dev
            // installs, so committing this is the normal, documented fix.
            storeFile = file("debug.keystore")
            // NOSONAR x3 below: static-analysis "hard-coded credential" rules can't tell
            // these apart from a real secret, but "android"/"androiddebugkey" is
            // the public, industry-standard Android debug-keystore convention
            // (the exact values AGP itself uses for its auto-generated debug
            // keystore) — see the comment above for why this one is committed.
            storePassword = "android" // NOSONAR
            keyAlias = "androiddebugkey" // NOSONAR
            keyPassword = "android" // NOSONAR
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Every dependency version in gradle/libs.versions.toml is already an exact
// pin, not a range — but that alone doesn't cover transitive dependencies
// pulled in without a version of their own choosing. Locking makes the full
// resolved graph (transitives included) explicit and reproducible: CI
// regenerates gradle.lockfile on every push (see build-umbra.yml's "Generate
// dependency locks" + "Commit updated lockfile" steps) and a build fails
// loudly if resolution would otherwise pick something different from what's
// committed. Declared here rather than via `allprojects{}` on the root
// project: :app is the only module with any real dependencies to lock — the
// root project has zero configurations of its own (confirmed by CI: running
// the bare `dependencies` task there reports "No configurations" and writes
// nothing), so a root-level declaration just left Sonar's dependency-lock
// check flagging a build.gradle.kts that could never have a matching
// lockfile next to it.
dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Official WireGuard tunnel library (wraps wireguard-go over JNI). Apache-2.0.
    implementation(libs.wireguard.tunnel)

    // QR-code config import. Same library the official WireGuard Android app uses
    // (confirmed by decompiling its APK: journeyapps package names, zxing_capture.xml
    // layout resources, and CaptureActivity all present) — its CaptureActivity ships
    // in the AAR's own manifest and handles the camera runtime-permission prompt
    // itself, so no extra Activity declaration or permission-request code is needed
    // here beyond the CAMERA permission in AndroidManifest.xml.
    implementation(libs.zxing.embedded)

    // Shizuku: privileged-without-root command execution for the hard per-app
    // firewall path. Apache-2.0. The user must have the separate Shizuku app
    // (or `adb shell` pairing) running on-device; see BUILDING.md.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
