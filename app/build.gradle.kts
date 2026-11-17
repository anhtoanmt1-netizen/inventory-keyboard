plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.inventory.scannerkeyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.inventory.scannerkeyboard"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "2.2"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    // CameraX
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    // ML Kit: model đóng gói sẵn trong app, chạy offline, không cần Google Play Services
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // Tạo mã QR để xuất giao diện / cài đặt (chỉ phần mã hóa, không kéo thêm thư viện nặng)
    implementation("com.google.zxing:core:3.5.3")
}
