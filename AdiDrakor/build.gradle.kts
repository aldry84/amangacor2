// AdiDrakor/build.gradle.kts

// use an integer for version numbers
version = 1

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "AdiDrakor (Khusus Drama Korea dari moviebox.ph)" // Deskripsi yang diperbarui untuk Drakor
    language    = "en" // Bahasa dari Moviebox
    authors = listOf("AdiDrakorUser") // Ganti sesuai keinginan Anda

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // Fokuskan hanya pada tipe yang didukung untuk Drakor
    tvTypes = listOf("TvSeries", "AsianDrama")

    // Ganti nama paket untuk iconURL
    iconUrl="https://www.google.com/s2/favicons?domain=moviebox.ph&sz=%size%"

    isCrossPlatform = true
}
