package com.Adicinema

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import java.net.URLEncoder // <-- Tambahkan Impor ini
import java.nio.charset.StandardCharsets

// Fungsi Extension untuk URL Encoding (Menggantikan .toUrl())
fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())

class Adicinema : MainAPI() {
    override var mainUrl = "https://api.themoviedb.org/3"
    override var name = "AdiCinema"
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    private val apiKey = "1d8730d33fc13ccbd8cdaaadb74892c7"
    
    override val hasQuickSearch = true

    // 🔍 Search film & series
    override suspend fun search(query: String): List<SearchResponse> {
        // Perbaikan: Menggunakan query.urlEncode()
        val url = "$mainUrl/search/multi?api_key=$apiKey&language=id-ID&query=${query.urlEncode()}"
        val res = app.get(url).parsedSafe<TmdbSearch>() ?: return emptyList()

        return res.results?.mapNotNull { item ->
            val id = item.id ?: return@mapNotNull null
            val poster = item.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
            val type = when (item.media_type) {
                "movie" -> TvType.Movie
                "tv" -> TvType.TvSeries
                else -> TvType.Movie
            }

            newMovieSearchResponse(item.title ?: item.name ?: "", id.toString(), type) {
                this.posterUrl = poster
                this.year = item.release_date?.take(4)?.toIntOrNull()
            }
        } ?: emptyList()
    }

    // 📄 Load detail (movie atau TV)
    override suspend fun load(url: String): LoadResponse? {
        val id = url.toIntOrNull() ?: return null

        val tvDetailUrl = "$mainUrl/tv/$id?api_key=$apiKey&language=id-ID&append_to_response=videos"
        val movieDetailUrl = "$mainUrl/movie/$id?api_key=$apiKey&language=id-ID&append_to_response=videos"

        val movieRes = app.get(movieDetailUrl).parsedSafe<TmdbDetail>()
        val tvRes = app.get(tvDetailUrl).parsedSafe<TmdbDetail>()

        val res = movieRes ?: tvRes ?: return null
        val isTv = tvRes != null

        val poster = res.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
        val trailer = res.videos?.results?.firstOrNull { it.site == "YouTube" }?.key

        if (!isTv) {
            return newMovieLoadResponse(res.title ?: "Unknown", url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = res.overview
                this.year = res.release_date?.take(4)?.toIntOrNull()
                addTrailer("https://www.youtube.com/watch?v=$trailer")
            }
        } else {
            val episodes = mutableListOf<Episode>()
            val seasonCount = res.number_of_seasons ?: 1
            for (season in 1..seasonCount) {
                val seasonUrl = "$mainUrl/tv/$id/season/$season?api_key=$apiKey&language=id-ID"
                val seasonRes = app.get(seasonUrl).parsedSafe<TmdbSeason>() ?: continue

                seasonRes.episodes?.forEach { ep ->
                    val epPoster = ep.still_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                    
                    episodes.add(
                        newEpisode(
                            data = "$id|$season|${ep.episode_number}"
                        ) {
                            this.name = ep.name
                            this.season = season
                            this.episode = ep.episode_number
                            this.posterUrl = epPoster
                            this.description = ep.overview
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(res.name ?: "Unknown", url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = res.overview
                addTrailer("https://www.youtube.com/watch?v=$trailer")
            }
        }
    }

    // 🎥 Load player VidSrc.to (movie atau TV)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("|")
        val id = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val season = parts.getOrNull(1)?.toIntOrNull()
        val episode = parts.getOrNull(2)?.toIntOrNull()

        // Ambil IMDB ID
        val imdbUrlMovie = "$mainUrl/movie/$id/external_ids?api_key=$apiKey"
        val imdbUrlTv = "$mainUrl/tv/$id/external_ids?api_key=$apiKey"

        val imdbMovie = app.get(imdbUrlMovie).parsedSafe<TmdbExternalIds>()?.imdb_id
        val imdbTv = app.get(imdbUrlTv).parsedSafe<TmdbExternalIds>()?.imdb_id
        
        // Perbaikan: Tidak ada perubahan pada baris ini, tapi masalah val reassignment telah diatasi
        // dengan penambahan fungsi di luar kelas utama
        val imdb = imdbMovie ?: imdbTv ?: return false 

        val vidsrcUrl = if (season != null && episode != null) {
            // TV Series
            "https://vidsrc.to/embed/tv/$imdb/$season/$episode"
        } else {
            // Movie
            "https://vidsrc.to/embed/movie/$imdb"
        }

        callback.invoke(
            newExtractorLink(
                this.name,
                "VidSrc",
                vidsrcUrl,
                INFER_TYPE 
            ) {
                this.referer = "https://vidsrc.to/"
                this.quality = Qualities.Unknown.value
                this.isM3u8 = false
            }
        )
        return true
    }

    // --- Data classes ---
    data class TmdbSearch(val results: List<TmdbItem>?)
    data class TmdbItem(
        val id: Int?,
        val title: String?,
        val name: String?,
        val poster_path: String?,
        val media_type: String?,
        val release_date: String?
    )

    data class TmdbDetail(
        val id: Int?,
        val title: String?,
        val name: String?,
        val overview: String?,
        val poster_path: String?,
        val release_date: String?,
        val videos: TmdbVideos?,
        val number_of_seasons: Int?
    )

    data class TmdbVideos(val results: List<TmdbVideo>?)
    data class TmdbVideo(val key: String?, val site: String?)
    data class TmdbSeason(val episodes: List<TmdbEpisode>?)
    data class TmdbEpisode(
        val episode_number: Int,
        val name: String?,
        val overview: String?,
        val still_path: String?
    )
    data class TmdbExternalIds(val imdb_id: String?)
}
