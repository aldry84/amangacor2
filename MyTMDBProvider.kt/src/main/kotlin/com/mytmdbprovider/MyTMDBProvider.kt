package mytmdbprovider 

import com.google.gson.Gson
import mytmdbprovider.BuildConfig 
import mytmdbprovider.VidsrcExtractor 
// Import CLOUDSTREAM 3 yang benar:
import com.lagradost.cloudstream3.metaproviders.MainDiscover
import com.lagradost.cloudstream3.APIHolder.Companion.app
import com.lagradost.cloudstream3.extractor.ExtractorLink
import com.lagradost.cloudstream3.model.LoadResponse
import com.lagradost.cloudstream3.model.SearchResponse
import com.lagradost.cloudstream3.model.HomePageList
import com.lagradost.cloudstream3.utils.AppUtils.tryParseInt 

class MyTMDBProvider : MainDiscover() {

    // --- Properti Dasar Source ---
    
    override val name = "My TMDB + Vidsrc" 
    override val mainUrl = "https://api.themoviedb.org"
    // Kunci API disisipkan langsung, sesuai permintaan Anda
    private val API_KEY = "1d8730d33fc13ccbd8cdaaadb74892c7" 
    
    // --- Variabel Pembantu ---
    private val GSON = Gson()

    // --- Implementasi Fungsi ---

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/3/search/multi?api_key=$API_KEY&query=$query"
        // Menggunakan app.get() yang sudah di-resolve
        val response = app.get(searchUrl).text 
        
        // 🚨 LOGIKA PARSING JSON TMDB di sini.
        println("TMDB Search JSON Response: $response")
        return emptyList() 
    }

    override suspend fun getMainPage(page: Int): List<HomePageList> {
        val trendingUrl = "$mainUrl/3/trending/all/day?api_key=$API_KEY&page=$page"
        val response = app.get(trendingUrl).text
        
        // 🚨 LOGIKA PARSING JSON TMDB di sini.
        println("TMDB Trending JSON Response: $response")
        return emptyList() 
    }
    
    override suspend fun load(data: String): LoadResponse? { // Menggunakan LoadResponse yang benar
        val (mediaType, tmdbId) = data.split("/", limit = 2)
        val detailUrl = "$mainUrl/3/$mediaType/$tmdbId?api_key=$API_KEY"
        val response = app.get(detailUrl).text
        
        // 🚨 LOGIKA PARSING JSON TMDB di sini
        println("TMDB Detail JSON Response: $response")
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
            val season = parts[1].tryParseInt() // tryParseInt sudah di-resolve
            val episode = parts[2].tryParseInt()
            
            vidsrcUrl = "https://vidsrc.me/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode"
            
        } else {
            vidsrcUrl = "https://vidsrc.me/embed/movie?tmdb=$data"
        }
        
        return extractor.getLink(vidsrcUrl)
    }
}
