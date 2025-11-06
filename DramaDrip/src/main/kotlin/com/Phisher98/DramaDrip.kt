package com.Phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.getQualityFromName // Import untuk mengatasi Unresolved reference
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody.Companion.toRequestBody

// --- API Data Classes ---
data class ApiResponse(
    @JsonProperty("status") val status: Boolean? = null,
    @JsonProperty("msg") val msg: String? = null,
    @JsonProperty("return") val data: List<ApiContent>? = null
)

data class ApiContent(
    @JsonProperty("judul") val title: String? = null,
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("link") val link: String? = null, // Digunakan sebagai ID unik
    @JsonProperty("id_content") val contentId: String? = null,
    @JsonProperty("type_content") val contentType: String? = null // movie, tv
)

data class StreamLinkResponse(
    @JsonProperty("status") val status: Boolean? = null,
    @JsonProperty("msg") val msg: String? = null,
    @JsonProperty("return") val streamData: List<StreamLinkData>? = null
)

data class StreamLinkData(
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("link_raw") val linkRaw: String? = null,
    @JsonProperty("quality") val quality: String? = null,
    @JsonProperty("resolusi") val resolution: String? = null
)
// --- API Data Classes End ---

class DramaDrip : MainAPI() {
    override var mainUrl: String = runBlocking {
        DramaDripProvider.getDomains()?.dramadrip ?: "https://dramadrip.com"
    }
    override var name = "DramaDrip API"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.AsianDrama, TvType.TvSeries)
    private val cinemeta_url = "https://v3-cinemeta.strem.io/meta"

    // Definisikan Endpoint
    private val API_KLASIK_LIST = "/api/v1/klasik/list"
    private val API_KLASIK_QUERY = "/api/v1/klasik/query"
    private val API_KLASIK_LOAD = "/api/v1/klasik/load"

    override val mainPage = mainPageOf(
        API_KLASIK_LIST to "Semua Konten (Via API)"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val jsonPayload = mapOf(
            "auth" to "", 
            "query" to "semua", 
            "limit" to 50,
            "offset" to (page - 1) * 50
        ).toJson()

        val response = app.post(
            "$mainUrl${request.data}",
            requestBody = jsonPayload.toRequestBody(),
            headers = mapOf("Content-Type" to "application/json")
        ).parsedSafe<ApiResponse>()

        val home = response?.data?.mapNotNull { it.toSearchResult() } ?: emptyList()

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = home.isNotEmpty()
        )
    }

    private fun ApiContent.toSearchResult(): SearchResponse? {
        val link = this.link ?: return null 
        val title = this.title ?: return null
        val posterUrl = this.image
        val type = when (this.contentType?.lowercase()) {
            "movie" -> TvType.Movie
            "tv", "series" -> TvType.TvSeries
            else -> TvType.Movie
        }

        return newMovieSearchResponse(title, link, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val jsonPayload = mapOf(
            "auth" to "",
            "query" to query,
            "limit" to 50,
            "offset" to 0
        ).toJson()

        val response = app.post(
            "$mainUrl$API_KLASIK_QUERY",
            requestBody = jsonPayload.toRequestBody(),
            headers = mapOf("Content-Type" to "application/json")
        ).parsedSafe<ApiResponse>()

        return response?.data?.mapNotNull { it.toSearchResult() } ?: emptyList()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun load(url: String): LoadResponse {
        val contentId = url 
        
        val jsonPayload = mapOf(
            "auth" to "",
            "id_content" to contentId
        ).toJson()

        val response = app.post(
            "$mainUrl$API_KLASIK_LOAD",
            requestBody = jsonPayload.toRequestBody(),
            headers = mapOf("Content-Type" to "application/json")
        ).parsedSafe<ApiResponse>()
        
        val contentDetail = response?.data?.firstOrNull() ?: throw Exception("API did not return content detail.")

        val title = contentDetail.title ?: "Judul Tidak Diketahui"
        val poster = contentDetail.image
        val type = when (contentDetail.contentType?.lowercase()) {
            "movie" -> TvType.Movie
            "tv", "series" -> TvType.TvSeries
            else -> TvType.Movie
        }

        if (type == TvType.TvSeries) {
            val episodes = listOf(
                newEpisode(contentId.toJson()) {
                    this.name = "Konten Series"
                    this.season = 1 
                    this.episode = 1
                }
            )

            return newTvSeriesLoadResponse(title, url, type, episodes) {
                this.posterUrl = poster
            }
        } else {
            val linkForLoadLinks = contentId.toJson()
            return newMovieLoadResponse(title, url, type, linkForLoadLinks) {
                this.posterUrl = poster
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val contentId = tryParseJson<String>(data) ?: return false

        val jsonPayload = mapOf(
            "auth" to "",
            "id_content" to contentId
        ).toJson()

        val response = app.post(
            "$mainUrl$API_KLASIK_LOAD",
            requestBody = jsonPayload.toRequestBody(),
            headers = mapOf("Content-Type" to "application/json")
        ).parsedSafe<StreamLinkResponse>()

        val streamLinks = response?.streamData ?: emptyList()

        if (streamLinks.isEmpty()) {
            Log.e("LoadLinks", "API returned no stream links for ID: $contentId")
            return false
        }

        for (stream in streamLinks) {
            val link = stream.link ?: stream.linkRaw ?: continue
            val qualityText = stream.quality ?: stream.resolution ?: "Unknown"
            
            // Perbaikan Unresolved reference 'getStringQuality'
            val qualityInt = getQualityFromName(qualityText)

            callback(
                // Perbaikan Invalid parameter 'referer' dan 'quality'
                newExtractorLink(
                    source = "DramaDrip API",
                    name = "DramaDrip API $qualityText",
                    url = link,
                ) {
                    this.quality = qualityInt
                    this.referer = "$mainUrl/" 
                }
            )
        }

        return true
    }
}
