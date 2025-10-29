// Import library dan konfigurasi Android Studio standar
plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("com.lagradost.cloudstream3") version "1.7.0" // Ganti dengan versi CS3 terbaru
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

// use an integer for version numbers
version = 1

cloudstream {
    // Nama Plugin, gunakan OMDb dan Fmovies sebagai deskripsi
    description = "AdiOMDb: Metadata dari OMDb, Streaming dari Fmoviesunblocked.net" 
    language    = "en" 
    authors = listOf("AdiUser") 

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 

    // Tipe Konten yang didukung. Tambahkan AsianDrama jika ingin difilter
    tvTypes = listOf("TvSeries", "Movie") 

    // Icon (Mengambil favicon dari domain utama OMDb)
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
