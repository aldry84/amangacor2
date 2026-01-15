// Gunakan angka integer untuk versi (mulai dari 1 untuk rilis baru)
version = 3

cloudstream {
    // Bahasa konten utama
    language = "id"
    
    // Deskripsi singkat (diambil dari meta title website)
    description = "Situs Nonton Film Sub Indo Streaming Movie Online"
    
    // Nama penulis (kamu)
    authors = listOf("aldry84")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 

    // Tipe konten yang didukung provider ini
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries",
        "Movie"
    )

    // Icon URL diambil langsung dari server JuraganFilm (berdasarkan log curl kamu)
    iconUrl = "https://tv41.juragan.film/wp-content/uploads/2019/05/faviconjf.png"

    // Mendukung Android & TV
    isCrossPlatform = false
}
