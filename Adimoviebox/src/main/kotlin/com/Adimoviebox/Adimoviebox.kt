package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    
    // SERVER 1 (Lama/Backup)
    private val apiUrl = "https://filmboom.top" 
    
    // SERVER 2 (Baru/Utama) - Kita pakai ini untuk Video juga!
    private val homeApiUrl = "https://h5-api.aoneroom.com"

    override var name = "Adimoviebox"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    // Header Dasar
    private val baseHeaders = mapOf(
        "Accept" to "application/json",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
        "x-client-info" to "{\"timezone\":\"Asia/Jakarta\"}"
    )

    // Switch Header (LokLok vs Default)
    private fun getDynamicHeaders(isLokLok: Boolean, isAone: Boolean = false): Map<String, String> {
        return baseHeaders + if (isLokLok) {
            mapOf("Origin" to "https://lok-lok.cc", "Referer" to "https://lok-lok.cc/")
        } else if (isAone) {
            mapOf("Origin" to "https://moviebox.ph", "Referer" to "https://moviebox.ph/")
        } else {
            mapOf("Origin" to "https://filmboom.top", "Referer" to "https://filmboom.top/")
        }
    }

    override val mainPage = mainPageOf(
        "home" to "Home",
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

    @Suppress("DEPRECATION")
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val id = request.data
        val targetUrl = if (request.name == "Home") {
            "$homeApiUrl/wefeed-h5api-bff/ranking-list/content?id=5283462032510044280&page=$page&perPage=12"
        } else {
            "$homeApiUrl/wefeed-h5api-bff/ranking-list/content?id=$id&page=$page&perPage=12"
        }
        
        val response = app.get(targetUrl).parsedSafe<Media>()
        val data = response?.data
        val listFilm = data?.subjectList ?: data?.items

        if (listFilm.isNullOrEmpty()) throw ErrorLoadingException("Data Kosong")

        val home = listFilm.mapNotNull { item ->
            item.toSearchResponse(this)
        }

        return newHomePageResponse(request.name, home)
    }

    @Suppress("DEPRECATION")
    override suspend fun search(query: String): List<SearchResponse> {
        // Coba Search via API Baru dulu
        val url = "$homeApiUrl/wefeed-h5api-bff/web/subject/search"
        val bodyMap = mapOf(
            "keyword" to query,
            "page" to "1",
            "perPage" to "20",
            "subjectType" to "0"
        )
        val requestBody = bodyMap.toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val response = app.post(url, requestBody = requestBody).parsedSafe<Media>()
        val items = response?.data?.items

        return items?.mapNotNull { it.toSearchResponse(this) } 
            ?: throw ErrorLoadingException("Pencarian tidak ditemukan.")
    }

    @Suppress("DEPRECATION")
    override suspend fun load(url: String): LoadResponse? {
        val isLokLok = url.contains("lok-lok.cc")
        // Gunakan API Baru (Aoneroom) karena lebih lengkap
        val headers = getDynamicHeaders(isLokLok, isAone = true)
        
        val regex = "(?:detail\\/|movies\\/)([^?]+)".toRegex()
        val matchResult = regex.find(url)
        val idFromUrl = matchResult?.groupValues?.get(1) ?: url.substringAfterLast("/")

        // API URL BARU (h5api)
        val targetUrl = "$homeApiUrl/wefeed-h5api-bff/web/subject/detail?subjectId=$idFromUrl"

        val response = app.get(targetUrl, headers = headers).parsedSafe<MediaDetail>()?.data
        
        val subject = response?.subject ?: throw ErrorLoadingException("Detail Kosong")
        
        val title = subject.title ?: "No Title"
        val poster = subject.cover?.url
        val description = subject.description
        val year = subject.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tags = subject.genre?.split(",")?.map { it.trim() }
        val trailerUrl = subject.trailer?.videoAddress?.url
        val scoreObj = Score.from10(subject.imdbRatingValue)
        
        val recommendations = app.get("$homeApiUrl/wefeed-h5api-bff/web/subject/detail-rec?subjectId=$idFromUrl&page=1&perPage=12", headers = headers)
            .parsedSafe<Media>()?.data?.items?.mapNotNull { it.toSearchResponse(this) }

        val isSeries = subject.subjectType == 2
        val sourceFlag = if (isLokLok) "LOKLOK" else "MBOX"

        if (isSeries) {
            val episodes = ArrayList<Episode>()
            response.resource?.seasons?.forEach { season ->
                val seasonNum = season.se ?: 1
                val epList = if (season.allEp.isNullOrEmpty()) {
                    (1..(season.maxEp ?: 0)).toList()
                } else {
                    season.allEp.split(",").mapNotNull { it.toIntOrNull() }
                }

                epList.forEach { epNum ->
                    val loadData = LoadData(subject.subjectId, seasonNum, epNum, subject.detailPath, sourceFlag).toJson()
                    episodes.add(
                        newEpisode(loadData) {
                            this.name = "Episode $epNum"
                            this.season = seasonNum
                            this.episode = epNum
                            this.posterUrl = poster
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                this.score = scoreObj
                if (!trailerUrl.isNullOrEmpty()) addTrailer(trailerUrl)
            }

        } else {
            val loadData = LoadData(subject.subjectId, 0, 0, subject.detailPath, sourceFlag).toJson()
            return newMovieLoadResponse(title, url, TvType.Movie, loadData) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                this.score = scoreObj
                if (!trailerUrl.isNullOrEmpty()) addTrailer(trailerUrl)
            }
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val media = parseJson<LoadData>(data)
        val isLokLok = media.source == "LOKLOK"
        
        // --- STRATEGI DUAL ENGINE ---
        // Kita coba API Baru (Aoneroom) dulu, kalau gagal, coba API Lama (Filmboom)
        
        // 1. Setup API Baru (Prioritas)
        val apiV2 = homeApiUrl
        val pathV2 = "wefeed-h5api-bff"
        val headersV2 = getDynamicHeaders(isLokLok, isAone = true)
        
        // 2. Setup API Lama (Cadangan)
        val apiV1 = apiUrl
        val pathV1 = "wefeed-h5-bff"
        val headersV1 = getDynamicHeaders(isLokLok, isAone = false)

        val refererUrl = "$apiV2/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"
        
        // Coba Fetch Link (Percobaan 1: Server Baru)
        var streams: ArrayList<Media.Data.Streams>? = null
        var usedApiUrl = apiV2
        var usedHeaders = headersV2

        try {
            val playUrlV2 = "$apiV2/$pathV2/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}"
            val responseV2 = app.get(playUrlV2, headers = headersV2).parsedSafe<Media>()
            streams = responseV2?.data?.streams
        } catch (e: Exception) {
            // Abaikan error, lanjut ke backup
        }

        // Jika Gagal, Coba Fetch Link (Percobaan 2: Server Lama)
        if (streams.isNullOrEmpty()) {
            try {
                usedApiUrl = apiV1
                usedHeaders = headersV1
                val playUrlV1 = "$apiV1/$pathV1/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}"
                val responseV1 = app.get(playUrlV1, headers = headersV1).parsedSafe<Media>()
                streams = responseV1?.data?.streams
            } catch (e: Exception) {
               // Gagal total
            }
        }

        if (streams.isNullOrEmpty()) return false

        streams?.forEach { source ->
            val videoUrl = source.url ?: return@forEach
            val qualityStr = source.resolutions ?: "Unknown"
            val qualityInt = getQualityFromName(qualityStr)
            
            // Generate Extractor Link
            callback.invoke(
                newExtractorLink(
                    this.name,
                    "Adimoviebox $qualityStr",
                    videoUrl,
                    INFER_TYPE
                ) {
                    this.headers = usedHeaders
                    this.quality = qualityInt
                }
            )
        }

        // Subtitle Logic
        val firstStream = streams?.firstOrNull()
        if (firstStream != null) {
            // Sesuaikan path subtitle dengan API yang berhasil dipakai
            val pathSub = if (usedApiUrl == homeApiUrl) "wefeed-h5api-bff" else "wefeed-h5-bff"
            val subUrl = "$usedApiUrl/$pathSub/web/subject/caption?format=${firstStream.format}&id=${firstStream.id}&subjectId=${media.id}"
            
            app.get(subUrl, headers = usedHeaders).parsedSafe<Media>()?.data?.captions?.forEach { sub ->
                subtitleCallback.invoke(
                    SubtitleFile(
                        lang = sub.lanName ?: "Unknown",
                        url = sub.url ?: return@forEach
                    )
                )
            }
        }

        return true
    }
}

// --- DATA CLASSES ---

data class LoadData(
    val id: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val detailPath: String? = null,
    val source: String? = "MBOX"
)

data class Media(
    @JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @JsonProperty("subjectList") val subjectList: ArrayList<Items>? = arrayListOf(),
        @JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
        @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
        @JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf(),
    ) {
        data class Streams(
            @JsonProperty("id") val id: String? = null,
            @JsonProperty("format") val format: String? = null,
            @JsonProperty("url") val url: String? = null,
            @JsonProperty("resolutions") val resolutions: String? = null,
        )
        data class Captions(
            @JsonProperty("
