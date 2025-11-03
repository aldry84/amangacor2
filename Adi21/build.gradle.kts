// use an integer for version numbers
version = 4


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Adimoviebox (moviebox.ph)" // Deskripsi yang diperbarui
    language    = "en" // Bahasa dari Moviebox
    authors = listOf("Adi21") // Ganti sesuai keinginan Anda

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // Anda mendukung semua tipe dari Moviebox
    tvTypes = listOf("Movie","TvSeries","Anime","AsianDrama")

    iconUrl="https://www.google.com/s2/favicons?domain=moviebox.ph&sz=%size%"

    isCrossPlatform = true
}
