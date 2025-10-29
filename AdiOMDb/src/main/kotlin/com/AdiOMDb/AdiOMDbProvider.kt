package com.AdiOMDb

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin 
import android.content.Context // <-- Baris ini harus ada

@CloudstreamPlugin
class AdiOMDbProvider: Plugin() { 
    // Jika 'override fun load()' gagal, coba load dengan Context sebagai fallback untuk versi lama:
    /*
    override fun load(context: Context) { 
        registerMainAPI(AdiOMDb())
    }
    */
    
    // Namun, yang paling stabil saat ini di Cloudstream 4.x adalah:
    override fun load() { 
        registerMainAPI(AdiOMDb())
    }
}
