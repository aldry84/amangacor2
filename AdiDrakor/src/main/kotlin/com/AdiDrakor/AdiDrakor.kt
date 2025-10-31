package com.AdiDrakor // PERBAIKAN: Pastikan ini adalah baris pertama file tanpa spasi di atasnya

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
        TvType.AsianDrama,
        TvType.Movie // Ditambahkan untuk mendukung pencarian non-Drakor
    )

    // PERUBAHAN UTAMA: Kategori Film Baru
    // Format data: "subjectType,sort,countryFilter" atau "channelId,sort" (untuk kategori lama)
    // subjectType: 0=All, 1=Movie, 2=Series (Digunakan di body API)
    // countryFilter: 'movie' (subjectType=1), 'series' (subjectType=2), 'korea', 'indonesia'
    override val mainPage: List<MainPageData> = mainPageOf(
        // KATEGORI UMUM BARU
        "1,Hottest,movie" to "Movies Populer",      // subjectType=1 (Movie), sort=Hottest, filter=movie
        "1,Latest,movie" to "Movies Terbaru",       // subjectType=1 (Movie), sort=Latest, filter=movie
        "2,Hottest,series" to "Series Populer",     // subjectType=2 (Series), sort=Hottest, filter=series
        "2,Latest,series" to "Series Terbaru",      // subjectType=2 (Series), sort=Latest, filter=series
        
        // KATEGORI KHUSUS (Drakor, Indonesia)
        "2,Hottest,korea" to "Drakor Populer",      // subjectType=2 (Series), sort=Hottest, filter=korea
        "2,Latest,korea" to "Drakor Terbaru",       // subjectType=2 (Series), sort=Latest, filter=korea
        "1,Hottest,indonesia" to "Indonesia Punya", // subjectType=1 (Movie), sort=Hottest, filter=indonesia
        
        // KATEGORI LAMA (SISANYA JANGAN DI OTAK ATIK)
        "2,ForYou" to "Drakor Pilihan",             // channelId=2, sort=ForYou
        "2,Rating" to "Drakor Rating Tertinggi",    // channelId=2, sort=Rating
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val params = request.data.split(",")
        
        // Cek jika menggunakan format lama (Hanya 2 parameter, contoh: 2,ForYou)
        if (params.size == 2) {
            val channelId = params.first()
            val sort = params.last()

            // Logika untuk kategori lama: "Drakor Pilihan" dan "Drakor Rating Tertinggi"
            val body = mapOf(
                "channelId" to channelId,
                "page" to page,
                "perPage" to "24",
                "sort" to sort
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

            val home = app.post("$mainUrl/wefeed-h5-bff/web/filter", requestBody = body)
                .parsedSafe<Media>()?.data?.items
                // Pertahankan filter Korea/Drama
                ?.filter { it.countryName?.contains("Korea", ignoreCase = true) == true || it.subjectType == 2 } 
                ?.map {
                    it.toSearchResponse(this)
                } ?: throw ErrorLoadingException("Tidak ada Data Drakor Ditemukan")

            return newHomePageResponse(request.name, home)
        }
        
        // LOGIKA BARU UNTUK KATEGORI UMUM, DRAKOR POPULER/TERBARU, DAN INDONESIA PUNYA (3 parameter)
        // Format params: [subjectType, sort, countryFilter]
        val subjectType = params[0] // 1=Movie, 2=Series (0 tidak digunakan karena filter lebih spesifik)
        val sort = params[1]
        val countryFilter = params[2] 
        
        // Gunakan channel 0 untuk akses filter yang lebih luas
        val body = mapOf(
            "channelId" to "0", 
            "page" to page,
            "perPage" to "24",
            "subjectType" to subjectType, 
            "sort" to sort
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        
        val allItems = app.post("$mainUrl/wefeed-h5-bff/web/filter", requestBody = body)
            .parsedSafe<Media>()?.data?.items ?: throw ErrorLoadingException("Tidak ada Data Ditemukan")
        
        // Terapkan filter lokal berdasarkan 'countryFilter'
        val home = allItems.filter { item ->
            when (countryFilter.lowercase()) {
                "korea" -> item.countryName?.contains("Korea", ignoreCase = true) == true && item.subjectType == 2 // Drakor
                "indonesia" -> item.countryName?.contains("Indonesia", ignoreCase = true) == true // Film/Series Indonesia
                "series" -> item.subjectType == 2 // Semua Series
                "movie" -> item.subjectType == 1 // Semua Movie
                else -> true 
            }
        }.map { it.toSearchResponse(this) }
        
        if (home.isEmpty()) throw ErrorLoadingException("Tidak ada Data Ditemukan untuk ${request.name}")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query) ?: emptyList()
    }

    // FUNGSI INI DIUBAH: Menghilangkan filter agar semua konten muncul di hasil pencarian
    override suspend fun search(query: String): List<SearchResponse>? {
        val results = app.post(
            "$mainUrl/wefeed-h5-bff/web/subject/search", requestBody = mapOf(
                "keyword" to query,
                "page" to "1",
                "perPage" to "0",
                "subjectType" to "0", // Cari semua tipe (0)
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items
            // Hapus filter konten Korea/Drama di sini
            ?.map { it.toSearchResponse(this) }
            ?: return null
            
        return results 
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val document = app.get("$mainUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id")
            .parsedSafe<MediaDetail>()?.data
        val subject = document?.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }

        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        
        // Tentukan tipe yang benar
        val tvType = when (subject?.subjectType) {
            1 -> TvType.Movie
            2 -> TvType.TvSeries
            1006 -> TvType.Anime
            else -> TvType.Movie
        }
        
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        
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
            app.get("$mainUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12")
                .parsedSafe<Media>()?.data?.items
                // Pertahankan filter rekomendasi untuk Drama Korea
                ?.filter { it.countryName?.contains("Korea", ignoreCase = true) == true || it.subjectType == 2 }
                ?.map {
                    it.toSearchResponse(this)
                }

        // LOGIKA BARU: Jika Movie, gunakan MovieLoadResponse
        if (tvType == TvType.Movie) {
             return newMovieLoadResponse(title, url, TvType.Movie, document?.resource?.seasons?.firstOrNull()?.allEp?.split(",")?.map { it.toInt() }?.map { ep ->
                newEpisode(
                    LoadData(
                        id,
                        1,
                        ep,
                        subject?.detailPath
                    ).toJson()
                ) {
                    this.episode = ep
                }
            } ?: emptyList()) {
                 this.posterUrl = poster
                 this.year = year
                 this.plot = description
                 this.tags = tags
                 this.score = Score.from10(subject?.imdbRatingValue) 
                 this.actors = actors
                 this.recommendations = recommendations
                 addTrailer(trailer, addRaw = true)
            }
        }
        
        // Logika TvSeries (Drakor/Serial)
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries,
                document?.resource?.seasons?.map { seasons ->
                (if (seasons.allEp.isNullOrEmpty()) (1..seasons.maxEp!!) else seasons.allEp.split(",")
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
            }?.flatten() ?: emptyList()) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(subject?.imdbRatingValue) 
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

        val id = streams?.first()?.id
        val format = streams?.first()?.format

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

        return true
    }
    
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
        }
    }

    data class Items(
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("subjectType") val subjectType: Int? = null, // 2 = TvSeries/Drama, 1 = Movie, 1006 = Anime
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("releaseDate") val releaseDate: String? = null,
        @JsonProperty("duration") val duration: Long? = null,
        @JsonProperty("genre") val genre: String? = null,
        @JsonProperty("cover") val cover: Cover? = null,
        @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
        @JsonProperty("countryName") val countryName: String? = null, // Digunakan untuk filter Korea
        @JsonProperty("trailer") val trailer: Trailer? = null,
        @JsonProperty("detailPath") val detailPath: String? = null,
    ) {

        // FUNGSI INI DIUBAH: Menggunakan tipe yang benar untuk hasil pencarian
        fun toSearchResponse(provider: AdiDrakor): SearchResponse {
            val type = when (subjectType) {
                1 -> TvType.Movie
                2 -> TvType.TvSeries
                1006 -> TvType.Anime
                else -> TvType.Movie
            }
            
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
