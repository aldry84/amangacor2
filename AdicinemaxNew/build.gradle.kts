@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties
import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish") 
}

// use an integer for version numbers
version = 1

android {
    compileSdk = 34
    
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    
    defaultConfig {
        namespace = "com.AdicinemaxNew"
        minSdk = 26 
        
        val properties = Properties()
        val localProperties = project.rootProject.file("local.properties")
        if (localProperties.exists()) {
            properties.load(localProperties.inputStream())
        }
        
        // ====================================================================
        // Konfigurasi BuildConfig
        // ====================================================================
        buildConfigField("String", "TMDB_API", "\"${properties.getProperty("TMDB_API", "")}\"")
        buildConfigField("String", "REMOTE_PROXY_LIST", "\"${properties.getProperty("REMOTE_PROXY_LIST", "")}\"") 
        buildConfigField("String", "VIDSRC_CC_API", "\"${properties.getProperty("VIDSRC_CC_API", "")}\"") 
        buildConfigField("String", "VIDSRC_XYZ", "\"${properties.getProperty("VIDSRC_XYZ", "")}\"") 
        buildConfigField("String", "XPRIME_API", "\"${properties.getProperty("XPRIME_API", "")}\"") 
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_ALT", "\"${properties.getProperty("MOVIEBOX_SECRET_KEY_ALT", "")}\"")
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_DEFAULT", "\"${properties.getProperty("MOVIEBOX_SECRET_KEY_DEFAULT", "")}\"")
        buildConfigField("String", "OPENSUBTITLES_API", "\"https://opensubtitles-v3.strem.io\"")
        buildConfigField("String", "HUBCLOUD_API", "\"${properties.getProperty("HUBCLOUD_API", "")}\"")
        buildConfigField("String", "GDFLIX_API", "\"${properties.getProperty("GDFLIX_API", "")}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
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
    
    lint {
        targetSdk = 34
    }
}

cloudstream {
    // All of these properties are optional, you can safely remove them
    description = "Adimoviebox (moviebox.ph)" // Deskripsi yang diperbarui
    language = "en" // Bahasa dari Moviebox
    authors = listOf("AdimovieboxUser") // Ganti sesuai keinginan Anda

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // Anda mendukung semua tipe dari Moviebox
    tvTypes = listOf("Movie", "TvSeries", "Anime", "AsianDrama")

    iconUrl = "https://www.google.com/s2/favicons?domain=moviebox.ph&sz=%size%"

    isCrossPlatform = true
}

dependencies {
    // Cloudstream dependency
    implementation("com.lagradost:cloudstream3:3.11.0")

    // Dependensi yang dibutuhkan untuk parsing JSON
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    
    // Dependensi UI/Platform
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.browser:browser:1.9.0")
}

// Repository configuration
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://repo1.maven.org/maven2") }
}

// Perbaikan konfigurasi publishing
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.AdicinemaxNew"
                artifactId = "AdicinemaxNew"
                version = project.version.toString()
                
                from(components["release"])
            }
        }
    }
}
