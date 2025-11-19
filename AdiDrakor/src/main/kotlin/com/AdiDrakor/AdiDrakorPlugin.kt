package com.AdiDrakor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
// Kami menggunakan referensi ke Jeniusplay2 dari package aslinya untuk menghemat kode, 
// atau Anda bisa menyalinnya jika ingin independen.
import com.Adicinemax21.Jeniusplay2 

@CloudstreamPlugin
class AdiDrakorPlugin : Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan API Utama AdiDrakor
        registerMainAPI(AdiDrakor())
        
        // Mendaftarkan Extractor tambahan (Opsional jika sudah ada di Adicinemax21, tapi bagus untuk redundansi)
        registerExtractorAPI(Jeniusplay2())
    }
}
