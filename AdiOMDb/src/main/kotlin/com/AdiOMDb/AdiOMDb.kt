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

class AdiOMDb : MainAPI() {
    // 1. Sumber Metadata (OMDb API)
    override var mainUrl = "https://www.omdbapi.com" 
    private val omdbApiKey = "8aabbe50" 
    
    // 2. Sumber Media (fmoviesunblocked.net)
    private val apiUrl = "https://fmoviesunblocked.net" 
    
    // Konfigurasi Cloudstream3
    override val instantLinkLoading = false 
    override var name = "AdiOMDb"
    override val hasMainPage = false // Kita fokus pada Search karena OMDb tidak punya Main Page
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
        // Panggil endpoint search fmoviesunblocked.net 
        val result = app.post(
            "$apiUrl/wefeed-h5-bff/web/subject/search", requestBody = mapOf(
                "keyword" to query,
                "page" to "1",
                "perPage" to "1",
                "subjectType" to "0", // Cari semua tipe
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items?.firstOrNull() 
        
        // Kembalikan subjectId (ID fmoviesunblocked.net)
        return result?.subjectId
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        // Panggil OMDb API untuk mencari judul
        val results = app.get(
            // Mencari Series dan Movie
            "$mainUrl/?s=${query.urlEncode()}&apikey=$omdbApiKey&type=series" 
        ).parsedSafe<OmdbSearch>()?.Search
        
        // Mapping hasil ke SearchResponse
        return results?.map { it.toSearchResponse(this) }
    }

    override suspend fun load(imdbID: String): LoadResponse { // imdbID adalah 'url' dari SearchResponse
        
        // 1. Ambil Detail Film/Serial dari OMDb
        val detail = app.get(
            "$mainUrl/?i=${imdbID.urlEncode()}&plot=full&apikey=$omdbApiKey"
        ).parsedSafe<OmdbItemDetail>()

        // 2. Cari ID unik Fmovies berdasarkan Judul OMDb
        val fmoviesID = findFmoviesID(detail?.Title ?: imdbID) 
             ?: throw ErrorLoadingException("ID Streaming Fmovies tidak ditemukan untuk ${detail?.Title}")
        
        val totalSeasons = detail?.totalSeasons?.toIntOrNull() ?: 1
        val episodes = mutableListOf<Episode>()

        // 3. Loop untuk Mengambil Semua Season dan Episode dari OMDb
        for (season in 1..totalSeasons) {
            val seasonDetail = app.get(
                "$mainUrl/?i=${imdbID.urlEncode()}&Season=$season&apikey=$omdbApiKey"
            ).parsedSafe<OmdbSeason>()
            
            seasonDetail?.Episodes?.forEach { ep ->
                val episodeNum = ep.Episode?.toIntOrNull()
                
                // Simpan Fmovies ID sebagai ID streaming unik di EpisodeData
                val epData = EpisodeData(imdbID, season, episodeNum ?: 1, fmoviesID) 
                
                episodes.add(
                    newEpisode(epData.toJson()) { // Simpan EpisodeData sebagai data unik
                        this.name = ep.Title
                        this.season = season
                        this.episode = episodeNum
                    }
                )
            }
        }
        
        // 4. Kembalikan LoadResponse
        return newTvSeriesLoadResponse(detail?.Title ?: "Unknown", imdbID, TvType.TvSeries, episodes) {
            this.posterUrl = detail?.Poster
            this.year = detail?.Year?.substringBefore("-")?.toIntOrNull()
            this.plot = detail?.Plot
            this.tags = detail?.Genre?.split(",")?.map { it.trim() }
            this.score = Score.from10(detail?.imdbRating?.toFloatOrNull()) 
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val episodeData = parseJson<EpisodeData>(data)
        
        // Fmovies ID adalah ID streaming unik yang kita butuhkan
        val fmoviesID = episodeData.streamingPath 
        
        // URL referer untuk otorisasi (menggunakan fmoviesunblocked.net)
        val referer = "$apiUrl/spa/videoPlayPage/movies/$fmoviesID?id=$fmoviesID&type=/movie/detail&lang=en"

        // 1. Ambil Link Streaming dari fmoviesunblocked.net
        val streams = app.get(
            "$apiUrl/wefeed-h5-bff/web/subject/play?subjectId=$fmoviesID&se=${episodeData.seasonNum}&ep=${episodeData.episodeNum}",
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

        // 2. Ambil Subtitle
        val id = streams?.first()?.id
        val format = streams?.first()?.format

        if (id != null && format != null) {
            app.get(
                "$apiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=$fmoviesID",
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

    // --- DATA CLASS UNTUK OMDb ---
    
    data class EpisodeData(
        val imdbID: String, 
        val seasonNum: Int,
        val episodeNum: Int,
        val streamingPath: String // Ini menyimpan Fmovies ID
    )
    
    data class OmdbSearch(
        @JsonProperty("Search") val Search: List<OmdbItem>? = null
    )

    data class OmdbItem(
        @JsonProperty("Title") val Title: String? = null,
        @JsonProperty("Year") val Year: String? = null,
        @JsonProperty("imdbID") val imdbID: String? = null, // Digunakan sebagai ID utama
        @JsonProperty("Type") val Type: String? = null,
        @JsonProperty("Poster") val Poster: String? = null,
    ) {
        fun toSearchResponse(provider: AdiOMDb): SearchResponse {
            val type = if (Type.equals("series", ignoreCase = true)) TvType.TvSeries else TvType.Movie
            return provider.newMovieSearchResponse(
                Title ?: "Tidak Ada Judul",
                imdbID ?: throw ErrorLoadingException("IMDb ID tidak ditemukan."), 
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
        @JsonProperty("Poster") val Poster: String? = null
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
            @JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(), // Untuk FindFmoviesID
            @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(), // Untuk loadLinks
            @JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf(), // Untuk loadLinks
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
        @JsonProperty("subjectId") val subjectId: String? = null, // ID yang kita butuhkan
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("cover") val cover: Cover? = null,
    ) {
        data class Cover(
            @JsonProperty("url") val url: String? = null,
        )
    }
}
