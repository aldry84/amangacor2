package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

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

    // Mapper Jackson untuk membaca @JsonProperty
    private val mapper = jacksonObjectMapper()

    private val commonHeaders = mapOf(
        "origin" to mainUrl,
        "referer" to "$mainUrl/",
        "x-client-info" to "{\"timezone\":\"Asia/Jakarta\"}",
        "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    )

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

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val id = request.data
        val targetUrl = "$homeApiUrl/wefeed-h5api-bff/ranking-list/content?id=$id&page=$page&perPage=12"

        val responseText = app.get(targetUrl, headers = commonHeaders).text
        val responseData = try {
            mapper.readValue(responseText, Media::class.java).data
        } catch (e: Exception) {
            null
        }

        val listFilm = responseData?.subjectList ?: responseData?.items
        val home = listFilm?.map {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("Gagal memuat kategori. Data kosong.")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/wefeed-h5api-bff/subject/search"
        
        // Membuat body JSON manual menggunakan Map agar rapi
        val jsonBodyMap = mapOf(
            "keyword" to query,
            "page" to 1,
            "perPage" to 12,
            "subjectType" to 0
        )
        
        // Konversi Map ke JSON String menggunakan Jackson
        val requestBody = mapper.writeValueAsString(jsonBodyMap)
            .toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val response = app.post(url, headers = commonHeaders, requestBody = requestBody)
        val responseText = response.body.string()

        // Parsing manual dengan Jackson
        val mediaData = try {
            mapper.readValue(responseText, Media::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            throw ErrorLoadingException("Gagal memproses data pencarian.")
        }

        return mediaData.data?.items?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Pencarian tidak ditemukan.")
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("?id=")
            .ifEmpty { url.substringAfterLast("/") }

        // Coba load menggunakan detailPath (slug)
        val detailUrl = "$homeApiUrl/wefeed-h5api-bff/detail?detailPath=$id"
        
        var mediaDetail: MediaDetail? = null
        try {
            val responseText = app.get(detailUrl, headers = commonHeaders).text
            mediaDetail = mapper.readValue(responseText, MediaDetail::class.java)
        } catch (e: Exception) {
            // Abaikan error, lanjut ke fallback
        }

        // Fallback: Jika gagal atau data kosong, coba load menggunakan subjectId
        if (mediaDetail?.data == null) {
            val fallbackUrl = "$apiUrl/wefeed-h5api-bff/subject/detail?subjectId=$id"
            try {
                val fallbackText = app.get(fallbackUrl, headers = commonHeaders).text
                mediaDetail = mapper.readValue(fallbackText, MediaDetail::class.java)
            } catch (e: Exception) {
                throw ErrorLoadingException("Gagal memuat detail konten.")
            }
        }

        val document = mediaDetail?.data ?: throw ErrorLoadingException("Data detail kosong.")
        val subject = document.subject ?: throw ErrorLoadingException("Subjek tidak ditemukan.")

        val title = subject.title ?: ""
        val poster = subject.cover?.url
        val tags = subject.genre?.split(",")?.map { it.trim() }
        val year = subject.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject.subjectType == 2) TvType.TvSeries else TvType.Movie
        val description = subject.description
        val trailer = subject.trailer?.videoAddress?.url
        val score = Score.from10(subject.imdbRatingValue)
        
        val realId = subject.subjectId ?: id
        val detailPath = subject.detailPath ?: id

        val actors = document.stars?.mapNotNull { cast ->
            ActorData(
                Actor(cast.name ?: return@mapNotNull null, cast.avatarUrl),
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        // Load rekomendasi dengan aman
        val recommendations = try {
            val recUrl = "$apiUrl/wefeed-h5api-bff/subject/detail-rec?subjectId=$realId&page=1&perPage=12"
            val recText = app.get(recUrl, headers = commonHeaders).text
            mapper.readValue(recText, Media::class.java).data?.items?.map { it.toSearchResponse(this) }
        } catch (e: Exception) {
            emptyList()
        }

        if (tvType == TvType.TvSeries) {
            val episodes = document.resource?.seasons?.flatMap { season ->
                val epList = if (!season.allEp.isNullOrEmpty()) {
                    season.allEp.split(",").mapNotNull { it.trim().toIntOrNull() }
                } else {
                    (1..(season.maxEp ?: 1)).toList()
                }

                epList.map { epNum ->
                    newEpisode(
                        LoadData(realId, season.se, epNum, detailPath).toJson()
                    ) {
                        this.season = season.se
                        this.episode = epNum
                    }
                }
            } ?: emptyList()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        } else {
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LoadData(realId, detailPath = detailPath).toJson()
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val media = AppUtils.parseJson<LoadData>(data)
        val referer = "$mainUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"
        val specificHeaders = commonHeaders + ("referer" to referer)

        val playUrl = "$apiUrl/wefeed-h5api-bff/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detailPath=${media.detailPath}"
        
        try {
            val responseText = app.get(playUrl, headers = specificHeaders).text
            val mediaData = mapper.readValue(responseText, Media::class.java)
            val streams = mediaData.data?.streams

            streams?.reversed()?.distinctBy { it.url }?.forEach { source ->
                val streamUrl = source.url ?: return@forEach
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        streamUrl,
                        INFER_TYPE
                    ) {
                        this.referer = mainUrl
                        this.quality = getQualityFromName(source.resolutions)
                    }
                )
            }

            // Load Subtitle
            val firstStream = streams?.firstOrNull()
            if (firstStream?.id != null && firstStream.format != null) {
                val subUrl = "$apiUrl/wefeed-h5api-bff/subject/caption?format=${firstStream.format}&id=${firstStream.id}&subjectId=${media.id}"
                try {
                    val subText = app.get(subUrl, headers = specificHeaders).text
                    val subData = mapper.readValue(subText, Media::class.java)
                    
                    subData.data?.captions?.forEach { subtitle ->
                        val subLink = subtitle.url ?: return@forEach
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                subtitle.lanName ?: "Unknown",
                                subLink
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Ignore subtitle errors
                }
            }

        } catch (e: Exception) {
            return false
        }

        return true
    }
}

// --- DATA CLASSES (Compatible with Jackson) ---

data class LoadData(
    val id: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val detailPath: String? = null,
)

data class Media(
    @param:JsonProperty("data") val data: Data? = null,
) {
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
            @param:JsonProperty("resolutions") val resolutions: String? = null,
        )

        data class Captions(
            @param:JsonProperty("lan") val lan: String? = null,
            @param:JsonProperty("lanName") val lanName: String? = null,
            @param:JsonProperty("url") val url: String? = null,
        )
    }
}

