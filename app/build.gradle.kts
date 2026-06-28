plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.gempa"
    compileSdk = 35 // Turunkan dari 36 ke 35 atau 34

    defaultConfig {
        applicationId = "com.example.gempa"
        minSdk = 24
        targetSdk = 35 // Turunkan dari 36 ke 35 atau 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // ... sisa kode lainnya

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
}

dependencies {
    // Gunakan versi manual agar tidak dipaksa ke SDK 36 oleh version catalog (libs)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // Retrofit untuk HTTP request
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    // Gson converter untuk parsing JSON BMKG
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // OkHttp logging (opsional, untuk debug)
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Library Maps & Location
// Hapus library google maps lama
    // implementation("com.google.android.gms:play-services-maps:...")

    // Tambahkan library OSM
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Library pendukung lainnya tetap sama
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // Unit Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}