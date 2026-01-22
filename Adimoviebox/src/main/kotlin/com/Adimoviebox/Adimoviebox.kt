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
    
    // API UTAMA (Aoneroom)
    private val apiUrl = "https://api.aoneroom.com" 

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

    // --- REVOLUSI HOME PAGE ---
    // Kita ganti ID angka (yang bikin error) dengan Kata Kunci Pencarian.
    // Ini memanfaatkan fitur Search yang sudah sukses kita buat.
    override val mainPage: List<MainPageData> = mainPageOf(
        "Indonesian" to "Indonesian Movies",
        "Drama" to "Drama Pilihan",
        "Romance" to "Romantis",
        "Action" to "Action",
        "Horror" to "Horror",
        "Korea" to "Korean Drama",
        "China" to "Chinese Drama",
        "Philippines" to "Pinoy Movies",
        "Adult" to "Dewasa (18+)" // Menambahkan kategori pedas
    )

    // --- LOGIKA HOME PAGE BARU ---
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val query = request.data // Ini akan berisi kata kunci dari list di atas (misal: "Indonesian")
        
        // Kita gunakan fungsi search() yang sudah terbukti berhasil
        // untuk mengisi halaman depan.
        val searchResults = search(query)

        if (searchResults.isEmpty()) {
            throw ErrorLoadingException("Kategori kosong")
        }

        return newHomePageResponse(request.name, searchResults)
    }

    // --- SEARCH LOGIC (TETAP SAMA KARENA SUDAH BERHASIL) ---
    override suspend fun search(query: String): List<SearchResponse> {
        val postData = mapOf(
            "keyword" to query,
            "page" to 1,
            "perPage" to 20
        )

        return app.post(
            "$apiUrl/wefeed-h5-bff/web/subject/search", 
            requestBody = postData.toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }
            ?: emptyList() // Ubah throw error jadi emptyList agar Home Page tidak crash kalau 1 kategori kosong
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // --- LOAD LOGIC (TETAP SAMA - SUDAH FIX) ---
    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        
        val document = app.get("$apiUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id")
            .parsedSafe<MediaDetail>()?.data
        
        // Hapus tanda tanya (?) yang bikin warning, tapi fungsinya tetap aman
        val subject = document?.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }
        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        val score = Score.from10(subject?.imdbRatingValue) 

        val recommendations = app.get("$apiUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12")
                .parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }

        val isSeries = subject?.subjectType == 2 
        
        val commonLoadData = LoadData(id, detailPath = subject?.detailPath)

        if (isSeries) {
            val episodes = document?.resource?.seasons?.flatMap { season ->
                val epList = if (season.allEp.isNullOrEmpty()) {
                    (1..(season.maxEp ?: 1)).map { it }
                } else {
                    season.allEp.split(",").mapNotNull { it.trim().toIntOrNull() }
                }
                
                epList.map { epNum ->
                    newEpisode(
                        commonLoadData.copy(season = season.se, episode = epNum).toJson()
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
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, commonLoadData.toJson()) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    // --- LINK HANDLING (TETAP SAMA - SUDAH FIX SPOOFING LOK-LOK) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        
        // Referer Sakti Lok-Lok
        val fakeReferer = "https://lok-lok.cc/" 

        val playUrl = "$apiUrl/wefeed-h5-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detailPath=${media.detailPath}"
        
        val response = app.get(playUrl, referer = fakeReferer).parsedSafe<Media>()
        val streams = response?.data?.streams

        if (!streams.isNullOrEmpty()) {
            streams.reversed().distinctBy { it.url }.forEach { source ->
                val url = source.url ?: return@forEach
                
                callback.invoke(
                    newExtractorLink(
                        this.name, 
                        "Aoneroom/LokLok ${source.resolutions ?: "HD"}", 
                        url, 
                        ExtractorLinkType.VIDEO 
                    ) {
                        this.referer = fakeReferer
                        this.quality = getQualityFromName(source.resolutions)
                    }
                )
            }
        }

        val id = streams?.firstOrNull()?.id
        val format = streams?.firstOrNull()?.format
        if (id != null && format != null) {
            app.get("$apiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=${media.id}&detailPath=${media.detailPath}", referer = fakeReferer)
                .parsedSafe<Media>()?.data?.captions?.forEach { subtitle ->
                    subtitleCallback.invoke(newSubtitleFile(subtitle.lanName ?: "Unknown", subtitle.url ?: return@forEach))
                }
        }

        return true
    }
}

// --- DATA CLASSES ---
data class LoadData(val id: String? = null, val season: Int? = null, val episode: Int? = null, val detailPath: String? = null)

data class Media(@field:JsonProperty("data") val data: Data? = null) {
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

data class MediaDetail(@field:JsonProperty("data") val data: Data? = null) {
    data class Data(
        @field:JsonProperty("subject") val subject: Items? = null,
        @field:JsonProperty("stars") val stars: ArrayList<Stars>? = arrayListOf(),
        @field:JsonProperty("resource") val resource: Resource? = null,
    ) {
        data class Stars(@field:JsonProperty("name") val name: String? = null, @field:JsonProperty("character") val character: String? = null, @field:JsonProperty("avatarUrl") val avatarUrl: String? = null)
        data class Resource(@field:JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf()) {
            data class Seasons(@field:JsonProperty("se") val se: Int? = null, @field:JsonProperty("maxEp") val maxEp: Int? = null, @field:JsonProperty("allEp") val allEp: String? = null)
        }
    }
}

data class Items(
    @field:JsonProperty("subjectId") val subjectId: String? = null,
    @field:JsonProperty("subjectType") val subjectType: Int? = null,
    @field:JsonProperty("title") val title: String? = null,
    @field:JsonProperty("description") val description: String? = null,
    @field:JsonProperty("releaseDate") val releaseDate: String? = null,
    @field:JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
    @field:JsonProperty("genre") val genre: String? = null,
    @field:JsonProperty("cover") val cover: Cover? = null,
    @field:JsonProperty("trailer") val trailer: Trailer? = null,
    @field:JsonProperty("detailPath") val detailPath: String? = null,
) {
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        val url = "${provider.mainUrl}/detail/${subjectId}"
        return provider.newMovieSearchResponse(title ?: "No Title", url, if (subjectType == 1) TvType.Movie else TvType.TvSeries, false) {
            this.posterUrl = cover?.url
            this.score = Score.from10(imdbRatingValue)
            this.year = releaseDate?.substringBefore("-")?.toIntOrNull()
        }
    }
    data class Cover(@field:JsonProperty("url") val url: String? = null)
    data class Trailer(@field:JsonProperty("videoAddress") val videoAddress: VideoAddress? = null) {
        data class VideoAddress(@field:JsonProperty("url") val url: String? = null)
    }
}
