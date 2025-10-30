// Adicinema/build.gradle.kts

version = 6 // Naikkan jika update plugin

cloudstream {
    description = "Hybrid OMDb metadata and FMovies streaming provider"
    language = "en"
    authors = listOf("Adicinema") // Diubah dari AdiOMDb

    /**
     * Status:
     * 0 = Down
     * 1 = Working
     * 2 = Slow
     * 3 = Beta/Test only
     */
    status = 1

    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime",
        "AsianDrama"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=omdbapi.com&sz=%size%"
    isCrossPlatform = true
}
