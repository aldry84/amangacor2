apply plugin: 'com.android.library'
apply plugin: 'kotlin-android'
apply plugin: 'kotlin-kapt'
apply plugin: 'com.lagradost.cloudstream3'

android {
    compileSdkVersion 34
    defaultConfig {
        minSdkVersion 21
        targetSdkVersion 34
        versionCode 1
        versionName "1.0.0" 
    }
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    namespace 'com.AdiOMDb'
}

cloudstream {
    // ID Unik Plugin Anda
    url = 'AdiOMDb.com' 
    // Nama Provider yang akan ditampilkan di Cloudstream3
    desc = 'Menggabungkan Metadata OMDb dan Streaming Fmoviesunblocked'
    authors = listOf("Adi")
}

dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])
    implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlin_version"
    implementation "androidx.core:core-ktx:1.12.0"
    implementation "androidx.appcompat:appcompat:1.6.1"
    implementation project(':lib:cloudstream-impl')
}
