// build.gradle.kts (Contoh Modifikasi)
version = 4

cloudstream {
    language = "en"
    
    // INI YANG PALING PENTING:
    packageName = "com.AdicinemaxNew" 

    authors = listOf("Hexated", "AdicinemaxCreator")
    status = 1 
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Anime",
        "AsianDrama",
    )
    iconUrl = "https://example.com/adicinemax_icon.png" 
}
