// use an integer for version numbers
version = 4

cloudstream {
    language = "en"
    
    // PERBAIKAN: Menggunakan set() untuk properti yang mungkin tidak dikenal secara langsung oleh DSL.
    set("packageName", "com.AdicinemaxNew") 

    description = "Metadata dan Streaming dari Adicinemax" 
    authors = listOf("Hexated", "AdicinemaxCreator") 

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 
    
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Anime",
        "AsianDrama",
    )

    // URL Icon sementara
    iconUrl = "https://example.com/adicinemax_icon.png" 
}
