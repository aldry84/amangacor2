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
    
    // API LAMA (Gudang/Player)
    private val apiUrl = "https://filmboom.top" 
    
    // API BARU (Etalase/Home)
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

    // --- KATEGORI SESUAI PERMINTAAN ---
    override val mainPage: List<MainPageData> = mainPageOf(
        "2|{\"classify\":\"All\",\"country\":\"Indonesia\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Indo Film",
        "5|{\"country\":\"Indonesia\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Indo Drama",
        "5|{\"country\":\"Korea\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "K-Drama",
        "2|{\"classify\":\"All\",\"country\":\"Indonesia\",\"genre\":\"Horror\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Horror Indo",
        "5|{\"classify\":\"All\",\"country\":\"All\",\"genre\":\"Anime\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Anime",
        "5|{\"classify\":\"All\",\"country\":\"China\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "C-Drama",
        "2|{\"classify\":\"All\",\"country\":\"United States\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Hollywood Movie"
    )

    // Header Home
    private fun getBaseHeaders(): Map<String, String> {
        return mapOf(
            "authority" to "h5-api.aoneroom.com",
            "accept" to "application/json",
            "content-type" to "application/json",
            "origin" to mainUrl,
            "referer" to "$mainUrl/",
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
        )
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val dataParts = request.data.split("|")
        val tabId = dataParts[0]
        val filterJson = dataParts.getOrNull(1) ?: ""
        
        // Request ke API Baru (aoneroom)
        val targetUrl = "$homeApiUrl/wefeed-h5api-bff/home/movieFilter?tabId=$tabId&filterType=$filterJson&pageNo=$page&pageSize=18"

        // Struktur response API baru { data: [item1, item2] }
        val responseData = app.get(targetUrl, headers = getBaseHeaders()).parsedSafe<FilterResponse>()
        
        val home = responseData?.data?.map {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("Gagal memuat kategori.")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        // API Lama (filmboom)
        return app.post(
            "$apiUrl/wefeed-h5-bff/web/subject/search", 
            requestBody = mapOf(
                "keyword" to query,
                "page" to "1",
                "perPage" to "0",
                "subjectType" to "0",
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Pencarian tidak ditemukan.")
    }

    override suspend fun load(url: String): LoadResponse {
        // Parsing URL internal
        val isInternalUrl = url.contains("?id=")
        
        val id = if (isInternalUrl) url.substringAfter("id=").substringBefore("&") else url.substringAfterLast("/")
        val path = if (isInternalUrl) url.substringAfter("path=").substringBefore("&") else ""

        // Gunakan API detail
        val detailUrl = if (path.isNotEmpty()) {
            "$homeApiUrl/wefeed-h5api-bff/detail?detailPath=$path"
        } else {
            "$apiUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id"
        }

        val document = app.get(detailUrl, headers = getBaseHeaders()).parsedSafe<MediaDetail>()?.data
        
        val subject = document?.subject
        val title = subject?.title ?: "Unknown"
        val poster = subject?.cover?.url ?: subject?.image?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }

        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject?.subjectType == 1) TvType.Movie else TvType.TvSeries
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        val score = Score.from10(subject?.imdbRatingValue?.toString()) 
        val actors = document?.stars?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: return@mapNotNull null,
                    cast.avatarUrl
                ),
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        val recUrl = "$apiUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12"
        val recommendations = app.get(recUrl).parsedSafe<Media>()?.data?.items?.map {
             it.toSearchResponse(this)
        }

        if (tvType == TvType.TvSeries) {
            val episodeList = mutableListOf<Episode>()
            
            if (!document?.seasonList.isNullOrEmpty()) {
                // API BARU (aoneroom)
                document?.seasonList?.forEach { season ->
                    val sNum = season.seasonNo ?: 1
                    season.episodeList?.forEach { ep ->
                        val epData = LoadData(id, sNum, ep.episodeNo, path).toJson()
                        episodeList.add(newEpisode(epData) {
                            this.name = ep.title
                            this.season = sNum
                            this.episode = ep.episodeNo
                            this.posterUrl = ep.cover?.url
                        })
                    }
                }
            } else {
                // API LAMA (Fallback)
                document?.resource?.seasons?.forEach { season ->
                    val sNum = season.se ?: 1
                    val eps = if (season.allEp.isNullOrEmpty()) (1..(season.maxEp ?: 1)).toList() else season.allEp.split(",").map { it.toInt() }
                    
                    eps.forEach { epNum ->
                        val epData = LoadData(id, sNum, epNum, subject?.detailPath).toJson()
                        episodeList.add(newEpisode(epData) {
                            this.season = sNum
                            this.episode = epNum
                        })
                    }
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
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
                LoadData(id, 0, 0, subject?.detailPath ?: path).toJson()
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

    // PERBAIKAN: Tambahkan @Suppress("DEPRECATION") di sini untuk fix build error
    @Suppress("DEPRECATION")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val media = parseJson<LoadData>(data)
        
        val referer = "https://filmboom.top/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"
        val playHeaders = mapOf(
            "authority" to "filmboom.top",
            "referer" to referer,
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}"
        )

        val playUrl = "$apiUrl/wefeed-h5api-bff/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detailPath=${media.detailPath}"
        
        val streams = app.get(playUrl, headers = playHeaders).parsedSafe<Media>()?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.map { source ->
            // Fix ExtractorLink (Constructor Biasa dengan urutan parameter)
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    source.url ?: return@map,
                    "https://filmboom.top/",
                    getQualityFromName(source.resolutions),
                    source.url.contains(".m3u8")
                )
            )
        }

        val id = streams?.firstOrNull()?.id
        val format = streams?.firstOrNull()?.format

        if (id != null && format != null) {
            app.get(
                "$apiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=${media.id}",
                referer = referer
            ).parsedSafe<Media>()?.data?.captions?.map { subtitle ->
                subtitleCallback.invoke(
                    newSubtitleFile(
                        subtitle.lanName ?: "",
                        subtitle.url ?: return@map
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
)

// Response Filter
data class FilterResponse(
    @JsonProperty("data") val data: ArrayList<Items>? = arrayListOf()
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
            @JsonProperty("lan") val lan: String? = null,
            @JsonProperty("lanName") val lanName: String? = null,
            @JsonProperty("url") val url: String? = null,
        )
    }
}

data class MediaDetail(
    @JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @JsonProperty("subject") val subject: Items? = null,
        @JsonProperty("stars") val stars: ArrayList<Stars>? = arrayListOf(),
        @JsonProperty("resource") val resource: Resource? = null,
        @JsonProperty("episodesCount") val episodesCount: Int? = null,
        @JsonProperty("seasonList") val seasonList: ArrayList<SeasonObj>? = arrayListOf()
    ) {
        data class Stars(
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("character") val character: String? = null,
            @JsonProperty("avatarUrl") val avatarUrl: String? = null,
        )

        data class Resource(
            @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        ) {
            data class Seasons(
                @JsonProperty("se") val se: Int? = null,
                @JsonProperty("maxEp") val maxEp: Int? = null,
                @JsonProperty("allEp") val allEp: String? = null,
            )
        }
        
        data class SeasonObj(
            @JsonProperty("seasonNo") val seasonNo: Int? = null,
            @JsonProperty("episodeList") val episodeList: ArrayList<EpisodeObj>? = arrayListOf()
        )
        data class EpisodeObj(
            @JsonProperty("episodeNo") val episodeNo: Int? = null,
            @JsonProperty("title") val title: String? = null,
            @JsonProperty("cover") val cover: Items.Cover? = null
        )
    }
}

data class Items(
    @JsonProperty("subjectId") val subjectId: String? = null,
    @JsonProperty("subjectType") val subjectType: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null,
    @JsonProperty("duration") val duration: Long? = null,
    @JsonProperty("genre") val genre: String? = null,
    @JsonProperty("cover") val cover: Cover? = null,
    @JsonProperty("image") val image: Cover? = null,
    @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
    @JsonProperty("countryName") val countryName: String? = null,
    @JsonProperty("trailer") val trailer: Trailer? = null,
    @JsonProperty("detailPath") val detailPath: String? = null,
) {
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        val path = detailPath ?: ""
        val url = "${provider.mainUrl}/detail?id=${subjectId}&path=${path}"
        
        val posterImage = cover?.url ?: image?.url

        return provider.newMovieSearchResponse(
            title ?: "No Title",
            url, 
            if (subjectType == 1) TvType.Movie else TvType.TvSeries
        ) {
            this.posterUrl = posterImage
            this.score = Score.from10(imdbRatingValue?.toString())
            this.year = releaseDate?.substringBefore("-")?.toIntOrNull()
        }
    }

    data class Cover(
        @JsonProperty("url") val url: String? = null,
    )

    data class Trailer(
        @JsonProperty("videoAddress") val videoAddress: VideoAddress? = null,
    ) {
        data class VideoAddress(
            @JsonProperty("url") val url: String? = null,
        )
    }
}
