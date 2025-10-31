package mytmdbprovider 

import com.google.gson.Gson
// HAPUS: import mytmdbprovider.BuildConfig // Import ini tidak diperlukan lagi
import mytmdbprovider.VidsrcExtractor 
import dev.shehand.cloudstream.MainApplication.Companion.app
import dev.shehand.cloudstream.extractor.ExtractorLink
import dev.shehand.cloudstream.source.MainDiscover
import dev.shehand.cloudstream.source.data.LoadData
import dev.shehand.cloudstream.source.data.SearchResponse
import dev.shehand.cloudstream.source.data.HomePageList
import dev.shehand.cloudstream.utils.AppUtils.tryParseInt

class MyTMDBProvider : MainDiscover() {

    // --- Properti Dasar Source ---
    
    override val name = "My TMDB + Vidsrc" 
    override val mainUrl = "https://api.themoviedb.org"
    
    // ✅ KUNCI API DISIMPAN LANGSUNG DI SINI (Dalam Teks Biasa)
    private val API_KEY = "1d8730d33fc13ccbd8cdaaadb74892c7" 
    
    // --- Variabel Pembantu ---
    private val GSON = Gson()

    // --- Implementasi Fungsi (Sisanya tetap sama) ---

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/3/search/multi?api_key=$API_KEY&query=$query"
        val response = app.get(searchUrl).text
        
        // ... Logika parsing ...
        return emptyList() 
    }

    override suspend fun getMainPage(page: Int): List<HomePageList> {
        val trendingUrl = "$mainUrl/3/trending/all/day?api_key=$API_KEY&page=$page"
        val response = app.get(trendingUrl).text
        
        // ... Logika parsing ...
        return emptyList() 
    }
    
    override suspend fun load(data: String): LoadData? {
        val (mediaType, tmdbId) = data.split("/", limit = 2)
        val detailUrl = "$mainUrl/3/$mediaType/$tmdbId?api_key=$API_KEY"
        val response = app.get(detailUrl).text
        
        // ... Logika parsing ...
        return null 
    }

    override suspend fun loadLinks(
        data: String, 
        isSeries: Boolean
    ): List<ExtractorLink> {
        
        val extractor = VidsrcExtractor()
        val vidsrcUrl: String

        // ... Logika Load Links (membuat URL Vidsrc) ...

        if (isSeries) {
            val parts = data.split("_")
            if (parts.size != 3) return emptyList()
            
            val tmdbId = parts[0]
            val season = parts[1].tryParseInt() 
            val episode = parts[2].tryParseInt()
            
            vidsrcUrl = "https://vidsrc.me/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode"
            
        } else {
            vidsrcUrl = "https://vidsrc.me/embed/movie?tmdb=$data"
        }
        
        return extractor.getLink(vidsrcUrl)
    }
}
