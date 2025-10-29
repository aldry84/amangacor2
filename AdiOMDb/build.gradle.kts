// --- BLOK WAJIB: MENDUKUNG PLUGIN DAN ANDROID ---

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    // Plugin utama Cloudstream3
    id("com.lagradost.cloudstream3")
}

// Tambahkan definisi repositori agar Gradle dapat menemukan dependensi
repositories {
    google()
    mavenCentral()
}

// Konfigurasi Android
android {
    namespace = "com.AdiOMDb"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0" 
    }
}

// --- METADATA CLOUDSTREAM (SESUAI PERMINTAAN ANDA) ---

// use an integer for version numbers
version = 1

cloudstream {
    description = "AdiOMDb: Metadata OMDb dan Streaming Fmoviesunblocked" 
    language    = "en"
    authors = listOf("AdiOMDbUser") 

    status = 1 

    // Menggunakan tipe umum yang kompatibel dengan OMDb
    tvTypes = listOf("TvSeries", "Movie") 

    // Menggunakan domain OMDb sebagai sumber ikon utama
    iconUrl="https://www.google.com/s2/favicons?domain=omdbapi.com&sz=%size%"

    isCrossPlatform = true
}

// Dependensi yang diperlukan
dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation(project(":lib:cloudstream-impl"))
}
