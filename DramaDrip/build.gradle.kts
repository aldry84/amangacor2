// use an integer for version numbers
version = 1

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "DramaDrip - Integrasi API" // Deskripsi yang diperbarui
    language    = "id" // Menggunakan 'id' karena API tampaknya berbahasa Indonesia
    authors = listOf("Phisher98") // Ganti sesuai dengan nama package Anda

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    tvTypes = listOf("Movie","TvSeries","AsianDrama") // Disesuaikan dengan DramaDrip.kt

    // Mengubah iconUrl ke domain Dramadrip jika tersedia
    iconUrl="https://www.google.com/s2/favicons?domain=dramadrip.com&sz=%size%"

    isCrossPlatform = true
}
