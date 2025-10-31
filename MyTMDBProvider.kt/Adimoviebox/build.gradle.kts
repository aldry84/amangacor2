// cloudstream-config.gradle.kts
// File ini hanya berisi konfigurasi CloudStream metadata

// use an integer for version numbers
version = 1 // ✅ Ditambahkan sesuai permintaan

cloudstream {
    // Properti Metadata Ekstensi (Menggunakan nama Anda)
    description = "TMDB & Vidsrc Integrator" 
    language    = "en" 
    authors = listOf("MyTMDBProviderAuthor") 

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // Status Ok

    // List of video source types.
    tvTypes = listOf("Movie","TvSeries","Anime","AsianDrama")

    // Menggunakan ikon TMDB karena ini adalah sumber metadata
    iconUrl="https://www.google.com/s2/favicons?domain=themoviedb.org&sz=%size%"

    isCrossPlatform = true
}
