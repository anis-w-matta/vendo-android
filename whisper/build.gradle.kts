plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.vendo.whisper"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        ndk {
            // arm64-v8a is the primary target (virtually all real devices
            // since ~2020); armeabi-v7a covers older/budget hardware.
            // x86_64 is left out of release builds - only useful for the
            // emulator - see debug-only override below.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // whisper.cpp itself is vendored under src/main/cpp/whisper.cpp/ (copied
    // from the upstream repo's src/, include/, ggml/ - see PROGRESS.md).
    // The JNI bridge (src/main/cpp/CMakeLists.txt + jni.c) is adapted from
    // whisper.cpp's own examples/whisper.android/lib/src/main/jni/whisper/.
    ndkVersion = "25.2.9519653" // matches the upstream whisper.android sample

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        debug {
            ndk { abiFilters += "x86_64" } // emulator support, debug only
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("javax.inject:javax.inject:1")
}
