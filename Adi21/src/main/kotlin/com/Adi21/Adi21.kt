// File: Adi21.kt

package com.Adi21

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.toScoreInt

// --- KONSTANTA YANG DIPERBARUI ---
class Adi21 : MainAPI() {
    // TMDB sebagai Main URL untuk Metadata
    override var mainUrl = "https://api.themoviedb.org"
    override var name = "Adi21 TMDB Source"
    override val instantLinkLoading = true
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "en" // Tetap 'en' karena TMDB API
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )
    
    // API KEY TMDB Anda
    private val TMDB_API_KEY = "1cfadd9dbfc534abf6de40e1e7eaf4c7"
    // URL dasar vidsrc.cc (digunakan di loadLinks)
    private val VIDSRC_URL = "https://vidsrc.cc"

    // URL TMDB API Helper
    private fun tmdbApi(path: String, page: Int = 1) = 
        "$mainUrl/3/$path?api_key=$TMDB_API_KEY&page=$page"
        
    // URL Gambar Poster TMDB
    private fun String?.toPosterUrl(): String? {
        return if (this != null) "https://image.tmdb.org/t/p/w500$this" else null
    }

    // --- MAIN PAGE (Mengganti Struktur moviebox.ph) ---
    override val mainPage: List<MainPageData> = mainPageOf(
        "trending/movie/day" to "Movies Trending Today",
        "trending/tv/day" to "TV Series Trending Today",
        "movie/popular" to "Popular Movies",
        "tv/popular" to "Popular TV Series",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val url = tmdbApi(request.data, page)
        
        val response = app.get(url).parsed<TmdbApiResult>()
        
        val mediaList = response.results.mapNotNull { item ->
            // Menghindari item tanpa ID
            val type = if (item.mediaType == "movie" || request.data.contains("movie")) TvType.Movie else TvType.TvSeries
            
            // Mengubah objek TMDB menjadi format SearchResponse CloudStream
            newTvSeriesSearchResponse(item.title ?: item.name ?: return@mapNotNull null, item.tmdbId.toString(), type) {
                // Menyimpan ID TMDB di 'url' untuk digunakan di fungsi load()
                posterUrl = item.posterPath.toPosterUrl()
                rating = item.voteAverage?.times(10)?.toInt()
                set  = "TMDB" // Sumber
            }
        }

        return newHomePageResponse(request.name, mediaList)
    }

    // --- SEARCH (Menggunakan Endpoint Pencarian TMDB) ---
    override suspend fun search(query: String): List<SearchResponse> {
        val url = tmdbApi("search/multi") + "&query=$query"
        
        return app.get(url).parsed<TmdbApiResult>().results
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            .mapNotNull { item ->
            val type = if (item.mediaType == "movie") TvType.Movie else TvType.TvSeries
            newTvSeriesSearchResponse(item.title ?: item.name ?: return@mapNotNull null, item.tmdbId.toString(), type) {
                posterUrl = item.posterPath.toPosterUrl()
                rating = item.voteAverage?.times(10)?.toInt()
            }
        }
    }

    // --- LOAD DETAIL (Menggunakan ID TMDB) ---
    override suspend fun load(url: String): LoadResponse {
        val tmdbId = url // TMDB ID disimpan di URL
        val typePath = if (tmdbId.contains("-S")) "tv" else "movie" // Asumsi: Kita hanya menyimpan ID numerik di search
        
        val detailUrl = tmdbApi("$typePath/$tmdbId")
        val response = app.get(detailUrl).parsedSafe<TmdbDetail>()?.data ?: throw ErrorLoadingException("Failed to load TMDB detail")
        
        val title = response.title ?: response.name ?: ""
        val poster = response.posterPath.toPosterUrl()
        val tags = response.genres?.mapNotNull { it.name }
        val year = response.releaseDate?.substringBefore("-")?.toIntOrNull() ?: response.firstAirDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (typePath == "tv") TvType.TvSeries else TvType.Movie
        val description = response.overview
        val score = response.voteAverage?.toScoreInt()
        
        // TMDB tidak menyediakan tautan trailer langsung, jadi kita harus melakukan permintaan lain
        val trailerResponse = app.get(tmdbApi("$typePath/$tmdbId/videos")).parsedSafe<TmdbVideos>()
        val trailer = trailerResponse?.results?.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }?.key
        
        // TMDB tidak memberikan daftar cast di endpoint /detail, butuh endpoint terpisah.
        
        val tmdbLink = LoadData(tmdbId, tvType == TvType.TvSeries).toJson()

        if (tvType == TvType.TvSeries) {
            val episodes = response.seasons?.flatMapIndexed { seasonIndex, season ->
                // Jika musim 0 (Specials) atau tanpa episode, lewati
                if (season.seasonNumber == 0 || season.episodeCount == 0) return@flatMapIndexed emptyList()
                
                // Membuat episode placeholder. Detail episode akan dimuat saat loadLinks.
                (1..season.episodeCount).map { episodeNum ->
                    newEpisode(tmdbLink) {
                        this.name = "S${season.seasonNumber}E$episodeNum"
                        this.season = season.seasonNumber
                        this.episode = episodeNum
                    }
                }
            } ?: emptyList()

            return newTvSeriesLoadResponse(title, tmdbId, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                // this.actors = ... (perlu permintaan cast terpisah)
                if (trailer != null) addTrailer("https://www.youtube.com/watch?v=$trailer", addRaw = true)
            }
        } else {
            return newMovieLoadResponse(title, tmdbId, TvType.Movie, tmdbLink) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                if (trailer != null) addTrailer("https://www.youtube.com/watch?v=$trailer", addRaw = true)
            }
        }
    }

    // --- LOAD LINKS (Menggunakan vidsrc.cc) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        val tmdbId = media.id ?: return false
        val isTv = media.isTv ?: return false
        
        val typePath = if (isTv) "tv" else "movie"
        val seasonNum = media.season ?: 0
        val episodeNum = media.episode ?: 0

        // Format URL vidsrc.cc yang menggunakan ID TMDB
        // vidsrc.cc sering menggunakan format 'TMDB ID' atau 'TMDB ID/Season/Episode'
        val urlEmbed = if (isTv) {
            "$VIDSRC_URL/v2/embed/$typePath/$tmdbId/$seasonNum/$episodeNum"
        } else {
            "$VIDSRC_URL/v2/embed/$typePath/$tmdbId"
        }
        
        // Kita menggunakan Adi21Extractor untuk memproses URL embed vidsrc.cc
        // Karena vidsrc.cc adalah sumber yang kompleks, kita menggunakan extractor eksternal.
        Adi21Extractor(VIDSRC_URL).get(urlEmbed, callback, subtitleCallback)
        
        return true
    }
}