data class MediaDetail(
    @param:JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @param:JsonProperty("subject") val subject: Items? = null,
        @param:JsonProperty("stars") val stars: ArrayList<Stars>? = arrayListOf(),
        @param:JsonProperty("resource") val resource: Resource? = null,
    ) {
        data class Stars(
            @param:JsonProperty("name") val name: String? = null,
            @param:JsonProperty("character") val character: String? = null,
            @param:JsonProperty("avatarUrl") val avatarUrl: String? = null,
        )

        data class Resource(
            @param:JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        ) {
            data class Seasons(
                @param:JsonProperty("se") val se: Int? = null,
                @param:JsonProperty("maxEp") val maxEp: Int? = null,
                @param:JsonProperty("allEp") val allEp: String? = null,
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
    @param:JsonProperty("duration") val duration: Long? = null,
    @param:JsonProperty("genre") val genre: String? = null,
    @param:JsonProperty("cover") val cover: Cover? = null,
    @param:JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
    @param:JsonProperty("countryName") val countryName: String? = null,
    @param:JsonProperty("trailer") val trailer: Trailer? = null,
    @param:JsonProperty("detailPath") val detailPath: String? = null,
) {
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        val url = "${provider.mainUrl}/detail/${detailPath ?: subjectId}"
        val posterImage = cover?.url

        return provider.newMovieSearchResponse(
            title ?: "No Title",
            url,
            if (subjectType == 1) TvType.Movie else TvType.TvSeries,
            false
        ) {
            this.posterUrl = posterImage
            this.score = Score.from10(imdbRatingValue)
            this.year = releaseDate?.substringBefore("-")?.toIntOrNull()
        }
    }

    data class Cover(
        @param:JsonProperty("url") val url: String? = null,
    )

    data class Trailer(
        @param:JsonProperty("videoAddress") val videoAddress: VideoAddress? = null,
    ) {
        data class VideoAddress(
            @param:JsonProperty("url") val url: String? = null,
        )
    }
}
