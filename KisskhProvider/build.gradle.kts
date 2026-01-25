android {
    defaultConfig {
        // Inisialisasi properti
        val properties = org.jetbrains.kotlin.konan.properties.Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        
        // Load jika file ada
        if (propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
        }

        android.buildFeatures.buildConfig = true

        // URL Default jika local.properties kosong
        val kissKhUrl = properties.getProperty("KissKh") 
            ?: "https://script.google.com/macros/s/AKfycbzn8B31PuDxzaMa9_CQ0VGEDasFqfzI5bXvjaIZH4DM8DNq9q6xj1ALvZNz_JT3jF0suA/exec?id="
        val kissKhSubUrl = properties.getProperty("KisskhSub") 
            ?: "https://script.google.com/macros/s/AKfycbyq6hTj0ZhlinYC6xbggtgo166tp6XaDKBCGtnYk8uOfYBUFwwxBui0sGXiu_zIFmA/exec?id="

        buildConfigField("String", "KissKh", "\"$kissKhUrl\"")
        buildConfigField("String", "KisskhSub", "\"$kissKhSubUrl\"")
    }
}

cloudstream {
    [span_1](start_span)// Metadata extension[span_1](end_span)
    authors = listOf("Phisher98", "Hexated", "Peerless")
    status = 1
    tvTypes = listOf("AsianDrama", "TvSeries", "Anime", "Movie")
}
