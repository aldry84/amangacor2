@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    // Memastikan plugin maven-publish dimuat
    id("maven-publish") 
}

// Nomor versi plugin
version = 465 

// Versi Cloudstream3 (Contoh)
val cloudstream_version = "3.11.0" 

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    
    defaultConfig {
        namespace = "com.AdicinemaxNew"
        minSdk = 26 
        targetSdk = 34 
        
        // Memuat kunci API dari file lokal (local.properties)
        val properties = Properties()
        // Asumsi local.properties ada di root proyek
        properties.load(project.rootProject.file("local.properties").inputStream())
        
        android.buildFeatures.buildConfig = true

        // =================================================================
        // FIELD KONFIGURASI BUILD (Kunci yang Sesuai dengan Proyek Kita)
        // =================================================================

        buildConfigField("String", "TMDB_API", "\"${properties.getProperty("TMDB_API")}\"")
        buildConfigField("String", "REMOTE_PROXY_LIST", "\"${properties.getProperty("REMOTE_PROXY_LIST")}\"") 
        
        // Vidsrc & Xprime APIs
        buildConfigField("String", "VIDSRC_CC_API", "\"${properties.getProperty("VIDSRC_CC_API")}\"") 
        buildConfigField("String", "VIDSRC_XYZ", "\"${properties.getProperty("VIDSRC_XYZ")}\"") 
        buildConfigField("String", "XPRIME_API", "\"${properties.getProperty("XPRIME_API")}\"") 
        
        // Kunci MovieBox/Enkripsi (untuk XPrime/API canggih)
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_ALT", "\"${properties.getProperty("MOVIEBOX_SECRET_KEY_ALT")}\"")
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_DEFAULT", "\"${properties.getProperty("MOVIEBOX_SECRET_KEY_DEFAULT")}\"")
        
        // HubCloud & GDFlix (Jika diperlukan oleh scraper)
        buildConfigField("String", "HUBCLOUD_API", "\"${properties.getProperty("HUBCLOUD_API")}\"")
        buildConfigField("String", "GDFLIX_API", "\"${properties.getProperty("GDFLIX_API")}\"")

        // Subtitle API
        buildConfigField("String", "OPENSUBTITLES_API", "\"https://opensubtitles-v3.strem.io\"")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // WAJIB: Mendaftarkan komponen Android untuk publikasi Maven
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

// Konfigurasi Cloudstream
cloudstream {
    language = "en"
     description = "Adicinemax: High-quality, non-anime sources (Movies & TV) based on robust API extraction."
     authors = listOf("Phisher98", "AdicinemaxDev") 
     status = 1 
     tvTypes = listOf("TvSeries", "Movie") 
     iconUrl = "https://i.imgur.com/example-icon.png" // Ganti dengan URL ikon yang relevan
     requiresResources = true
     isCrossPlatform = false
}

dependencies {
    // Dependensi Cloudstream3 Wajib
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:$cloudstream_version")

    // Dependensi yang dibutuhkan untuk parsing JSON
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    
    // Dependensi UI/Platform
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.browser:browser:1.9.0")
}

// Blok Publikasi Utama
publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            
            from(components["release"])
        }
    }
}