// --- EXTRACTOR (Harus menangani konten dari URL embed vidsrc.cc) ---
// Catatan: Ini adalah kerangka. Logika parsing konten vidsrc.cc harus diimplementasikan
class Adi21Extractor(override val mainUrl: String) : ExtractorLink() {
    override val name = "Adi21-vidsrc"
    override val requiresReferer = false

    override suspend fun get(
        url: String, 
        callback: (ExtractorLink) -> Unit, 
        subtitleCallback: (SubtitleFile) -> Unit
    ): Boolean {
        // Contoh langkah untuk vidsrc.cc:
        // 1. Ambil konten dari URL embed vidsrc.cc.
        val response = app.get(url).text
        
        // 2. Cari iframe atau kode JS yang berisi URL video akhir (M3U8, MP4, dll.).
        //    Ini sering membutuhkan Regex atau parsing JS yang kompleks.
        
        // Placeholder untuk hasil (Anda harus menggantinya dengan logika nyata)
        callback.invoke(
            newExtractorLink(
                this.name,
                "Video dari vidsrc.cc",
                "https://example.com/placeholder_video.m3u8", // Ganti dengan URL video nyata
                url, // referer
                getQualityFromName("720p") // Ganti dengan kualitas yang terdeteksi
            )
        )
        
        // Placeholder untuk subtitel (berdasarkan Gambar 2 yang Anda berikan sebelumnya)
        // Jika Anda dapat mengekstrak URL .vtt/.srt/.json subtitel, panggil:
        // subtitleCallback.invoke(SubtitleFile("English", "https://example.com/subs.vtt"))
        
        return true
    }
}

// --- DATA CLASS TMDB BARU ---

// Hasil Pencarian/Homepage
data class TmdbApiResult(
    @JsonProperty("results") val results: List<TmdbItem>
)

data class TmdbItem(
    @JsonProperty("id") val tmdbId: Int,
    @JsonProperty("media_type") val mediaType: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
)

// Detail Film/Serial
data class TmdbDetail(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("genres") val genres: List<Genre>? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("seasons") val seasons: List<Season>? = null,
) {
    data class Genre(@JsonProperty("name") val name: String? = null)
    data class Season(
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("episode_count") val episodeCount: Int = 0, // Default 0
    )
    val data: TmdbDetail
        get() = this
}

// Data Video/Trailer TMDB
data class TmdbVideos(
    @JsonProperty("results") val results: List<VideoItem>? = null
) {
    data class VideoItem(
        @JsonProperty("site") val site: String? = null, // e.g., "YouTube"
        @JsonProperty("type") val type: String? = null, // e.g., "Trailer"
        @JsonProperty("key") val key: String? = null // Video ID
    )
}

// Data Class untuk menyimpan ID TMDB yang akan di-parse ke loadLinks
data class LoadData(
    val id: String? = null, // ID TMDB
    val isTv: Boolean? = null,
    val season: Int? = null,
    val episode: Int? = null,
)
