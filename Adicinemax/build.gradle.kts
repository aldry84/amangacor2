// use an integer for version numbers
version = 4

// Catatan: Jika Anda ingin mempertahankan nama "com.Adicinemax" dari kode Kotlin, 
// pastikan Anda tidak menetapkan 'packageName' di sini kecuali Anda ingin mengubahnya.

cloudstream {
    language = "en"
    
    // Deskripsi yang lebih relevan untuk Adicinemax (Opsional)
    description = "Metadata dan Streaming dari Adicinemax" 
    
    authors = listOf("Hexated", "AdicinemaxCreator") // Anda bisa menambahkan nama Anda

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 
    
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Anime",
        "AsianDrama",
    )

    // PERUBAHAN: Icon URL diganti, karena URL lama (moviebox.ph) tidak lagi digunakan sebagai mainUrl.
    // Jika Anda ingin menggunakan ikon TMDb, Anda harus menemukan URL ikon yang sesuai,
    // atau menggunakan ikon placeholder/default.
    // Untuk saat ini, saya akan menggunakan URL placeholder.
    iconUrl = "https://example.com/adicinemax_icon.png" 
}
