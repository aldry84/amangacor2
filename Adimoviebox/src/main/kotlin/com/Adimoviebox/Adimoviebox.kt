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
    // URL Utama dan API
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

    // --- KEAMANAN (SIGNATURE SYSTEM) ---
    // Kunci rahasia agar dianggap aplikasi resmi (diambil dari MovieBox)
    private val secretKeyDefault = "NzZpUmwwN3MweFNOOWpxbUVXQXQ3OUVCSlp1bElRSXNWNjRGWnIyTw=="

    private val commonHeaders = mapOf(
        "origin" to mainUrl,
        "referer" to "$mainUrl/",
        "x-client-info" to "{\"timezone\":\"Asia/Jakarta\"}",
        "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    private fun md5(input: ByteArray): String {
        return MessageDigest.getInstance("MD5").digest(input)
            .joinToString("") { "%02x".format(it) }
    }

    private fun generateXClientToken(): String {
        val timestamp = System.currentTimeMillis().toString()
        val hash = md5(timestamp.reversed().toByteArray())
        return "$timestamp,$hash"
    }

    private fun generateXTrSignature(method: String, url: String, body: String? = null): String {
        val timestamp = System.currentTimeMillis()
        val parsed = Uri.parse(url)
        val path = parsed.path ?: ""
        
        // Logika canonical string yang ketat
        val bodyHash = if (body != null) md5(body.toByteArray()) else ""
        val bodyLength = body?.toByteArray()?.size?.toString() ?: ""

        val canonical = "${method.uppercase()}\n\n\n$bodyLength\n$timestamp\n$bodyHash\n$path"

        val mac = Mac.getInstance("HmacMD5")
        val secretBytes = base64DecodeArray(secretKeyDefault)
        mac.init(SecretKeySpec(secretBytes, "HmacMD5"))
        val signature = base64Encode(mac.doFinal(canonical.toByteArray()))

        return "$timestamp|2|$signature"
    }

    // Fungsi pembantu untuk membuat header dengan tanda tangan
    private fun getSignedHeaders(method: String, url: String, body: String? = null): Map<String, String> {
        return commonHeaders + mapOf(
            "x-client-token" to generateXClientToken(),
            "x-tr-signature" to generateXTrSignature(method, url, body)
        )
    }

    // --- KATEGORI UTAMA (ASLI) ---
    override val mainPage: List<MainPageData> = mainPageOf(
        "5283462032510044280" to "Indonesian Drama",
        "6528093688173053896" to "Indonesian Movies",
        "5848753831881965888" to "Indo Horror",
        "997144265920760504" to "Hollywood Movies",
        "4380734070238626200" to "K-Drama",
        "8624142774394406504" to "C-Drama",
        "3058742380078711608" to "Disney",
        "8449223314756747760" to "Pinoy Drama",
        "606779077307122552" to "Pinoy Movie",
        "872031290915189720" to "Bad Ending Romance" 
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
        val tags = subject?.genre?.split(",")?.map { it.trim() }
        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        val score = Score.from10(subject?.imdbRatingValue)
        
        val realId = subject?.subjectId ?: id
        val detailPath = subject?.detailPath ?: id

        // Parsing Aktor
        val actors = document.stars?.mapNotNull { cast ->
            ActorData(
                Actor(cast.name ?: return@mapNotNull null, cast.avatarUrl),
                roleString = cast.character
            )
        }?.distinctBy { it.actor.name }

        // Parsing Rekomendasi
        val recUrl = "$apiUrl/wefeed-h5api-bff/subject/detail-rec?subjectId=$realId&page=1&perPage=12"
        val recHeaders = getSignedHeaders("GET", recUrl)
        val recommendations = app.get(recUrl, headers = recHeaders)
            .parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }

        return if (subject?.subjectType == 2) {
            val episodes = document.resource?.seasons?.flatMap { seasons ->
                val epList = if (seasons.allEp.isNullOrEmpty()) (1..(seasons.maxEp ?: 1)).toList() 
                             else seasons.allEp.split(",").map { it.toInt() }
                epList.map { ep ->
                    // Fix: LoadData sekarang memiliki default value, jadi aman
                    newEpisode(LoadData(realId, seasons.se, ep, detailPath).toJson()) {
                        this.season = seasons.se
                        this.episode = ep
                    }
                }
            } ?: emptyList()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            // Fix: LoadData aman dipanggil tanpa season/episode
            newMovieLoadResponse(title, url, TvType.Movie, LoadData(realId, detailPath = detailPath).toJson()) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
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
        val responseData = app.get(playUrl, headers = headers).parsedSafe<Media>()?.data
        val streams = responseData?.streams

        streams?.forEach { source ->
            callback.invoke(
                newExtractorLink(this.name, this.name, source.url ?: return@forEach, INFER_TYPE) {
                    this.quality = getQualityFromName(source.resolutions)
                    this.referer = mainUrl
                }
            )
        }

        // Subtitle logic
        val streamId = streams?.firstOrNull()?.id
        val format = streams?.firstOrNull()?.format
        if (streamId != null && format != null) {
            val subUrl = "$apiUrl/wefeed-h5api-bff/subject/caption?format=$format&id=$streamId&subjectId=${media.id}"
            val subHeaders = getSignedHeaders("GET", subUrl)
            
            app.get(subUrl, headers = subHeaders).parsedSafe<Media>()?.data?.captions?.forEach { subtitle ->
                subtitleCallback.invoke(newSubtitleFile(subtitle.lanName ?: "Unknown", subtitle.url ?: return@forEach))
            }
        }
        return true
    }
}

