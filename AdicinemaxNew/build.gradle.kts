@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties
import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

// Versi Plugin
version = 465

// Versi Cloudstream3 (Contoh)
val cloudstream_version = "3.11.0" 

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    
    defaultConfig {
        // Nama paket untuk plugin Anda
        namespace = "com.AdicinemaxNew"
        minSdk = 26 
        // Perhatian: 'targetSdk' sudah usang, namun tetap dipertahankan untuk kompatibilitas lingkungan Cloudstream3 lama
        targetSdk = 34 
        
        // Memuat properties dari local.properties
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        
        android.buildFeatures.buildConfig = true

        // ====================================================================
        // Konfigurasi BuildConfig untuk API Kunci & URL
        // ====================================================================

        buildConfigField("String", "TMDB_API", "\"${properties.getProperty("TMDB_API")}\"")
        buildConfigField("String", "REMOTE_PROXY_LIST", "\"${properties.getProperty("REMOTE_PROXY_LIST")}\"") 
        buildConfigField("String", "VIDSRC_CC_API", "\"${properties.getProperty("VIDSRC_CC_API")}\"") 
        buildConfigField("String", "VIDSRC_XYZ", "\"${properties.getProperty("VIDSRC_XYZ")}\"") 
        buildConfigField("String", "XPRIME_API", "\"${properties.getProperty("XPRIME_API")}\"") 
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_ALT", "\"${properties.getProperty("MOVIEBOX_SECRET_KEY_ALT")}\"")
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_DEFAULT", "\"${properties.getProperty("MOVIEBOX_SECRET_KEY_DEFAULT")}\"")
        buildConfigField("String", "OPENSUBTITLES_API", "\"https://opensubtitles-v3.strem.io\"")
        buildConfigField("String", "HUBCLOUD_API", "\"${properties.getProperty("HUBCLOUD_API")}\"")
        buildConfigField("String", "GDFLIX_API", "\"${properties.getProperty("GDFLIX_API")}\"")
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
}

// Konfigurasi Cloudstream
cloudstream {
    language = "en"
     description = "Adicinemax: High-quality, non-anime sources using advanced API extraction."
     authors = listOf("Phisher98", "AdicinemaxDev") 
     status = 1 
     tvTypes = listOf("TvSeries", "Movie") 
     iconUrl = "https://i.imgur.com/example-icon.png"
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

// Blok Publikasi yang diperbaiki
publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            
            // Perbaikan: Menggunakan 'assembleRelease' dan memastikan task tersebut berjalan
            artifact(project.tasks.getByName("assembleRelease")) {
                // Konfigurasi tambahan: memastikan task build AAR dijalankan sebelum publishing
                (this as org.gradle.api.artifacts.PublishArtifact).builtBy(project.tasks.getByName("assembleRelease"))
            }
        }
    }
}
