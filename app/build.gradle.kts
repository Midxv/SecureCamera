plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.midxv.secam"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.midxv.secam"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // 1. CameraX Core Pipeline (For High-Res HEIC & 60fps HEVC)
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-video:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    implementation("androidx.camera:camera-extensions:$camerax_version")

    // 2. AndroidX Security (For AES-256-GCM Encryption)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // 3. Location Services (For EXIF data on photos/videos)
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // 4. UI Components (Material Design & Complex Layouts)
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // ---------------------------------------------------------
    // NEW: MEDIA VIEWING & STREAMING ENGINES
    // ---------------------------------------------------------

    // 5. ExoPlayer (AndroidX Media3) - For playing local 1080p 60fps video
    val media3_version = "1.3.0"
    implementation("androidx.media3:media3-exoplayer:$media3_version")
    implementation("androidx.media3:media3-ui:$media3_version")

    // 6. Coil - Modern, Kotlin-first Image Loader
    implementation("io.coil-kt:coil:2.6.0")

    // 7. Glide - Classic, high-performance Image Loader
    implementation("com.github.bumptech.glide:glide:4.16.0")
}