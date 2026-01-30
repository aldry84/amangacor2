package com.Adimoviebox

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://lok-lok.cc"
    private val apiUrl = "https://lok-lok.cc" 
    private val homeApiUrl = "https://h5-api.aoneroom.com"

    override val instantLinkLoading = true
    override var name = "Adimoviebox"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    // KUNCI RAHASIA UNTUK SIGNATURE (Diambil dari MovieBoxProvider)
    private val secretKeyDefault = "NzZpUmwwN3MweFNOOWpxbUVXQXQ3OUVCSlp1bElRSXNWNjRGWnIyTw=="

    // HEADER DASAR
    private val commonHeaders = mapOf(
        "origin" to mainUrl,
        "referer" to "$mainUrl/",
        "accept" to "application/json",
        "x-client-info" to "{\"package_name\":\"com.adimoviebox.id\",\"version_name\":\"3.0.0\",\"os\":\"android\",\"timezone\":\"Asia/Jakarta\"}",
        "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    // --- FUNGSI KEAMANAN (SIGNATURE LOGIC) ---

    private fun md5(input: ByteArray): String {
        return MessageDigest.getInstance("MD5").digest(input)
            .joinToString("") { "%02x".format(it) }
    }

    private fun generateXClientToken(): String {
        val timestamp = System.currentTimeMillis().toString()
        val hash = md5(timestamp.reversed().toByteArray())
        return "$timestamp,$hash"
    }

    private fun generateXTrSignature(
        method: String,
        url: String,
        body: String? = null
    ): String {
        val timestamp = System.currentTimeMillis()
        val parsed = Uri.parse(url)
        val path = parsed.path ?: ""
        
        // Mengurutkan query parameters untuk validitas signature
        val query = if (parsed.queryParameterNames.isNotEmpty()) {
            parsed.queryParameterNames.sorted().joinToString("&") { key ->
                parsed.getQueryParameters(key).joinToString("&") { value -> "$key=$value" }
            }
        } else ""
        
        val canonicalUrl = if (query.isNotEmpty()) "$path?$query" else path
        val bodyHash = if (body != null) md5(body.toByteArray()) else ""
        val bodyLength = body?.toByteArray()?.size?.toString() ?: ""

        val canonical = "${method.uppercase()}\napplication/json\napplication/json\n$bodyLength\n$timestamp\n$bodyHash\n$canonicalUrl"

        val mac = Mac.getInstance("HmacMD5")
        val secretBytes = base64DecodeArray(secretKeyDefault)
        mac.init(SecretKeySpec(secretBytes, "HmacMD5"))
        val signature = base64Encode(mac.doFinal(canonical.toByteArray()))

        return "$timestamp|2|$signature"
    }

    private fun getSignedHeaders(method: String, url: String, body: String? = null): Map<String, String> {
        return commonHeaders + mapOf(
            "x-client-token" to generateXClientToken(),
            "x-tr-signature" to generateXTrSignature(method, url, body),
            "content-type" to "application/json"
        )
    }

    // --- IMPLEMENTASI API ---

    override val mainPage: List<MainPageData> = mainPageOf(
        "5283462032510044280" to "Indonesian Drama",
        "6528093688173053896" to "Indonesian Movies",
        "5848753831881965888" to "Indo Horror",
        "997144265920760504" to "Hollywood Movies",
        "4380734070238626200" to "K-Drama"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val id = request.data 
        val targetUrl = "$homeApiUrl/wefeed-h5api-bff/ranking-list/content?id=$id&page=$page&perPage=12"
        
        val headers = getSignedHeaders("GET", targetUrl)
        val responseData = app.get(targetUrl, headers = headers).parsedSafe<Media>()?.data
        val listFilm = responseData?.subjectList ?: responseData?.items

        val home = listFilm?.map { it.toSearchResponse(this) } 
            ?: throw ErrorLoadingException("Gagal memuat kategori.")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val targetUrl = "$apiUrl/wefeed-h5api-bff/subject/search"
        val body = mapOf(
            "keyword" to query,
            "page" to "1",
            "perPage" to "20",
            "subjectType" to "0",
        ).toJson()

        val headers = getSignedHeaders("POST", targetUrl, body)
        return app.post(
            targetUrl, 
            headers = headers,
            requestBody = body.toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Pencarian tidak ditemukan.")
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("?id=").ifEmpty { url.substringAfterLast("/") }
        val detailUrl = "$homeApiUrl/wefeed-h5api-bff/detail?detailPath=$id"
        
        val headers = getSignedHeaders("GET", detailUrl)
        val response = app.get(detailUrl, headers = headers).parsedSafe<MediaDetail>()
        
        val document = response?.data ?: throw ErrorLoadingException("Gagal memuat detail.")
        
        val subject = document.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject?.subjectType == 2) TvType.TvSeries else TvType.Movie
        
        val realId = subject?.subjectId ?: id
        val detailPath = subject?.detailPath ?: id

        return if (tvType == TvType.TvSeries) {
            val episodes = document.resource?.seasons?.flatMap { seasons ->
                val epList = seasons.allEp?.split(",")?.mapNotNull { it.toIntOrNull() } 
                    ?: (1..(seasons.maxEp ?: 1)).toList()
                
                epList.map { ep ->
                    newEpisode(LoadData(realId, seasons.se, ep, detailPath).toJson()) {
                        this.season = seasons.se
                        this.episode = ep
                    }
                }
            } ?: emptyList()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = subject?.description
                this.score = Score.from10(subject?.imdbRatingValue)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, LoadData(realId, detailPath = detailPath).toJson()) {
                this.posterUrl = poster
                this.year = year
                this.plot = subject?.description
                this.score = Score.from10(subject?.imdbRatingValue)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        val playUrl = "$apiUrl/wefeed-h5api-bff/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detailPath=${media.detailPath}"
        
        val headers = getSignedHeaders("GET", playUrl)
        val streams = app.get(playUrl, headers = headers).parsedSafe<Media>()?.data?.streams

        streams?.forEach { source ->
            callback.invoke(
                newExtractorLink(this.name, this.name, source.url ?: return@forEach, INFER_TYPE) {
                    this.quality = getQualityFromName(source.resolutions)
                    this.referer = mainUrl
                }
            )
        }
        return true
    }
}

// --- DATA CLASSES TETAP SAMA SEPERTI SEBELUMNYA ---
data class LoadData(val id: String?, val season: Int?, val episode: Int?, val detailPath: String?)
data class Media(@param:JsonProperty("data") val data: Data? = null) {
    data class Data(
        val subjectList: ArrayList<Items>? = arrayListOf(),
        val items: ArrayList<Items>? = arrayListOf(),
        val streams: ArrayList<Streams>? = arrayListOf()
    ) {
        data class Streams(val url: String?, val resolutions: String?)
    }
}
data class MediaDetail(@param:JsonProperty("data") val data: Data? = null) {
    data class Data(val subject: Items? = null, val resource: Resource? = null) {
        data class Resource(val seasons: ArrayList<Seasons>?) {
            data class Seasons(val se: Int?, val maxEp: Int?, val allEp: String?)
        }
    }
}
data class Items(
    val subjectId: String?, val subjectType: Int?, val title: String?, 
    val description: String?, val releaseDate: String?, val cover: Cover?, 
    val imdbRatingValue: String?, val detailPath: String?
) {
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        val url = "${provider.mainUrl}/detail/${detailPath ?: subjectId}"
        return provider.newMovieSearchResponse(title ?: "", url, if (subjectType == 1) TvType.Movie else TvType.TvSeries, false) {
            this.posterUrl = cover?.url
            this.year = releaseDate?.substringBefore("-")?.toIntOrNull()
        }
    }
    data class Cover(val url: String?)
}
