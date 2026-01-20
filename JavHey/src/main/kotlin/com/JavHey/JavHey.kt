import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class JavHeyProvider : MainAPI() {
    override var mainUrl = "https://javhey.com"
    override var name = "JavHey"
    override val hasMainPage = true
    override var lang = "id" // Karena situsnya Subtitle Indonesia
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    // Mengatur Kategori Halaman Utama
    override val mainPage = mainPageOf(
        "$mainUrl/videos/paling-baru/page=" to "Paling Baru",
        "$mainUrl/videos/paling-dilihat/page=" to "Paling Dilihat",
        "$mainUrl/videos/top-rating/page=" to "Top Rating",
        "$mainUrl/videos/jav-sub-indo/page=" to "JAV Sub Indo"
    )

    // ==========================================
    // BAGIAN 1: MENGAMBIL DAFTAR VIDEO
    // ==========================================
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse? {
        // Membentuk URL halaman (misal: .../page=1)
        val url = request.data + page
        val doc = app.get(url).document
        
        val home = doc.select("article.item").mapNotNull {
            toSearchResult(it)
        }

        return newHomePageResponse(request.name, home)
    }

    // Fungsi pembantu untuk mengubah HTML menjadi Data Video Cloudstream
    private fun toSearchResult(element: Element): SearchResponse? {
        // Mengambil Judul
        val title = element.selectFirst("h3 > a")?.text()?.trim() 
            ?: element.selectFirst("img")?.attr("alt") 
            ?: return null

        // Mengambil Link Halaman Detail
        val href = element.selectFirst("div.item_header > a")?.attr("href") ?: return null
        
        // Mengambil Gambar Poster
        val posterUrl = element.selectFirst("img")?.attr("src")

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    // ==========================================
    // BAGIAN 2: PENCARIAN (SEARCH)
    // ==========================================
    override suspend fun search(query: String): List<SearchResponse> {
        // URL Search: https://javhey.com/search?s=kata-kunci
        val url = "$mainUrl/search?s=$query"
        val doc = app.get(url).document
        
        return doc.select("article.item").mapNotNull {
            toSearchResult(it)
        }
    }

    // ==========================================
    // BAGIAN 3: MEMUAT DETAIL VIDEO
    // ==========================================
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        // Mengambil info detail lagi untuk memastikan akurasi
        val title = doc.selectFirst("h1")?.text()?.trim() ?: "Unknown Title"
        val poster = doc.selectFirst("div.video_player img")?.attr("src") 
            ?: doc.selectFirst("article.item img")?.attr("src")
        
        // Mengambil Deskripsi (jika ada)
        val description = doc.select("div.video-description").text()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    // ==========================================
    // BAGIAN 4: LOAD LINKS (Logika Bysebuho)
    // ==========================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        
        // 1. Cari iframe embed (Bysebuho)
        // Biasanya ada di dalam div.video_player atau langsung iframe
        val iframeSrc = doc.select("iframe").attr("src")
        
        if (iframeSrc.contains("bysebuho")) {
            invokeBysebuho(iframeSrc, data, callback)
            return true
        }
        
        return false
    }

    // Fungsi khusus menangani API Bysebuho yang kamu temukan tadi
    private suspend fun invokeBysebuho(iframeUrl: String, referer: String, callback: (ExtractorLink) -> Unit) {
        // Ubah URL embed: https://bysebuho.com/e/CODE/judul -> jadi CODE
        val code = iframeUrl.substringAfter("/e/").substringBefore("/")
        
        // Panggil API JSON yang kamu temukan di cURL
        val apiUrl = "https://bysebuho.com/api/videos/$code/embed/details"
        
        val headers = mapOf(
            "Referer" to iframeUrl,
            "x-embed-origin" to mainUrl,
            "x-embed-parent" to iframeUrl,
            "x-embed-referer" to mainUrl
        )

        try {
            val jsonText = app.get(apiUrl, headers = headers).text
            val json = parseJson<BysebuhoResponse>(jsonText)

            // Link rahasia ada di 'embed_frame_url' (biasanya domain 9n8o.com)
            val nextUrl = json.embed_frame_url
            
            if (!nextUrl.isNullOrEmpty()) {
                // Cloudstream punya ekstraktor bawaan yang canggih,
                // kita suruh dia ekstrak link dari 9n8o.com ini.
                loadExtractor(nextUrl, callback)
            }
        } catch (e: Exception) {
            // Error handling ringan
            e.printStackTrace()
        }
    }

    // Data Class untuk parsing JSON Bysebuho
    data class BysebuhoResponse(
        val id: Int? = null,
        val code: String? = null,
        val title: String? = null,
        val embed_frame_url: String? = null
    )
}
