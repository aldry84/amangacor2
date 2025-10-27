// use an integer for version numbers
version = 1


cloudstream {
    description = "Adimoviemaze"
    language    = "en"
    authors = listOf("AdimoviemazeUser")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 

    tvTypes = listOf("Movie","TvSeries")

    // Menggunakan favicon default dari domain
    iconUrl="https://www.google.com/s2/favicons?domain=moviemaze.cc&sz=%size%"

    isCrossPlatform = true
}
