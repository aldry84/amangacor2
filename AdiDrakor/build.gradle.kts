// AdiDrakor/build.gradle.kts

plugins {
    kotlin("jvm")
    id("com.lagradost.cloudstream3.gradleplugin") version "1.0.0"
}

// Gunakan angka untuk versi plugin
version = 1

cloudstream {
    // Semua properti ini opsional

    description = "AdiDrakor (Khusus Drama Korea dari moviebox.ph)"
    language = "en"
    authors = listOf("AdiDrakorUser")

    /**
     * Status:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     */
    status = 1

    // Jenis tayangan yang didukung
    tvTypes = listOf("TvSeries", "AsianDrama")

    // URL ikon ekstensi
    iconUrl = "https://www.google.com/s2/favicons?domain=moviebox.ph&sz=%size%"

    // Dapat digunakan lintas platform (PC, Android, dll)
    isCrossPlatform = true
}
