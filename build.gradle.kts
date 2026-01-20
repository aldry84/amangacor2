import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        // AGP Versi Stabil
        classpath("com.android.tools.build:gradle:8.2.2")
        // Cloudstream Plugin
        classpath("com.github.recloudstream:gradle:master-SNAPSHOT")
        // UPDATE PENTING: Menggunakan Kotlin 2.1.0 untuk mengatasi error metadata
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // Ganti URL repo ini sesuai repo GitHub kamu yang sebenarnya
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/aldry84/amangacor2")
        authors = listOf("PartnerCoding") 
    }

    android {
        // Namespace sementara (akan ditimpa oleh plugin masing-masing)
        namespace = "com.phisher98" 

        defaultConfig {
            minSdk = 21
            compileSdkVersion(34) // Android 14
            targetSdk = 34
        }

        // WAJIB JAVA 11 (Standard Cloudstream Terbaru)
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                // WAJIB JVM 11
                jvmTarget.set(JvmTarget.JVM_11)
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions"
                )
            }
        }
    }

    dependencies {
        val implementation by configurations
        val cloudstream by configurations
        
        // Library Cloudstream (Pre-release)
        cloudstream("com.lagradost:cloudstream3:pre-release")

        // Dependencies Standar
        implementation(kotlin("stdlib"))
        implementation("com.github.Blatzar:NiceHttp:0.4.13")
        implementation("org.jsoup:jsoup:1.17.2") 
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
        implementation("org.mozilla:rhino:1.7.14")
        implementation("me.xdrop:fuzzywuzzy:1.4.0")
        
        // Library tambahan
        implementation("com.google.code.gson:gson:2.10.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
        implementation("app.cash.quickjs:quickjs-android:0.9.2")
        implementation("com.github.vidstige:jadb:v1.2.1")
        implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
