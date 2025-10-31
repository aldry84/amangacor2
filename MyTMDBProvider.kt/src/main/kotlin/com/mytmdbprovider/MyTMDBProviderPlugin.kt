package mytmdbprovider 

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.APIHolder.Companion.registerMainAPI // Mengganti registerMainPageSource
import android.content.Context // Diperlukan untuk fungsi load()

// Kelas ini adalah TITIK MASUK (entry point) ekstensi Anda
class MyTMDBProviderPlugin: CloudstreamPlugin() { 
    // Fungsi load() Cloudstream memerlukan parameter context
    override fun load(context: Context) { 
        // Daftarkan Source utama Anda
        registerMainAPI(MyTMDBProvider()) 
    }
}
