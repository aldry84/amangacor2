Package com.AdiDrakor

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
    override var mainUrl = "https://moviebox.ph" // URL Utama API tetap
    private val apiUrl = "https://fmoviesunblocked.net" // URL Tambahan API tetap
    
    override val instantLinkLoading = true
    override var name = "AdiDrakor" // Ganti Nama
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.TvSeries, // Fokus hanya pada Serial TV (Drama)
        TvType.AsianDrama // Tambahkan tipe ini sebagai fokus utama
    )

    // Fokuskan halaman utama hanya pada konten yang kemungkinan besar adalah Drama Korea (misalnya, TvSeries/AsianDrama)
    // Berdasarkan skema API Anda, 2 adalah untuk TvSeries/Drama. Saya akan memprioritaskan kategori ini.
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
        
        // Coba tambahkan "countryName" ke body jika API mendukung filtering di endpoint ini.
        // Jika tidak, API akan mengembalikan semua, dan filter dilakukan di 'toSearchResponse'.
        val body = mapOf(
            "channelId" to params.first(), // Tetap menggunakan ID TvSeries/Drama (2)
            "page" to page,
            "perPage" to "24",
            "sort" to params.last()
            // Menghapus 'countryName' dari body, karena filter akan dilakukan di sisi aplikasi untuk keakuratan.
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val home = app.post("$mainUrl/wefeed-h5-bff/web/filter", requestBody = body)
            .parsedSafe<Media>()?.data?.items
            // Filter hanya Drama/Serial yang berasal dari Korea (countryName="Korea") 
            // DAN dipastikan tipe-nya adalah Serial TV/Drama (subjectType=2), untuk menghilangkan Anime/Film non-Drakor.
            ?.filter { 
                it.countryName?.contains("Korea", ignoreCase = true) == true && it.subjectType == 2 
            } 
            ?.map {
                it.toSearchResponse(this)
            } ?: throw ErrorLoadingException("Tidak ada Data Drakor Ditemukan")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        // Quick Search akan menggunakan fungsi search yang sekarang dapat menemukan semua tipe
        return search(query) ?: emptyList()
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        // Untuk pencarian, kita akan mencari semua tipe konten (subjectType: "0")
        // agar film dan anime non-Drakor tetap dapat ditemukan, sesuai permintaan.
        val results = app.post(
            "$mainUrl/wefeed-h5-bff/web/subject/search", requestBody = mapOf(
                "keyword" to query,
                "page" to "1",
                "perPage" to "0",
                "subjectType" to "0", // Cari semua tipe (0) agar semua konten ditemukan
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items
            // TIDAK ADA FILTER di sini, agar semua hasil pencarian (Drakor, Movie, Anime) muncul.
            // Poin: "seandainya jika saya mencari film diluar drakor filmnya tetap dapat ditemukan."
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
        
        // Tentukan tipe, ini akan digunakan untuk 'LoadResponse'
        val isDrakorOrTvSeries = subject?.subjectType == 2
        val isKorean = subject?.countryName?.contains("Korea", ignoreCase = true) == true
        
        // Jika ini adalah Serial TV (2) ATAU berlabel Korea, kita anggap itu adalah TvSeries/AsianDrama (untuk link load)
        // Jika ini adalah tipe lain (Movie/Anime) DAN BUKAN Korea, kita anggap itu adalah Movie
        val tvType = if (isDrakorOrTvSeries || isKorean) TvType.TvSeries else TvType.Movie 
        
        // **LOGIC GUARD RAIL:**
        // Jika tujuannya HANYA Drakor, kita bisa membatasi:
        // if (!isDrakorOrTvSeries || !isKorean) throw ErrorLoadingException("Konten bukan Drama Korea.")
        // Namun, karena pencarian (search) sekarang memungkinkan semua tipe, kita harus mengizinkan Load Movie juga.
        
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
                // Filter rekomendasi untuk Drama Korea juga (hanya yang subjectType=2 DAN countryName="Korea")
                ?.filter { it.countryName?.contains("Korea", ignoreCase = true) == true && it.subjectType == 2 }
                ?.map {
                    it.toSearchResponse(this)
                }
        
        // LOGIC SPLIT:
        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, // Dipaksa ke TvSeries karena fokus Drakor
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
        } else {
            // Mengembalikan MovieLoadResponse untuk konten non-Serial TV/Drakor (misalnya Movie non-Korea)
            newMovieLoadResponse(title, url, TvType.Movie, url) {
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
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        // Ganti URL referer ke fmoviesunblocked.net
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
            // Sudah diperbaiki: Mengganti konstruktor SubtitleFile yang deprecated dengan newSubtitleFile
            subtitleCallback.invoke(
                newSubtitleFile( // Perubahan di sini
                    subtitle.lanName ?: "",
                    subtitle.url ?: return@map
                )
            )
        }

        return true
    }
    
    // Semua Data Class dipertahankan, karena struktur respons API tidak berubah.
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

        fun toSearchResponse(provider: AdiDrakor): SearchResponse {
            // Tentukan tipe yang paling sesuai: TvSeries jika SubjectType=2 atau negara=Korea, selain itu Movie.
            val type = when {
                subjectType == 2 -> TvType.TvSeries
                countryName?.contains("Korea", ignoreCase = true) == true -> TvType.TvSeries
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
