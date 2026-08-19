plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.vendo.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        // 10.0.2.2 is the Android emulator's alias for the host machine's
        // localhost - this reaches a backend run via `uvicorn` on the same
        // dev machine. For a physical device on the same network, replace
        // with that machine's LAN IP, e.g. "http://192.168.1.20:8000/".
        buildConfigField("String", "BASE_URL", "\"http://10.50.26.77:8000/\"")
    }

    buildFeatures {
        buildConfig = true
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
    implementation(project(":core:datastore"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("javax.inject:javax.inject:1")
}
