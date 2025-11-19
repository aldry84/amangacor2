// =======================================================
// BAGIAN INI UNTUK MEMPERBAIKI ERROR KOMPILASI ANDA
// =======================================================

pluginManagement {
    repositories {
        // Repositori tempat plugin build CloudStream disimpan
        maven("https://jitpack.io")
        
        // Repositori standar Gradle
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            // INI PERBAIKANNYA: 
            // Memberi tahu Gradle bahwa "com.github.recloudstream.gradle"
            // sebenarnya adalah "cs-gradle"
            if (requested.id.id == "com.github.recloudstream.gradle") {
                useModule("com.github.recloudstream:cs-gradle:${requested.version}")
            }
        }
    }
}

plugins {
    // Menggunakan versi stabil BUKAN "master-SNAPSHOT"
    id("com.github.recloudstream.gradle") version "1.2.2" apply false
}


// =======================================================
// KODE ASLI ANDA DIMULAI DARI SINI (INI SUDAH BENAR)
// =======================================================

rootProject.name = "CloudstreamPlugins"

// This file sets what projects are included. All new projects should get automatically included unless specified in "disabled" variable.
val disabled = listOf<String>()

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}

// To only include a single project, comment out the previous lines (except the first one), and include your plugin like so:
// include("PluginName")
