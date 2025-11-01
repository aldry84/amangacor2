// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Kdramas and Movies"
    authors = listOf("AdiDewasa")

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
        "Movie",
    )

    requiresResources = true
    language = "en"

    iconUrl = "https://www.google.com/s2/favicons?domain=dramafull.cc&sz=%size%"

    isCrossPlatform = true
}
