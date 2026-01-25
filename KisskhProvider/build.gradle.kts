import org.jetbrains.kotlin.konan.properties.Properties

version = 1

android {
    defaultConfig {
        [span_0](start_span)// Inisialisasi properti untuk membaca file local.properties[span_0](end_span)
        val properties = Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        
        [span_1](start_span)// Load jika file ada di lokal[span_1](end_span)
        if (propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
        }

        [span_2](start_span)// Aktifkan fitur BuildConfig[span_2](end_span)
        android.buildFeatures.buildConfig = true

        // URL Default (Fallback) jika local.properties tidak ditemukan atau kosong
        val kissKhUrl = properties.getProperty("KissKh") 
            ?: "https://script.google.com/macros/s/AKfycbzn8B31PuDxzaMa9_CQ0VGEDasFqfzI5bXvjaIZH4DM8DNq9q6xj1ALvZNz_JT3jF0suA/exec?id="
        val kissKhSubUrl = properties.getProperty("KisskhSub") 
            ?: "https://script.google.com/macros/s/AKfycbyq6hTj0ZhlinYC6xbggtgo166tp6XaDKBCGtnYk8uOfYBUFwwxBui0sGXiu_zIFmA/exec?id="

        [span_3](start_span)// Daftarkan variabel ke dalam BuildConfig[span_3](end_span)
        buildConfigField("String", "KissKh", "\"$kissKhUrl\"")
        buildConfigField("String", "KisskhSub", "\"$kissKhSubUrl\"")
    }
}

cloudstream {
    [span_4](start_span)// Metadata extension[span_4](end_span)
    language = "id"
    authors = listOf("aldry84")
    status = 1
    [span_5](start_span)tvTypes = listOf("AsianDrama", "TvSeries", "Anime", "Movie")[span_5](end_span)
    isCrossPlatform = true
}
