// AdiOMDb/src/main/kotlin/com/AdiOMDb/AdiOMDb.kt

package com.AdiOMDb

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

class AdiOMDb : MainAPI() {
    // 1. Sumber Metadata (OMDb API)
    override var mainUrl = "https://www.omdbapi.com" 
    private val omdbApiKey = "8aabbe50" 
    
    // 2. Sumber Media (fmoviesunblocked.net) - Disesuaikan dengan URL dari Adimoviebox
    private val apiUrl = "https://fmoviesunblocked.net" // Tetap menggunakan fmoviesunblocked.net
    
    // Konfigurasi Cloudstream3
    override val instantLinkLoading = false 
    override var name = "AdiOMDb"
    override val hasMainPage = false 
    override val hasQuickSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.TvSeries, 
        TvType.Movie 
    )

    // --- FUNGSI UTAMA CLOUDSTREAM3 ---

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query) ?: emptyList()
    }
    
    // Fungsi Helper: Cari ID fmoviesunblocked.net menggunakan Judul OMDb
    private suspend fun findFmoviesID(query: String): String? {
        // Menggunakan endpoint dan struktur request yang sama dengan Adimoviebox
        val jsonBody = mapOf(
            "keyword" to query,
            "page" to "1",
            "perPage" to "1",
            "subjectType" to "0", 
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        return app.post(
            "$apiUrl/wefeed-h5-bff/web/subject/search", 
            requestBody = jsonBody
        ).parsedSafe<Media>()
         ?.data
         ?.items
         ?.firstOrNull() 
         ?.subjectId
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        // Panggil OMDb API - Hapus &type=series untuk mendukung Movie dan Series
        val encodedQuery = URLEncoder.encode(query, "utf-8") 
        
        val results = app.get(
            "$mainUrl/?s=$encodedQuery&apikey=$omdbApiKey" 
        ).parsedSafe<OmdbSearch>()?.Search
        
        return results?.mapNotNull { it.toSearchResponse(this) }
    }

    override suspend fun load(imdbID: String): LoadResponse { 
        
        val encodedImdbID = URLEncoder.encode(imdbID, "utf-8") 
        
        // 1. Ambil Detail Film/Serial dari OMDb
        val detail = app.get(
            "$mainUrl/?i=$encodedImdbID&plot=full&apikey=$omdbApiKey"
        ).parsedSafe<OmdbItemDetail>()
        
        val title = detail?.Title ?: throw ErrorLoadingException("Detail OMDb tidak ditemukan untuk ID: $imdbID")
        val isSeries = detail.Type.equals("series", ignoreCase = true)
        
        // 2. Cari ID unik Fmovies berdasarkan Judul OMDb
        // MENGGUNAKAN title dari OMDb (lebih akurat daripada hanya imdbID)
        val fmoviesID = findFmoviesID(title) 
             ?: throw ErrorLoadingException("ID Streaming Fmovies tidak ditemukan untuk $title")
        
        // 3. Persiapan Data Dasar
        val posterUrl = detail.Poster
        val year = detail.Year?.substringBefore("-")?.toIntOrNull()
        val plot = detail.Plot
        val tags = detail.Genre?.split(",")?.map { it.trim() }
        val score = Score.from10(detail.imdbRating?.toFloatOrNull())
        
        // --- LOGIKA UTAMA: MOVIE vs SERIES ---
        
        if (isSeries) {
            val episodes = mutableListOf<Episode>()
            val totalSeasons = detail.totalSeasons?.toIntOrNull() ?: 1

            for (season in 1..totalSeasons) {
                val seasonDetail = app.get(
                    "$mainUrl/?i=$encodedImdbID&Season=$season&apikey=$omdbApiKey" 
                ).parsedSafe<OmdbSeason>()
                
                seasonDetail?.Episodes?.forEach { ep ->
                    val episodeNum = ep.Episode?.toIntOrNull()
                    
                    // Gunakan fmoviesID sebagai streamingPath
                    val epData = EpisodeData(imdbID, season, episodeNum ?: 1, fmoviesID) 
                    
                    episodes.add(
                        newEpisode(epData.toJson()) { 
                            this.name = ep.Title
                            this.season = season
                            this.episode = episodeNum
                        }
                    )
                }
            }
            
            // Kembalikan LoadResponse untuk TV Series
            return newTvSeriesLoadResponse(title, imdbID, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
                this.tags = tags
                this.score = score
            }
        } else {
            // Movie
            val epData = EpisodeData(imdbID, 1, 1, fmoviesID) 

            // Kembalikan LoadResponse untuk Movie
            return newMovieLoadResponse(title, imdbID, TvType.Movie, epData.toJson()) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
                this.tags = tags
                this.score = score
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Menggunakan struktur LoadData yang sama dengan Adimoviebox, tapi dengan field berbeda
        val episodeData = parseJson<EpisodeData>(data)
        
        val fmoviesID = episodeData.streamingPath 
        val seasonNum = episodeData.seasonNum 
        val episodeNum = episodeData.episodeNum
        
        // Referer menggunakan fmoviesID sebagai detailPath-nya
        val referer = "$apiUrl/spa/videoPlayPage/movies/$fmoviesID?id=${episodeData.imdbID}&type=/movie/detail&lang=en"

        // 1. Ambil Link Streaming dari fmoviesunblocked.net
        val streams = app.get(
            "$apiUrl/wefeed-h5-bff/web/subject/play?subjectId=$fmoviesID&se=$seasonNum&ep=$episodeNum",
            referer = referer
        ).parsedSafe<Media>()?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.mapNotNull { source ->
            source.url?.let { url ->
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        url,
                        INFER_TYPE
                    ) {
                        this.referer = "$apiUrl/"
                        // Penanganan kualitas yang aman dari Adimoviebox
                        this.quality = source.resolutions?.let { getQualityFromName(it) } ?: Qualities.Unknown.value
                    }
                )
            }
        }

        // 2. Ambil Subtitle
        val firstStream = streams?.firstOrNull()
        val id = firstStream?.id
        val format = firstStream?.format

        if (id != null && format != null) {
            app.get(
                "$apiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=$fmoviesID",
                referer = referer
            ).parsedSafe<Media>()?.data?.captions?.mapNotNull { subtitle ->
                subtitle.url?.let { url ->
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            subtitle.lanName ?: "Unknown",
                            url
                        )
                    )
                }
            }
        }

        return true
    }
    
    // --- DATA CLASS UNTUK OMDb (Dipertahankan) ---
    
    data class EpisodeData(
        val imdbID: String, 
        val seasonNum: Int,
        val episodeNum: Int,
        val streamingPath: String // Ini menyimpan Fmovies ID (detailPath)
    )
    
    data class OmdbSearch(
        @JsonProperty("Search") val Search: List<OmdbItem>? = null
    )

    data class OmdbItem(
        @JsonProperty("Title") val Title: String? = null,
        @JsonProperty("Year") val Year: String? = null,
        @JsonProperty("imdbID") val imdbID: String? = null, 
        @JsonProperty("Type") val Type: String? = null,
        @JsonProperty("Poster") val Poster: String? = null,
    ) {
        fun toSearchResponse(provider: AdiOMDb): SearchResponse? {
            if (imdbID.isNullOrBlank() || Title.isNullOrBlank()) {
                return null
            }
            
            val type = if (Type.equals("series", ignoreCase = true)) TvType.TvSeries else TvType.Movie
            
            return provider.newMovieSearchResponse(
                Title,
                imdbID, 
                type,
                true 
            ) {
                this.posterUrl = Poster
                this.year = Year?.toIntOrNull()
            }
        }
    }

    data class OmdbItemDetail(
        @JsonProperty("Title") val Title: String? = null,
        @JsonProperty("Year") val Year: String? = null,
        @JsonProperty("Plot") val Plot: String? = null,
        @JsonProperty("Genre") val Genre: String? = null,
        @JsonProperty("imdbRating") val imdbRating: String? = null, 
        @JsonProperty("totalSeasons") val totalSeasons: String? = null,
        @JsonProperty("Poster") val Poster: String? = null,
        @JsonProperty("Type") val Type: String? = null // Wajib
    )

    data class OmdbSeason(
        @JsonProperty("Episodes") val Episodes: List<OmdbEpisode>? = null
    )

    data class OmdbEpisode(
        @JsonProperty("Title") val Title: String? = null,
        @JsonProperty("Episode") val Episode: String? = null
    )

    // --- DATA CLASS UNTUK Fmovies (Media) - Disesuaikan dari Adimoviebox ---
    
    data class Media(
        @JsonProperty("data") val data: Data? = null,
    ) {
        data class Data(
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
                @JsonProperty("lanName") val lanName: String? = null,
                @JsonProperty("url") val url: String? = null,
            )
        }
    }

    data class Items(
        @JsonProperty("subjectId") val subjectId: String? = null, 
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("cover") val cover: Cover? = null,
    ) {
        data class Cover(
            @JsonProperty("url") val url: String? = null,
        )
    }
}