// --- DATA CLASSES (DIPERBAIKI) ---

// Fix Utama: Menambahkan = null pada semua field
data class LoadData(
    val id: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val detailPath: String? = null
)

data class Media(@param:JsonProperty("data") val data: Data? = null) {
    data class Data(
        @param:JsonProperty("subjectList") val subjectList: ArrayList<Items>? = arrayListOf(),
        @param:JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
        @param:JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
        @param:JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf(),
    ) {
        data class Streams(
            @param:JsonProperty("id") val id: String? = null, 
            @param:JsonProperty("format") val format: String? = null, 
            @param:JsonProperty("url") val url: String? = null, 
            @param:JsonProperty("resolutions") val resolutions: String? = null
        )
        data class Captions(
            @param:JsonProperty("lanName") val lanName: String? = null, 
            @param:JsonProperty("url") val url: String? = null
        )
    }
}

data class MediaDetail(@param:JsonProperty("data") val data: Data? = null) {
    data class Data(
        @param:JsonProperty("subject") val subject: Items? = null, 
        @param:JsonProperty("stars") val stars: ArrayList<Stars>? = arrayListOf(), 
        @param:JsonProperty("resource") val resource: Resource? = null
    ) {
        data class Stars(
            @param:JsonProperty("name") val name: String? = null, 
            @param:JsonProperty("character") val character: String? = null, 
            @param:JsonProperty("avatarUrl") val avatarUrl: String? = null
        )
        data class Resource(
            @param:JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf()
        ) {
            data class Seasons(
                @param:JsonProperty("se") val se: Int? = null, 
                @param:JsonProperty("maxEp") val maxEp: Int? = null, 
                @param:JsonProperty("allEp") val allEp: String? = null
            )
        }
    }
}

data class Items(
    @param:JsonProperty("subjectId") val subjectId: String? = null, 
    @param:JsonProperty("subjectType") val subjectType: Int? = null, 
    @param:JsonProperty("title") val title: String? = null, 
    @param:JsonProperty("description") val description: String? = null, 
    @param:JsonProperty("releaseDate") val releaseDate: String? = null, 
    @param:JsonProperty("cover") val cover: Cover? = null, 
    @param:JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null, 
    @param:JsonProperty("detailPath") val detailPath: String? = null,
    @param:JsonProperty("genre") val genre: String? = null, 
    @param:JsonProperty("trailer") val trailer: Trailer? = null
) {
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        val url = "${provider.mainUrl}/detail/${detailPath ?: subjectId}"
        return provider.newMovieSearchResponse(title ?: "", url, if (subjectType == 1) TvType.Movie else TvType.TvSeries, false) {
            this.posterUrl = cover?.url
            this.year = releaseDate?.substringBefore("-")?.toIntOrNull()
            this.score = Score.from10(imdbRatingValue)
        }
    }
    data class Cover(@param:JsonProperty("url") val url: String? = null)
    data class Trailer(@param:JsonProperty("videoAddress") val videoAddress: VideoAddress? = null) {
        data class VideoAddress(@param:JsonProperty("url") val url: String? = null)
    }
}
