package mytmdbprovider 

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.APIHolder.Companion.registerMainAPI
import android.content.Context 

// Kelas ini adalah TITIK MASUK (entry point) ekstensi Anda
class MyTMDBProviderPlugin: CloudstreamPlugin() { 
    override fun load(context: Context) { 
        registerMainAPI(MyTMDBProvider()) 
    }
}
