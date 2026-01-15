// use an integer for version numbers
version = 1

cloudstream {
    language = "id"
    // All of these properties are optional, you can safely remove them

     description = "#1 AdiManu extention based on MultiAPI"
     authors = listOf("AldryManu")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries",
        "Anime",
        "Movie",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=homespecially.com&sz=128"
}
