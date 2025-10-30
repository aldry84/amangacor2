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
    // Gunakan 'tt' sebagai fallback untuk IMDB ID jika diperlukan
    override var mainUrl = "https://www.omdbapi.com" 
    private val omdbApiKey = "8aabbe50" 
    
    // 2. Sumber Media (fmoviesunblocked.net)
    private val apiUrl = "https://fmoviesunblocked.net" 
    
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
        // Panggil OMDb API untuk mencari judul (tanpa batasan tipe)
        // URLEncoder.encode sangat penting untuk query pencarian
        val encodedQuery = URLEncoder.encode(query, "utf-8") 
        
        val results = app.get(
            "$mainUrl/?s=$encodedQuery&apikey=$omdbApiKey" // Menghapus &type=series
        ).parsedSafe<OmdbSearch>()?.Search
        
        // Filter out items that couldn't be converted (missing ID/Title)
        return results?.mapNotNull { it.toSearchResponse(this) }
    }

    override suspend fun load(imdbID: String): LoadResponse { 
        
        // OMDb ID tidak perlu di-encode jika murni ttXXXXXX, tapi tidak masalah jika di-encode
        val encodedImdbID = URLEncoder.encode(imdbID, "utf-8") 
        
        // 1. Ambil Detail Film/Serial dari OMDb
        val detail = app.get(
            "$mainUrl/?i=$encodedImdbID&plot=full&apikey=$omdbApiKey"
        ).parsedSafe<OmdbItemDetail>()
        
        // Validasi Detail
        val title = detail?.Title ?: throw ErrorLoadingException("Detail OMDb tidak ditemukan untuk ID: $imdbID")
        val isSeries = detail.Type.equals("series", ignoreCase = true)
        
        // 2. Cari ID unik Fmovies berdasarkan Judul OMDb
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

            // Loop hanya jika ini adalah Series
            for (season in 1..totalSeasons) {
                val seasonDetail = app.get(
                    "$mainUrl/?i=$encodedImdbID&Season=$season&apikey=$omdbApiKey" 
                ).parsedSafe<OmdbSeason>()
                
                seasonDetail?.Episodes?.forEach { ep ->
                    val episodeNum = ep.Episode?.toIntOrNull()
                    
                    // Simpan Fmovies ID sebagai ID streaming unik di EpisodeData
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
            // Ini adalah Movie (atau tipe lain seperti Game/Episode yang akan diperlakukan sebagai Movie)
            // Simpan Fmovies ID di EpisodeData
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
        // Menggunakan parseJsonSafe untuk penanganan error yang lebih baik
        val episodeData = parseJson<EpisodeData>(data)
        
        val fmoviesID = episodeData.streamingPath 
        
        // 'se' dan 'ep' akan tetap 1 jika itu Movie.
        val seasonNum = episodeData.seasonNum 
        val episodeNum = episodeData.episodeNum
        
        val referer = "$apiUrl/spa/videoPlayPage/movies/$fmoviesID?id=$fmoviesID&type=/movie/detail&lang=en"

        // 1. Ambil Link Streaming dari fmoviesunblocked.net
        val streams = app.get(
            "$apiUrl/wefeed-h5-bff/web/subject/play?subjectId=$fmoviesID&se=$seasonNum&ep=$episodeNum",
            referer = referer
        ).parsedSafe<Media>()?.data?.streams

        // Pastikan streams tidak null sebelum mapping
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
                        // Memanggil getQualityFromName dengan null-safety
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
                // Pastikan URL Subtitle tidak null
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

    // --- DATA CLASS UNTUK OMDb ---
    
    data class EpisodeData(
        val imdbID: String, 
        val seasonNum: Int, // Akan menjadi 1 untuk Movie
        val episodeNum: Int, // Akan menjadi 1 untuk Movie
        val streamingPath: String // Ini menyimpan Fmovies ID
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
        // Mengembalikan SearchResponse? untuk menangani null ID/Title
        fun toSearchResponse(provider: AdiOMDb): SearchResponse? {
            // Memastikan ID dan Title ada
            if (imdbID.isNullOrBlank() || Title.isNullOrBlank()) {
                return null
            }
            
            // Tentukan tipe
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
        @JsonProperty("Type") val Type: String? = null // Menambahkan Type untuk membedakan Movie/Series
    )

    data class OmdbSeason(
        @JsonProperty("Episodes") val Episodes: List<OmdbEpisode>? = null
    )

    data class OmdbEpisode(
        @JsonProperty("Title") val Title: String? = null,
        @JsonProperty("Episode") val Episode: String? = null
    )

    // --- DATA CLASS UNTUK Fmovies (Media) ---
    
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
