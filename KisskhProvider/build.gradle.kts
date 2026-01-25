import org.jetbrains.kotlin.konan.properties.Properties

version = 1

android {
    defaultConfig {
        val properties = Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        
        if (propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
        }

        android.buildFeatures.buildConfig = true

        val kissKhUrl = properties.getProperty("KissKh") 
            ?: "https://script.google.com/macros/s/AKfycbzn8B31PuDxzaMa9_CQ0VGEDasFqfzI5bXvjaIZH4DM8DNq9q6xj1ALvZNz_JT3jF0suA/exec?id="
        val kissKhSubUrl = properties.getProperty("KisskhSub") 
            ?: "https://script.google.com/macros/s/AKfycbyq6hTj0ZhlinYC6xbggtgo166tp6XaDKBCGtnYk8uOfYBUFwwxBui0sGXiu_zIFmA/exec?id="

        buildConfigField("String", "KISSKH_API", "\"$kissKhUrl\"")
        buildConfigField("String", "KISSKH_SUB", "\"$kissKhSubUrl\"")
    }
}

cloudstream {
    language = "id"
    authors = listOf("aldry84")
    status = 1
    tvTypes = listOf("AsianDrama", "TvSeries", "Anime", "Movie")
    isCrossPlatform = true
    
    // Icon telah ditambahkan di sini
    iconUrl = "https://www.google.com/s2/favicons?domain=kisskh.ovh&sz=%size%"
}
