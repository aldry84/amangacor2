package com.AdiDrakor

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class AdiDrakor : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    private val apiUrl = "https://fmoviesunblocked.net"
    
    override val instantLinkLoading = true
    override var name = "AdiDrakor" 
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "en"
    
    override val supportedTypes = setOf(
        TvType.TvSeries, 
        TvType.AsianDrama 
    )

    override val mainPage: List<MainPageData> = mainPageOf(
        "2,ForYou" to "Drakor Pilihan",
        "2,Hottest" to "Drakor Terpopuler",
        "2,Latest" to "Drakor Terbaru",
        "2,Rating" to "Drakor Rating Tertinggi",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val params = request.data.split(",")
        
        if (params.first() != "2") throw ErrorLoadingException("Halaman utama AdiDrakor hanya untuk TvSeries (Drakor).")

        val body = mapOf(
            "channelId" to params.first(),
            "page" to page,
            "perPage" to "24",
            "sort" to params.last()
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        // Line 56: Mengakses data melalui parsedSafe<Media>()?.data
        val home = app.post("$mainUrl/wefeed-h5-bff/web/filter", requestBody = body)
            .parsedSafe<Media>()?.data?.items
            // Memperbaiki Unresolved reference 'it'
            ?.filter { 
                it.subjectType == 2 && 
                it.countryName?.contains("Korea", ignoreCase = true) == true
            } 
            ?.map {
                it.toSearchResponse(this)
            } ?: throw ErrorLoadingException("Tidak ada Data Drakor Ditemukan")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query) ?: emptyList()
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val results = app.post(
            "$mainUrl/wefeed-h5-bff/web/subject/search", requestBody = mapOf(
                "keyword" to query,
                "page" to "1",
                "perPage" to "0",
                "subjectType" to "2",
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        // Line 82: Mengakses data melalui parsedSafe<Media>()?.data
        ).parsedSafe<Media>()?.data?.items
            // Memperbaiki Unresolved reference 'it'
            ?.filter { 
                it.subjectType == 2 && 
                it.countryName?.contains("Korea", ignoreCase = true) == true
            }
            ?.map { it.toSearchResponse(this) }
            ?: return null
            
        return results 
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        // Line 98: Mengakses data melalui parsedSafe<MediaDetail>()?.data
        val document = app.get("$mainUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id")
            .parsedSafe<MediaDetail>()?.data
        val subject = document?.subject
        
        // Memperbaiki Unresolved reference 'countryName'
        if (subject?.subjectType != 2 || subject.countryName?.contains("Korea", ignoreCase = true) != true) {
             throw ErrorLoadingException("Konten ini bukan Drama Korea yang valid. Tipe: ${subject?.subjectType}, Negara: ${subject?.countryName}")
        }
        
        // Memperbaiki Unresolved reference 'title', 'cover', 'genre', 'releaseDate'
        val title = subject.title ?: ""
        val poster = subject.cover?.url
        val tags = subject.genre?.split(",")?.map { it.trim() }

        val year = subject.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = TvType.TvSeries 
        
        // Memperbaiki Unresolved reference 'description', 'trailer'
        val description = subject.description
        val trailer = subject.trailer?.videoAddress?.url
        
        // Memperbaiki Unresolved reference 'stars' dan masalah infer type
        val actors = document.stars?.mapNotNull { cast ->
            ActorData(
                Actor(
                    // Memperbaiki Unresolved reference 'name', 'avatarUrl'
                    cast.name ?: return@mapNotNull null,
                    cast.avatarUrl
                ),
                // Memperbaiki Unresolved reference 'character'
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        // Line 129: Mengakses data melalui parsedSafe<Media>()?.data
        val recommendations =
            app.get("$mainUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12")
                .parsedSafe<Media>()?.data?.items
                // Memperbaiki Unresolved reference 'it'
                ?.filter { 
                    it.subjectType == 2 && 
                    it.countryName?.contains("Korea", ignoreCase = true) == true 
                }
                ?.map {
                    it.toSearchResponse(this)
                }

        // Memperbaiki Unresolved reference 'resource', 'seasons' dan masalah infer type
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, 
                document.resource?.seasons?.map { seasons ->
                // Memperbaiki Unresolved reference 'allEp' dan 'maxEp'
                (if (seasons.allEp.isNullOrEmpty()) (1..seasons.maxEp!!) else seasons.allEp.split(",")
                    .map { it.toInt() })
                    .map { episode ->
                        newEpisode(
                            // Memperbaiki error Too many arguments/Unresolved reference: pastikan LoadData memiliki parameter
                            LoadData(
                                id,
                                seasons.se, // Unresolved reference 'se' diperbaiki dengan mengembalikannya ke Seasons data class
                                episode,
                                subject.detailPath // Unresolved reference 'detailPath' diperbaiki
                            ).toJson()
                        ) {
                            this.season = seasons.se
                            this.episode = episode
                        }
                    }
            }?.flatten() ?: emptyList()) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                // Memperbaiki Unresolved reference 'imdbRatingValue'
                this.score = Score.from10(subject.imdbRatingValue) 
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        
        // Memperbaiki Unresolved reference 'detailPath' dan 'id'
        val referer = "$apiUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"

        val streams = app.get(
            // Memperbaiki Unresolved reference 'id', 'season', 'episode'
            "$apiUrl/wefeed-h5-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}",
            referer = referer
        // Line 182: Mengakses data melalui parsedSafe<Media>()?.data
        ).parsedSafe<Media>()?.data?.streams

        // Memperbaiki masalah infer type dan Unresolved reference 'url'
        streams?.reversed()?.distinctBy { it.url }?.map { source ->
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    source.url ?: return@map,
                    INFER_TYPE
                ) {
                    this.referer = "$apiUrl/"
                    // Memperbaiki Unresolved reference 'resolutions'
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }

        // Memperbaiki Unresolved reference 'id' dan masalah Function invocation 'format(...)' expected
        val id = streams?.first()?.id
        val format = streams?.first()?.format

        app.get(
            // Memperbaiki Unresolved reference 'id'
            "$apiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=${media.id}",
            referer = referer
        // Line 204: Mengakses data melalui parsedSafe<Media>()?.data
        ).parsedSafe<Media>()?.data?.captions?.map { subtitle ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    // Memperbaiki Unresolved reference 'lanName' dan 'url'
                    subtitle.lanName ?: "",
                    subtitle.url ?: return@map
                )
            )
        }

        return true
    }
    
    // --- DATA CLASSES YANG DIPERBAIKI ---
    // Memperbaiki Data class must have at least one primary constructor parameter.
    
    data class LoadData(
        val id: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val detailPath: String? = null,
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
                // Memperbaiki Unresolved reference 'url' (Line 250)
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
            // Memperbaiki Unresolved reference 'stars'
            @JsonProperty("stars") val stars: ArrayList<Stars>? = arrayListOf(),
            // Memperbaiki Unresolved reference 'resource'
            @JsonProperty("resource") val resource: Resource? = null,
        ) {
            data class Stars(
                // Memperbaiki Unresolved reference 'name', 'character', 'avatarUrl'
                @JsonProperty("name") val name: String? = null,
                @JsonProperty("character") val character: String? = null,
                @JsonProperty("avatarUrl") val avatarUrl: String? = null,
            )

            data class Resource(
                @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
            ) {
                data class Seasons(
                    // Memperbaiki Unresolved reference 'se', 'maxEp', 'allEp'
                    @JsonProperty("se") val se: Int? = null,
                    @JsonProperty("maxEp") val maxEp: Int? = null,
                    @JsonProperty("allEp") val allEp: String? = null,
                )
            }
        }
    }

    data class Items(
        // Memperbaiki semua Unresolved reference: subjectId, subjectType, title, dll.
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("subjectType") val subjectType: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("releaseDate") val releaseDate: String? = null,
        @JsonProperty("duration") val duration: Long? = null,
        @JsonProperty("genre") val genre: String? = null,
        @JsonProperty("cover") val cover: Cover? = null,
        @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null, 
        @JsonProperty("countryName") val countryName: String? = null, 
        @JsonProperty("trailer") val trailer: Trailer? = null,
        @JsonProperty("detailPath") val detailPath: String? = null,
    ) {

        fun toSearchResponse(provider: AdiDrakor): SearchResponse {
            val type = TvType.TvSeries
            
            return provider.newMovieSearchResponse(
                title ?: "",
                subjectId ?: "",
                type, 
                false
            ) {
                this.posterUrl = cover?.url
                this.score = Score.from10(imdbRatingValue)
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
}
