package mytmdbprovider // 🚨 PACKAGE BARU: mytmdbprovider

import com.google.gson.Gson
import mytmdbprovider.BuildConfig // Ganti impor BuildConfig
import mytmdbprovider.VidsrcExtractor // Ganti impor VidsrcExtractor
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
    private val API_KEY = BuildConfig.TMDB_API_KEY 

    // --- Variabel Pembantu ---
    private val GSON = Gson()

    // --- Implementasi Fungsi (Sama seperti sebelumnya) ---

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/3/search/multi?api_key=$API_KEY&query=$query"
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
    
    override suspend fun load(data: String): LoadData? {
        val (mediaType, tmdbId) = data.split("/", limit = 2)
        val detailUrl = "$mainUrl/3/$mediaType/$tmdbId?api_key=$API_KEY"
        val response = app.get(detailUrl).text
        
        // 🚨 LOGIKA PARSING JSON TMDB di sini (Detail Media)
        println("TMDB Detail JSON Response: $response")
        return null 
    }

    // --- Fungsi Load Links (JEMBATAN KOLABORASI) ---

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
        
        println("Memanggil Vidsrc Extractor untuk URL: $vidsrcUrl")
        
        return extractor.getLink(vidsrcUrl)
    }
}
