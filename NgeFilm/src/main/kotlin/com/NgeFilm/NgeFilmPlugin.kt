package com.NgeFilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class NgeFilmPlugin: Plugin() {
    override fun load(context: Context) {
        // 1. Daftarkan Provider (Wajib)
        registerMainAPI(NgeFilm())

        // 2. Daftarkan Extractor (Opsional, tapi Good Practice)
        // Ini berguna jika nanti kamu mau menghapus logika "if" manual di NgeFilm.kt
        registerExtractorAPI(RpmLive())
    }
}
