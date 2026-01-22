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
    
    // API LAMA (Gudang): Untuk Search, Detail, dan Playback (Video)
    private val apiUrl = "https://filmboom.top" 
    
    // API BARU (Etalase): Khusus untuk Halaman Depan / Kategori
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

    // --- BAGIAN KATEGORI LENGKAP ---
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
        
        // Request ke API Baru (aoneroom)
        val targetUrl = "$homeApiUrl/wefeed-h5api-bff/ranking-list/content?id=$id&page=$page&perPage=12"

        // Logika Hybrid: Coba ambil 'subjectList' (API Baru), kalau null coba 'items' (Jaga-jaga)
        val responseData = app.get(targetUrl).parsedSafe<Media>()?.data
        val listFilm = responseData?.subjectList ?: responseData?.items

        val home = listFilm?.map {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("Gagal memuat kategori. Data kosong.")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        // API Lama masih menggunakan wadah "items"
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
        val id = url.substringAfterLast("/")
        
        val document = app.get("$apiUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id")
            .parsedSafe<MediaDetail>()?.data
        
        val subject = document?.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }

        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject?.subjectType == 2) TvType.TvSeries else TvType.Movie
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        
        // PERBAIKAN: Menghapus .toString() yang redundan karena imdbRatingValue sudah String?
        val score = Score.from10(subject?.imdbRatingValue) 
        
        val actors = document?.stars?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: return@mapNotNull null,
                    cast.avatarUrl
                ),
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        val recommendations =
            app.get("$apiUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12")
                .parsedSafe<Media>()?.data?.items?.map {
                    it.toSearchResponse(this)
                }

        return if (tvType == TvType.TvSeries) {
            val episode = document?.resource?.seasons?.map { seasons ->
                (if (seasons.allEp.isNullOrEmpty()) (1..(seasons.maxEp ?: 1)) else seasons.allEp.split(",")
                    .map { it.toInt() })
                    .map { episode ->
                        newEpisode(
                            LoadData(
                                id,
                                seasons.se,
                                episode,
                                subject?.detailPath
                            ).toJson()
                        ) {
                            this.season = seasons.se
                            this.episode = episode
                        }
                    }
            }?.flatten() ?: emptyList()
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episode) {
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
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LoadData(id, detailPath = subject?.detailPath).toJson()
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

        val media = parseJson<LoadData>(data)
        val referer = "$apiUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"

        val streams = app.get(
            "$apiUrl/wefeed-h5-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}",
            referer = referer
        ).parsedSafe<Media>()?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.map { source ->
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    source.url ?: return@map,
                    INFER_TYPE
                ) {
                    this.referer = "$apiUrl/"
                    this.quality = getQualityFromName(source.resolutions)
                }
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

// PERBAIKAN: Menambahkan @field: pada semua anotasi @JsonProperty untuk menghindari warning
data class Media(
    @field:JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @field:JsonProperty("subjectList") val subjectList: ArrayList<Items>? = arrayListOf(),
        @field:JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
        @field:JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
        @field:JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf(),
    ) {
        data class Streams(
            @field:JsonProperty("id") val id: String? = null,
            @field:JsonProperty("format") val format: String? = null,
            @field:JsonProperty("url") val url: String? = null,
            @field:JsonProperty("resolutions") val resolutions: String? = null,
        )

        data class Captions(
            @field:JsonProperty("lan") val lan: String? = null,
            @field:JsonProperty("lanName") val lanName: String? = null,
            @field:JsonProperty("url") val url: String? = null,
        )
    }
}

data class MediaDetail(
    @field:JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @field:JsonProperty("subject") val subject: Items? = null,
        @field:JsonProperty("stars") val stars: ArrayList<Stars>? = arrayListOf(),
        @field:JsonProperty("resource") val resource: Resource? = null,
    ) {
        data class Stars(
            @field:JsonProperty("name") val name: String? = null,
            @field:JsonProperty("character") val character: String? = null,
            @field:JsonProperty("avatarUrl") val avatarUrl: String? = null,
        )

        data class Resource(
            @field:JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        ) {
            data class Seasons(
                @field:JsonProperty("se") val se: Int? = null,
                @field:JsonProperty("maxEp") val maxEp: Int? = null,
                @field:JsonProperty("allEp") val allEp: String? = null,
            )
        }
    }
}

data class Items(
    @field:JsonProperty("subjectId") val subjectId: String? = null,
    @field:JsonProperty("subjectType") val subjectType: Int? = null,
    @field:JsonProperty("title") val title: String? = null,
    @field:JsonProperty("description") val description: String? = null,
    @field:JsonProperty("releaseDate") val releaseDate: String? = null,
    @field:JsonProperty("duration") val duration: Long? = null,
    @field:JsonProperty("genre") val genre: String? = null,
    @field:JsonProperty("cover") val cover: Cover? = null,
    @field:JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
    @field:JsonProperty("countryName") val countryName: String? = null,
    @field:JsonProperty("trailer") val trailer: Trailer? = null,
    @field:JsonProperty("detailPath") val detailPath: String? = null,
) {
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        val url = "${provider.mainUrl}/detail/${subjectId}"
        
        val posterImage = cover?.url

        return provider.newMovieSearchResponse(
            title ?: "No Title",
            url,
            if (subjectType == 1) TvType.Movie else TvType.TvSeries,
            false
        ) {
            this.posterUrl = posterImage
            this.score = Score.from10(imdbRatingValue) // .toString() dihapus di sini juga karena sudah String?
            this.year = releaseDate?.substringBefore("-")?.toIntOrNull()
        }
    }

    data class Cover(
        @field:JsonProperty("url") val url: String? = null,
    )

    data class Trailer(
        @field:JsonProperty("videoAddress") val videoAddress: VideoAddress? = null,
    ) {
        data class VideoAddress(
            @field:JsonProperty("url") val url: String? = null,
        )
    }
}
