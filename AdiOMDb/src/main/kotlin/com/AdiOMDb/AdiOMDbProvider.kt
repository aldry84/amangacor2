package com.AdiOMDb

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin 
import com.lagradost.cloudstream3.plugins.Plugin // Class dasar yang digunakan untuk pendaftaran

@CloudstreamPlugin
class AdiOMDbProvider: Plugin() { 
    // Pastikan Anda menggunakan fungsi 'registerMainAPI' di dalam 'load'
    override fun load() { 
        // Logika sederhana untuk mendaftarkan API utama Anda
        registerMainAPI(AdiOMDb())
    }
}
