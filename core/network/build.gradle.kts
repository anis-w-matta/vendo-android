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
        // Fallback only - the salesman enters the real server address on
        // the Login screen (persisted via SettingsDataStore, applied by
        // ServerUrlInterceptor) so the same build works on any network.
        // This value is just what a fresh install shows as a starting
        // point: 10.0.2.2 is the Android emulator's alias for the host
        // machine's localhost.
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000/\"")
        // Shared-secret gate matching the backend's optional `api_key`
        // setting (app/config.py) - blank means the header is omitted and
        // the backend's check stays a no-op, same as today. Set this (and
        // the matching backend .env value) before deploying anywhere the
        // port is reachable beyond localhost/trusted LAN.
        buildConfigField("String", "API_KEY", "\"\"")
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
