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

// Menggunakan nama yang sama dengan sebelumnya
class AdiDrakor : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    private val apiUrl = "https://fmoviesunblocked.net"
    
    override val instantLinkLoading = true
    override var name = "AdiDrakor" 
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "en"
    
    // Pembatasan tipe hanya untuk TvSeries/AsianDrama (menghilangkan Anime dan Movie)
    override val supportedTypes = setOf(
        TvType.TvSeries, 
        TvType.AsianDrama 
    )

    // Fokuskan halaman utama hanya pada kategori TvSeries (ID 2)
    override val mainPage: List<MainPageData> = mainPageOf(
        "2,ForYou" to "Drakor Pilihan",
        "2,Hottest" to "Drakor Terpopuler",
        "2,Latest" to "Drakor Terbaru",
        "2,Rating" to "Drakor Rating Tertinggi",
    )

    // **PERBAIKAN FOKUS KETAT: getMainPage**
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val params = request.data.split(",")
        
        // Memastikan channelId adalah '2' (TvSeries)
        if (params.first() != "2") throw ErrorLoadingException("Halaman utama AdiDrakor hanya untuk TvSeries (Drakor).")

        val body = mapOf(
            "channelId" to params.first(),
            "page" to page,
            "perPage" to "24",
            "sort" to params.last()
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val home = app.post("$mainUrl/wefeed-h5-bff/web/filter", requestBody = body)
            .parsedSafe<Media>()?.data?.items
            // FILTER KETAT: subjectType harus 2 (TvSeries) DAN countryName harus mengandung "Korea"
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

    // **PERBAIKAN FOKUS KETAT: search**
    override suspend fun search(query: String): List<SearchResponse>? {
        val results = app.post(
            "$mainUrl/wefeed-h5-bff/web/subject/search", requestBody = mapOf(
                "keyword" to query,
                "page" to "1",
                "perPage" to "0",
                "subjectType" to "2", // **MEMBATASI PENCARIAN HANYA PADA TV SERIES (2)**
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items
            // FILTER KETAT: Pastikan hasil pencarian adalah TvSeries (2) DAN dari Korea
            ?.filter { 
                it.subjectType == 2 && 
                it.countryName?.contains("Korea", ignoreCase = true) == true
            }
            ?.map { it.toSearchResponse(this) }
            ?: return null
            
        return results 
    }

    // **PERBAIKAN FOKUS KETAT: load**
    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val document = app.get("$mainUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id")
            .parsedSafe<MediaDetail>()?.data
        val subject = document?.subject
        
        // PEMERIKSAAN KETAT: Tolak jika bukan TvSeries/Drama (2) atau bukan dari Korea
        if (subject?.subjectType != 2 || subject.countryName?.contains("Korea", ignoreCase = true) != true) {
             throw ErrorLoadingException("Konten ini bukan Drama Korea yang valid. Tipe: ${subject?.subjectType}, Negara: ${subject?.countryName}")
        }
        
        val title = subject.title ?: ""
        val poster = subject.cover?.url
        val tags = subject.genre?.split(",")?.map { it.trim() }

        val year = subject.releaseDate?.substringBefore("-")?.toIntOrNull()
        // Kita paksa TvType.TvSeries karena kita sudah memfilter di atas.
        val tvType = TvType.TvSeries 
        
        val description = subject.description
        val trailer = subject.trailer?.videoAddress?.url
        
        val actors = document.stars?.mapNotNull { cast ->
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
                // FILTER KETAT: Hanya rekomendasikan Drakor
                ?.filter { 
                    it.subjectType == 2 && 
                    it.countryName?.contains("Korea", ignoreCase = true) == true 
                }
                ?.map {
                    it.toSearchResponse(this)
                }

        // Sekarang kita hanya perlu mengembalikan TvSeriesLoadResponse
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, 
                document.resource?.seasons?.map { seasons ->
                (if (seasons.allEp.isNullOrEmpty()) (1..seasons.maxEp!!) else seasons.allEp.split(",")
                    .map { it.toInt() })
                    .map { episode ->
                        newEpisode(
                            LoadData(
                                id,
                                seasons.se,
                                episode,
                                subject.detailPath
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
        // Logika loadLinks tidak perlu diubah, karena ini hanya mengambil stream setelah load berhasil.
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
    
    // Data Classes... (tidak berubah)
    
    // ... (Semua Data Class: LoadData, Media, MediaDetail, Items) dipertahankan
    
    data class LoadData(/* ... */) // dipertahankan
    data class Media(/* ... */) // dipertahankan
    data class MediaDetail(/* ... */) // dipertahankan
    
    data class Items(
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("subjectType") val subjectType: Int? = null, // 2 = TvSeries/Drama
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

        // PERUBAHAN: Memaksa tipe ke TvSeries dan menambahkan filter internal
        fun toSearchResponse(provider: AdiDrakor): SearchResponse {
            // Jika konten lolos filter di pemanggil (MainPage/Search), maka itu adalah Drakor.
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

        data class Cover(/* ... */) // dipertahankan
        data class Trailer(/* ... */) // dipertahankan
    }
}
