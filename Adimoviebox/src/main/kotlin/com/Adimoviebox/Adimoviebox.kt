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
    // URL Web Utama (Hanya untuk referensi/share)
    override var mainUrl = "https://moviebox.ph"
    
    // API LAMA (Gudang Data): Untuk Search, Detail, Playback
    private val apiUrl = "https://filmboom.top" 
    
    // API BARU (Etalase): Khusus untuk Halaman Depan / Kategori
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

    // --- KATEGORI (Sesuai Kode Lama) ---
    override val mainPage = mainPageOf(
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

    // ==========================================
    // 1. HOME PAGE (API JSON) -> Poster Muncul
    // ==========================================
    @Suppress("DEPRECATION")
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val id = request.data
        
        // Request ke API Baru (aoneroom) agar poster HD muncul
        val targetUrl = "$homeApiUrl/wefeed-h5api-bff/ranking-list/content?id=$id&page=$page&perPage=12"
        
        // Parsing JSON
        val response = app.get(targetUrl).parsedSafe<Media>()
        val data = response?.data
        
        // Ambil list film (bisa 'subjectList' atau 'items')
        val listFilm = data?.subjectList ?: data?.items

        if (listFilm.isNullOrEmpty()) throw ErrorLoadingException("Data Kosong")

        val home = listFilm.mapNotNull { item ->
            item.toSearchResponse(this)
        }

        return newHomePageResponse(request.name, home)
    }

    // ==========================================
    // 2. SEARCH (API POST) -> Pencarian Jalan
    // ==========================================
    @Suppress("DEPRECATION")
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/wefeed-h5-bff/web/subject/search"
        
        // Body JSON (Wajib format ini agar server merespon)
        val bodyMap = mapOf(
            "keyword" to query,
            "page" to "1",
            "perPage" to "20", // Ambil agak banyak
            "subjectType" to "0"
        )
        
        val requestBody = bodyMap.toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val response = app.post(url, requestBody = requestBody).parsedSafe<Media>()
        val items = response?.data?.items

        return items?.mapNotNull { it.toSearchResponse(this) } 
            ?: throw ErrorLoadingException("Pencarian tidak ditemukan.")
    }

    // ==========================================
    // 3. LOAD DETAIL (Trailer & Score Fixed)
    // ==========================================
    @Suppress("DEPRECATION")
    override suspend fun load(url: String): LoadResponse? {
        // Ambil ID dari URL (misal: .../detail/12345 -> 12345)
        val id = url.substringAfterLast("/")
        
        // Panggil API Detail
        val response = app.get("$apiUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id")
            .parsedSafe<MediaDetail>()?.data
        
        val subject = response?.subject ?: throw ErrorLoadingException("Detail Kosong")
        
        val title = subject.title ?: "No Title"
        val poster = subject.cover?.url
        val description = subject.description
        val year = subject.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tags = subject.genre?.split(",")?.map { it.trim() }
        
        // Trailer URL
        val trailerUrl = subject.trailer?.videoAddress?.url

        // Score (Rating)
        val ratingDouble = subject.imdbRatingValue?.toDoubleOrNull()
        
        // Rekomendasi
        val recommendations = app.get("$apiUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12")
            .parsedSafe<Media>()?.data?.items?.mapNotNull { it.toSearchResponse(this) }

        // Deteksi Tipe (Series / Movie)
        val isSeries = subject.subjectType == 2

        if (isSeries) {
            // Logika Series: Parsing Season & Episode
            val episodes = ArrayList<Episode>()
            response.resource?.seasons?.forEach { season ->
                val seasonNum = season.se ?: 1
                // Jika allEp kosong, generate manual dari 1..maxEp
                val epList = if (season.allEp.isNullOrEmpty()) {
                    (1..(season.maxEp ?: 0)).toList()
                } else {
                    season.allEp.split(",").mapNotNull { it.toIntOrNull() }
                }

                epList.forEach { epNum ->
                    // Data Load disimpan dalam JSON String agar rapi
                    val loadData = LoadData(id, seasonNum, epNum, subject.detailPath).toJson()
                    
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
                
                // Trailer
                if (!trailerUrl.isNullOrEmpty()) {
                    addTrailer(trailerUrl)
                }
                
                // Score
                if (ratingDouble != null) {
                    this.score = Score(average = ratingDouble)
                }
            }

        } else {
            // Logika Movie
            val loadData = LoadData(id, 0, 0, subject.detailPath).toJson()
            
            return newMovieLoadResponse(title, url, TvType.Movie, loadData) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                
                // Trailer
                if (!trailerUrl.isNullOrEmpty()) {
                    addTrailer(trailerUrl)
                }
                
                // Score
                if (ratingDouble != null) {
                    this.score = Score(average = ratingDouble)
                }
            }
        }
    }

    // ==========================================
    // 4. LOAD LINKS (Constructor Fixed)
    // ==========================================
    @Suppress("DEPRECATION")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        // Parse Data yang dikirim dari fungsi load()
        val media = parseJson<LoadData>(data)
        
        // Referer Wajib untuk Player
        val refererUrl = "$apiUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"

        // Panggil API Play
        val playUrl = "$apiUrl/wefeed-h5-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}"
        
        val response = app.get(playUrl, headers = mapOf("Referer" to refererUrl)).parsedSafe<Media>()
        val streams = response?.data?.streams

        streams?.forEach { source ->
            val videoUrl = source.url ?: return@forEach
            val qualityStr = source.resolutions ?: "Unknown"
            val qualityInt = getQualityFromName(qualityStr)
            
            val isM3u8 = videoUrl.contains(".m3u8")
            val type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO

            // FIX: Gunakan Constructor ExtractorLink
            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = "Adimoviebox $qualityStr",
                    url = videoUrl,
                    referer = "$apiUrl/", // Referer sesuai kode lama
                    quality = qualityInt,
                    type = type,
                    headers = mapOf("Referer" to "$apiUrl/")
                )
            )
        }

        // Subtitle (Caption)
        val firstStream = streams?.firstOrNull()
        if (firstStream != null) {
            val subUrl = "$apiUrl/wefeed-h5-bff/web/subject/caption?format=${firstStream.format}&id=${firstStream.id}&subjectId=${media.id}"
            
            app.get(subUrl, headers = mapOf("Referer" to refererUrl)).parsedSafe<Media>()?.data?.captions?.forEach { sub ->
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

// ==========================================
// DATA CLASSES (RESTORED FROM OLD CODE)
// ==========================================

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
        @JsonProperty("resource") val resource: Resource? = null,
    ) {
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
    @JsonProperty("subjectType") val subjectType: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null,
    @JsonProperty("genre") val genre: String? = null,
    @JsonProperty("cover") val cover: Cover? = null,
    @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
    @JsonProperty("trailer") val trailer: Trailer? = null,
    @JsonProperty("detailPath") val detailPath: String? = null,
) {
    // Fungsi Helper untuk Mapping ke SearchResponse
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        val url = "${provider.mainUrl}/detail/${subjectId}"
        val posterImage = cover?.url

        return provider.newMovieSearchResponse(
            title ?: "No Title",
            url,
            if (subjectType == 1) TvType.Movie else TvType.TvSeries
        ) {
            this.posterUrl = posterImage
            // Score aman
            val ratingD = imdbRatingValue?.toDoubleOrNull()
            if (ratingD != null) {
                this.score = Score(average = ratingD)
            }
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
