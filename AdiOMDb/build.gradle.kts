// Import library dan konfigurasi Android Studio standar
plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    // JANGAN masukkan versi di sini, versi didefinisikan di file root atau settings.gradle.kts
    id("com.lagradost.cloudstream3")
}

// Tambahkan definisi repositori untuk Gradle menemukan plugin dan dependensi
repositories {
    google() // WAJIB untuk dependensi Android dan plugin Google
    mavenCentral() // WAJIB untuk dependensi umum
    // Tambahkan repositori Cloudstream3 jika ada (jika tidak ditemukan di Maven Central)
    // maven("https://repo.cloudstream.dev/release") 
}

// Gunakan nama paket baru Anda
group = "com.AdiOMDb"

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

// --- CLOUDSTREAM PLUGIN CONFIGURATION ---

version = 1

cloudstream {
    description = "AdiOMDb: Metadata dari OMDb, Streaming dari Fmoviesunblocked.net" 
    language    = "en" 
    authors = listOf("AdiUser") 
    status = 1 
    tvTypes = listOf("TvSeries", "Movie") 
    iconUrl="https://www.google.com/s2/favicons?domain=omdbapi.com&sz=%size%"
    isCrossPlatform = true
}

// Dependensi yang dibutuhkan oleh Cloudstream3
dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // Cloudstream3 Core
    implementation(project(":lib:cloudstream-impl"))
}
