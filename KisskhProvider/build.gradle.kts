import org.jetbrains.kotlin.konan.properties.Properties

// Gunakan integer untuk nomor versi
version = 1

android {
    defaultConfig {
        // Inisialisasi properti untuk membaca local.properties
        val properties = Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        
        // Hanya memuat jika file local.properties ada (mencegah error di GitHub Actions)
        if (propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
        }

        // Aktifkan fitur BuildConfig agar bisa diakses di kode Kotlin
        android.buildFeatures.buildConfig = true

        // URL Default jika local.properties tidak ditemukan
        val kissKhUrl = properties.getProperty("KissKh") 
            ?: "https://script.google.com/macros/s/AKfycbzn8B31PuDxzaMa9_CQ0VGEDasFqfzI5bXvjaIZH4DM8DNq9q6xj1ALvZNz_JT3jF0suA/exec?id="
        val kissKhSubUrl = properties.getProperty("KisskhSub") 
            ?: "https://script.google.com/macros/s/AKfycbyq6hTj0ZhlinYC6xbggtgo166tp6XaDKBCGtnYk8uOfYBUFwwxBui0sGXiu_zIFmA/exec?id="

        // Daftarkan variabel ke BuildConfig (Pastikan nama variabel sesuai dengan di file .kt)
        buildConfigField("String", "KissKh", "\"$kissKhUrl\"")
        buildConfigField("String", "KisskhSub", "\"$kissKhSubUrl\"")
    }
}

cloudstream {
    // Metadata untuk Extension
    language = "id"
    authors = listOf("aldry84")
    [span_1](start_span)status = 1 // 1 berarti status extension "Ok"[span_1](end_span)
    [span_2](start_span)tvTypes = listOf("AsianDrama", "TvSeries", "Anime", "Movie")[span_2](end_span)
    
    [span_3](start_span)// Ikon provider[span_3](end_span)
    iconUrl = "https://www.google.com/s2/favicons?domain=kisskh.co&sz=%size%"
    
    // Mendukung multi-platform
    isCrossPlatform = true
}
