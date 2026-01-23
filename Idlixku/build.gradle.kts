// use an integer for version numbers
version = 6

cloudstream {
    // Informasi Plugin
    name = "Idlixku" // Nama plugin wajib ada
    description = "Nonton Film, Serial, Drama Korea dan Anime Subtitle Indonesia"
    language = "id"
    
    authors = listOf("aldry84")

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
        "Anime" // Tambahan Anime agar sesuai fitur
    )

    // PERBAIKAN: Tanda kutip dua (") ditambahkan di sini
    iconUrl = "https://tv12.idlixku.com/wp-content/uploads/2020/06/idlix.png"

    isCrossPlatform = true
}
