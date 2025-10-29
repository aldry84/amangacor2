// --- BLOK WAJIB: DEFINISI PLUGINS DAN REPOSITORI ---
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Mendefinisikan versi plugin yang diperlukan di buildscript
        classpath("com.lagradost.cloudstream3:cloudstream-plugin:1.7.0") 
        classpath("com.android.tools.build:gradle:8.2.0") // Android Gradle Plugin (AGP)
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0") // Kotlin Plugin
    }
}

// Mengaplikasikan Plugin (Mengganti Blok 'plugins' dengan 'apply')
apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")
apply(plugin = "kotlin-kapt")
apply(plugin = "com.lagradost.cloudstream3")


// Tambahkan definisi repositori untuk dependensi utama project
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
    // Ini mengasumsikan folder proyek Anda memiliki modul ':lib:cloudstream-impl'
    implementation(project(":lib:cloudstream-impl")) 
}
