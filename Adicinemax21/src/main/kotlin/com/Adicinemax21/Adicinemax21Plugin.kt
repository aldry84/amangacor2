package com.Adicinemax21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Adicinemax21Plugin : Plugin() {
    override fun load(context: Context) {
        // Semua fungsionalitas Extractor Jeniusplay2 telah dihapus.
        // File ini hanya mendaftarkan API utama Adicinemax21.
        registerMainAPI(Adicinemax21())
        
        // PENTING: Jika Anda memindahkan Extractor StreamPlay yang asli ke dalam
        // proyek ini, pastikan semua Extractor API yang mereka andalkan (misalnya
        // HubCloud, MixDrop) juga sudah terdaftar, atau Extractor-nya
        // sudah ada di dalam Extractor API yang terpisah.
    }
}
