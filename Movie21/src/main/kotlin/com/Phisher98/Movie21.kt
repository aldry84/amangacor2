package com.movie21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import java.net.URLEncoder

class Movie21 : MainAPI() {
    override var mainUrl = "https://api.themoviedb.org/3"
    override var name = "Movie21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val tmdbKey = "1cfadd9dbfc534abf6de40e1e7eaf4c7"
    private val tmdbImage = "https://image.tmdb.org/t/p/w500"
    // Mengubah base URL ke vidsrc-embed.ru (sesuai kode Anda)
    private val embedBase = "https://vidsrc-embed.ru" 

    // Helper untuk membuat format URL data: "TMDB_ID|media_type"
    private fun getDataUrl(id: Int, mediaType: String): String = "$id|$mediaType"

    // =============================
    // MAIN PAGE
    // =============================
    override val mainPage = mainPageOf(
        "$mainUrl/trending/movie/day?api_key=$tmdbKey" to "Film Trending",
        "$mainUrl/movie/popular?api_key=$tmdbKey" to "Film Populer",
        "$mainUrl/tv/popular?api_key=$tmdbKey" to "Serial Populer",
        "$mainUrl/tv/airing_today?api_key=$tmdbKey" to "Tayang Hari Ini"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(request.data).text
        val json = JSONObject(response).getJSONArray("results")
        val items = mutableListOf<SearchResponse>()
        
        // Tentukan tipe konten berdasarkan nama request
        val mediaType = if (request.name.contains("Serial")) "tv" else "movie"
        val tvType = if (mediaType == "tv") TvType.TvSeries else TvType.Movie

        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            val id = item.optInt("id")
            if (id == 0) continue

            val title = item.optString("title", item.optString("name", ""))
            val poster = tmdbImage + item.optString("poster_path", "")
            val year = item.optString("release_date", item.optString("first_air_date", "")).take(4).toIntOrNull()

            // Perbaikan: Menggunakan newMovieSearchResponse dan format data string standar
            items.add(
                newMovieSearchResponse(title, getDataUrl(id, mediaType), tvType) {
                    this.posterUrl = poster
                    this.year = year
                }
            )
        }

        return newHomePageResponse(request.name, items)
    }

    // =============================
    // SEARCH
    // =============================
    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/search/multi?api_key=$tmdbKey&query=$encodedQuery"
        val response = app.get(url).text
        val json = JSONObject(response).getJSONArray("results")
        val results = mutableListOf<SearchResponse>()

        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            val id = item.optInt("id")
            val mediaType = item.optString("media_type", "movie")
            if (id == 0 || (mediaType != "movie" && mediaType != "tv")) continue

            val tvType = if (mediaType == "movie") TvType.Movie else TvType.TvSeries
            val title = item.optString("title", item.optString("name", ""))
            val poster = tmdbImage + item.optString("poster_path", "")
            val year = item.optString("release_date", item.optString("first_air_date", "")).take(4).toIntOrNull()

            // Perbaikan: Menggunakan newMovieSearchResponse dan format data string standar
            results.add(
                newMovieSearchResponse(title, getDataUrl(id, mediaType), tvType) {
                    this.posterUrl = poster
                    this.year = year
                }
            )
        }

        return results
    }

    // =============================
    // LOAD DETAIL PAGE
    // =============================
    override suspend fun load(url: String): LoadResponse? {
        // Perbaikan: Mengurai URL string standar "TMDB_ID|media_type"
        val parts = url.split("|")
        if (parts.size != 2) return null
        
        val tmdbId = parts[0]
        val mediaType = parts[1]
        val isMovie = mediaType == "movie"
        
        val endpoint = if (isMovie)
            "$mainUrl/movie/$tmdbId?api_key=$tmdbKey"
        else
            "$mainUrl/tv/$tmdbId?api_key=$tmdbKey"

        val json = JSONObject(app.get(endpoint).text)
        
        val title = json.optString("title", json.optString("name", ""))
        val poster = tmdbImage + json.optString("poster_path", "")
        val overview = json.optString("overview", "")
        val year = json.optString("release_date", json.optString("first_air_date", "")).take(4).toIntOrNull()

        if (isMovie) {
            // Movie Load Response
            val embedUrl = "$embedBase/embed/movie?tmdb=$tmdbId"
            
            return newMovieLoadResponse(title, url, TvType.Movie, embedUrl) {
                this.posterUrl = poster
                this.plot = overview
                this.year = year
            }
        } else {
            // TvSeries Load Response - Mengambil Musim (tanpa memuat episode per musim secara terpisah)
            val seasonsArray = json.optJSONArray("seasons")
            val seasons = if (seasonsArray != null) {
                (0 until seasonsArray.length()).mapNotNull { i ->
                    val seasonObj = seasonsArray.getJSONObject(i)
                    val seasonNumber = seasonObj.optInt("season_number")
                    val episodeCount = seasonObj.optInt("episode_count")
                    
                    if (seasonNumber >= 1 && episodeCount > 0) {
                        val episodes = (1..episodeCount).map { episodeNumber ->
                            Episode(
                                // Format data untuk loadLinks: "TMDB_ID|SeasonNum|EpisodeNum"
                                data = "$tmdbId|$seasonNumber|$episodeNumber", 
                                name = "Eps $episodeNumber"
                            )
                        }
                        
                        TvSeason(
                            name = seasonObj.optString("name", "Musim $seasonNumber"),
                            season = seasonNumber,
                            episodes = episodes
                        )
                    } else null
                }
            } else listOf()

            // Perbaikan: Menggunakan newTvSeriesLoadResponse
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, seasons) {
                this.posterUrl = poster
                this.plot = overview
                this.year = year
            }
        }
    }

    // =============================
    // LOAD LINKS (STREAM FETCHER)
    // =============================
    override suspend fun loadLinks(
        data: String,
        isTrailer: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (isTrailer) return false

        val parts = data.split("|")
        val embedUrl: String
        
        if (parts.size == 2) { 
            // Film: data berasal dari LoadResponse URL, format: "TMDB_ID|movie"
            val tmdbId = parts[0]
            embedUrl = "$embedBase/embed/movie?tmdb=$tmdbId"
        } else if (parts.size == 3) {
            // Serial TV: data berasal dari Episode.data, format: "TMDB_ID|S|E"
            val (tmdbId, sNum, eNum) = parts
            embedUrl = "$embedBase/embed/tv?tmdb=$tmdbId&season=$sNum&episode=$eNum"
        } else {
            return false // Data tidak valid
        }
        
        // Panggil loadExtractor. Karena ini adalah URL embed, kita perlu extractor yang dapat mem-parse-nya.
        // Di sini kita mengirimkan URL embed sebagai link dan referer.
        loadExtractor(embedUrl, embedBase, subtitleCallback, callback)
        
        return true
    }
}
