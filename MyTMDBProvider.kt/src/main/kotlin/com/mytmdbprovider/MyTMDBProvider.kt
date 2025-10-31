package mytmdbprovider 

import com.google.gson.Gson
// HAPUS: import mytmdbprovider.BuildConfig // TIDAK DIPERLUKAN KARENA KUNCI DISIMPAN LANGSUNG

import mytmdbprovider.VidsrcExtractor 
// PERBAIKAN IMPOR CLOUDSTREAM 3:
import com.lagradost.cloudstream3.metaproviders.MainDiscover
import com.lagradost.cloudstream3.APIHolder.Companion.app
import com.lagradost.cloudstream3.extractor.ExtractorLink
import com.lagradost.cloudstream3.model.* // Import semua model seperti LoadResponse, SearchResponse, HomePageList
import com.lagradost.cloudstream3.utils.AppUtils.tryParseInt 

class MyTMDBProvider : MainDiscover() {

    // --- Properti Dasar Source ---
    
    override val name = "My TMDB + Vidsrc" 
    override val mainUrl = "https://api.themoviedb.org"
    // ✅ KUNCI API disisipkan langsung, sesuai permintaan Anda.
    private val API_KEY = "1d8730d33fc13ccbd8cdaaadb74892c7" 
    
    // ... Sisanya tetap sama
    private val GSON = Gson()

    // Fungsi search, getMainPage, dan load sekarang akan me-resolve karena MainDiscover
    // sudah diimpor dengan benar.
    
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/3/search/multi?api_key=$API_KEY&query=$query"
        // PERBAIKAN: .get() adalah suspend function, tapi di Cloudstream, app.get().text sudah ok.
        val response = app.get(searchUrl).text 
        
        // 🚨 LOGIKA PARSING JSON TMDB di sini.
        return emptyList() 
    }

    override suspend fun getMainPage(page: Int): List<HomePageList> {
        val trendingUrl = "$mainUrl/3/trending/all/day?api_key=$API_KEY&page=$page"
        val response = app.get(trendingUrl).text
        
        // 🚨 LOGIKA PARSING JSON TMDB di sini.
        return emptyList() 
    }
    
    override suspend fun load(data: String): LoadResponse? { 
        val (mediaType, tmdbId) = data.split("/", limit = 2)
        val detailUrl = "$mainUrl/3/$mediaType/$tmdbId?api_key=$API_KEY"
        val response = app.get(detailUrl).text
        
        // 🚨 LOGIKA PARSING JSON TMDB di sini.
        return null 
    }

    override suspend fun loadLinks(
        data: String, 
        isSeries: Boolean
    ): List<ExtractorLink> {
        
        val extractor = VidsrcExtractor()
        val vidsrcUrl: String

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
