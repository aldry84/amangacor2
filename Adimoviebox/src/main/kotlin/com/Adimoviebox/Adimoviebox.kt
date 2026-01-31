package com.Adimoviebox

import com.google.gson.annotations.SerializedName
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class Adimoviebox : MainAPI() {
    // Domain & API
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

    // Header Web/H5 (Tanpa Enkripsi Rumit)
    private val commonHeaders = mapOf(
        "Origin" to mainUrl,
        "Referer" to "$mainUrl/",
        "X-Client-Info" to "{\"timezone\":\"Asia/Jakarta\",\"os\":\"web\",\"uuid\":\"web-uuid\"}",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    override val mainPage: List<MainPageData> = mainPageOf(
        "5283462032510044280" to "Indonesian Drama",
        "6528093688173053896" to "Indonesian Movies",
        "5848753831881965888" to "Indo Horror",
        "997144265920760504" to "Hollywood Movies",
        "4380734070238626200" to "K-Drama",
        "8624142774394406504" to "C-Drama",
        "3058742380078711608" to "Disney",
        "8449223314756747760" to "Pinoy Drama"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val id = request.data
        // Endpoint Ranking List
        val targetUrl = "$homeApiUrl/wefeed-h5api-bff/ranking-list/content?id=$id&page=$page&perPage=12"
        
        val response = app.get(targetUrl, headers = commonHeaders).parsedSafe<MediaResponse>()
        val data = response?.data

        val items = data?.subjectList ?: data?.items ?: emptyList()
        if (items.isEmpty()) throw ErrorLoadingException("Data kosong atau gagal dimuat")

        val home = items.map { it.toSearchResponse(this) }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/wefeed-h5api-bff/subject/search"
        
        // Body JSON manual sederhana
        val jsonBody = """
            {
                "keyword": "$query",
                "page": 1,
                "perPage": 12,
                "subjectType": 0
            }
        """.trimIndent()

        val requestBody = jsonBody.toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        // Menggunakan parsedSafe bawaan Cloudstream (Gson)
        val response = app.post(url, headers = commonHeaders, requestBody = requestBody)
            .parsedSafe<MediaResponse>()
            
        val items = response?.data?.items ?: response?.data?.subjectList
        
        return items?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Pencarian tidak ditemukan atau error API.")
    }

    override suspend fun load(url: String): LoadResponse {
        // Ambil ID dari URL (bisa berupa angka ID atau slug path)
        val idOrSlug = url.substringAfterLast("?id=").ifEmpty { url.substringAfterLast("/") }

        // 1. Coba load via detailPath (Slug)
        var detailData: MediaDetailData? = null
        try {
            val slugUrl = "$homeApiUrl/wefeed-h5api-bff/detail?detailPath=$idOrSlug"
            detailData = app.get(slugUrl, headers = commonHeaders).parsedSafe<MediaDetailResponse>()?.data
        } catch (e: Exception) {
            // Lanjut ke fallback
        }

        // 2. Fallback: Coba load via subjectId (ID Angka) jika cara 1 gagal
        if (detailData?.subject == null) {
            val idUrl = "$apiUrl/wefeed-h5api-bff/subject/detail?subjectId=$idOrSlug"
            detailData = app.get(idUrl, headers = commonHeaders).parsedSafe<MediaDetailResponse>()?.data
        }

        val subject = detailData?.subject ?: throw ErrorLoadingException("Gagal memuat detail konten.")

        val title = subject.title ?: "No Title"
        val poster = subject.cover?.url
        val tags = subject.genre?.split(",")?.map { it.trim() }
        val year = subject.releaseDate?.substringBefore("-")?.toIntOrNull()
        val description = subject.description
        val trailer = subject.trailer?.videoAddress?.url
        // Rating handling aman
        val ratingString = subject.imdbRatingValue
        val score = Score.from10(ratingString)

        val realId = subject.subjectId ?: idOrSlug
        val detailPath = subject.detailPath ?: idOrSlug

        // Actors
        val actors = detailData.stars?.mapNotNull { star ->
            val name = star.name ?: return@mapNotNull null
            ActorData(Actor(name, star.avatarUrl), roleString = star.character)
        } ?: emptyList()

        // Recommendations
        val recUrl = "$apiUrl/wefeed-h5api-bff/subject/detail-rec?subjectId=$realId&page=1&perPage=12"
        val recommendations = app.get(recUrl, headers = commonHeaders)
            .parsedSafe<MediaResponse>()?.data?.items?.map { it.toSearchResponse(this) }

        val type = if (subject.subjectType == 2) TvType.TvSeries else TvType.Movie

        if (type == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            detailData.resource?.seasons?.forEach { season ->
                val seasonNum = season.se ?: 1
                val epList = if (!season.allEp.isNullOrEmpty()) {
                    season.allEp.split(",").mapNotNull { it.trim().toIntOrNull() }
                } else {
                    (1..(season.maxEp ?: 1)).toList()
                }

                epList.forEach { epNum ->
                    val epData = LoadDataLink(realId, seasonNum, epNum, detailPath)
                    episodes.add(
                        newEpisode(epData.toJson()) {
                            this.season = seasonNum
                            this.episode = epNum
                            this.name = "Episode $epNum"
                        }
                    )
                }
            }
            return newTvSeriesLoadResponse(title, url, type, episodes) {
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
            val movieData = LoadDataLink(realId, detailPath = detailPath)
            return newMovieLoadResponse(title, url, type, movieData.toJson()) {
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
        val linkData = parseJson<LoadDataLink>(data)
        
        // Header khusus referer untuk play
        val refererUrl = "$mainUrl/spa/videoPlayPage/movies/${linkData.detailPath}?id=${linkData.id}&type=/movie/detail&lang=en"
        val playHeaders = commonHeaders.toMutableMap()
        playHeaders["Referer"] = refererUrl

        val playUrl = "$apiUrl/wefeed-h5api-bff/subject/play?subjectId=${linkData.id}&se=${linkData.season ?: 0}&ep=${linkData.episode ?: 0}&detailPath=${linkData.detailPath}"

        val response = app.get(playUrl, headers = playHeaders).parsedSafe<MediaResponse>()
        val streams = response?.data?.streams

        streams?.reversed()?.forEach { stream ->
            val streamUrl = stream.url ?: return@forEach
            val qualityTag = stream.resolutions ?: "Unknown"
            
            callback.invoke(
                newExtractorLink(
                    this.name,
                    "$name $qualityTag",
                    streamUrl,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = getQualityFromName(qualityTag)
                }
            )

            // Cek Subtitle (hanya sekali dari stream pertama yang valid)
            if (stream.id != null && stream.format != null) {
                // Logic subtitle bisa diletakkan di sini jika perlu looping, 
                // tapi biasanya cukup request terpisah di bawah
            }
        }

        // Request Subtitle Terpisah (Ambil dari stream pertama yang punya ID)
        val firstValidStream = streams?.firstOrNull { it.id != null && it.format != null }
        if (firstValidStream != null) {
            val subUrl = "$apiUrl/wefeed-h5api-bff/subject/caption?format=${firstValidStream.format}&id=${firstValidStream.id}&subjectId=${linkData.id}"
            val subResponse = app.get(subUrl, headers = playHeaders).parsedSafe<MediaResponse>()
            
            subResponse?.data?.captions?.forEach { sub ->
                if (!sub.url.isNullOrEmpty()) {
                    subtitleCallback.invoke(
                        newSubtitleFile(sub.lanName ?: "Unknown", sub.url)
                    )
                }
            }
        }

        return true
    }
}

// ================= DATA CLASSES (GSON STANDARD) =================
// Semua menggunakan @SerializedName agar kompatibel dengan parsedSafe Cloudstream

data class LoadDataLink(
    val id: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val detailPath: String? = null
)

data class MediaResponse(
    @SerializedName("data") val data: MediaData? = null
)

data class MediaData(
    @SerializedName("subjectList") val subjectList: List<MediaItem>? = null,
    @SerializedName("items") val items: List<MediaItem>? = null,
    @SerializedName("streams") val streams: List<MediaStream>? = null,
    @SerializedName("captions") val captions: List<MediaCaption>? = null
)

data class MediaDetailResponse(
    @SerializedName("data") val data: MediaDetailData? = null
)

data class MediaDetailData(
    @SerializedName("subject") val subject: MediaItem? = null,
    @SerializedName("stars") val stars: List<MediaStar>? = null,
    @SerializedName("resource") val resource: MediaResource? = null
)

data class MediaItem(
    @SerializedName("subjectId") val subjectId: String? = null,
    @SerializedName("subjectType") val subjectType: Int? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("cover") val cover: MediaCover? = null,
    @SerializedName("imdbRatingValue") val imdbRatingValue: String? = null,
    @SerializedName("trailer") val trailer: MediaTrailer? = null,
    @SerializedName("detailPath") val detailPath: String? = null,
    @SerializedName("genre") val genre: String? = null
) {
    fun toSearchResponse(provider: MainAPI): SearchResponse {
        val finalUrl = "${provider.mainUrl}/detail/${detailPath ?: subjectId}"
        val poster = cover?.url
        val isMovie = subjectType != 2 // 2 biasanya series

        return provider.newMovieSearchResponse(
            title ?: "No Title",
            finalUrl,
            if (isMovie) TvType.Movie else TvType.TvSeries
        ) {
            this.posterUrl = poster
            this.score = Score.from10(imdbRatingValue)
            this.year = releaseDate?.substringBefore("-")?.toIntOrNull()
        }
    }
}

data class MediaCover(
    @SerializedName("url") val url: String? = null
)

data class MediaTrailer(
    @SerializedName("videoAddress") val videoAddress: MediaVideoAddress? = null
)

data class MediaVideoAddress(
    @SerializedName("url") val url: String? = null
)

data class MediaStar(
    @SerializedName("name") val name: String? = null,
    @SerializedName("character") val character: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)

data class MediaResource(
    @SerializedName("seasons") val seasons: List<MediaSeason>? = null
)

data class MediaSeason(
    @SerializedName("se") val se: Int? = null,
    @SerializedName("maxEp") val maxEp: Int? = null,
    @SerializedName("allEp") val allEp: String? = null
)

data class MediaStream(
    @SerializedName("id") val id: String? = null,
    @SerializedName("format") val format: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("resolutions") val resolutions: String? = null
)

data class MediaCaption(
    @SerializedName("lanName") val lanName: String? = null,
    @SerializedName("url") val url: String? = null
)
