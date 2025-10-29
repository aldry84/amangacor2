// --- BLOK WAJIB UNTUK GRADLE/ANDROID ---
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

// Konfigurasi Android (Wajib ada)
android {
    namespace = "com.AdiOMDb" // Menggunakan namespace AdiOMDb yang benar
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0" 
    }
}
// --- AKHIR BLOK WAJIB ---


// use an integer for version numbers
version = 1

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "AdiOMDb (Metadata OMDb dan Streaming Fmoviesunblocked.net)" 
    language    = "en" 
    authors = listOf("AdiOMDbUser") 

    status = 1 

    tvTypes = listOf("TvSeries", "AsianDrama", "Movie")

    iconUrl="https://www.google.com/s2/favicons?domain=fmoviesunblocked.net&sz=%size%"

    isCrossPlatform = true
}

// Dependensi yang diperlukan (Wajib ada)
dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation(project(":lib:cloudstream-impl"))
}
