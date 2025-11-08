package com.AdicinemaxNew

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class AdicinemaxNew : MainAPI() {
    override var mainUrl = "https://vidsrc-embed.ru"
    override var name = "AdicinemaxNew"
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = false
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val tmdbApiKey = "1cfadd9dbfc534abf6de40e1e7eaf4c7"
    
    companion object {
        const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
        const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val responses = mutableListOf<HomePageList>()
        
        // Latest Movies from vidsrc - lebih reliable
        val latestMovies = getLatestMovies(page)
        if (latestMovies.isNotEmpty()) {
            responses.add(HomePageList("Latest Movies", latestMovies))
        }
        
        // Latest TV Shows from vidsrc - lebih reliable
        val latestTVShows = getLatestTVShows(page)
        if (latestTVShows.isNotEmpty()) {
            responses.add(HomePageList("Latest TV Shows", latestTVShows))
        }
        
        // Latest Episodes from vidsrc - lebih reliable
        val latestEpisodes = getLatestEpisodes(page)
        if (latestEpisodes.isNotEmpty()) {
            responses.add(HomePageList("Latest Episodes", latestEpisodes))
        }
        
        return newHomePageResponse(responses)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Prioritize search from vidsrc langsung
        val vidsrcResults = searchVidsrc(query)
        if (vidsrcResults.isNotEmpty()) {
            return vidsrcResults
        }
        // Fallback ke TMDB
        return searchTMDB(query)
    }

    override suspend fun loadLinks(
        data: String, 
        isCasting: Boolean, 
        subtitleCallback: (SubtitleFile) -> Unit, 
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("|")
        if (parts.size < 3) return false
        
        val type = parts[0]
        val tmdbId = parts[1]
        val imdbId = parts[2]
        val season = if (parts.size > 3) parts[3] else null
        val episode = if (parts.size > 4) parts[4] else null
        
        val vidSrcUrl = buildVidSrcUrl(type, tmdbId, imdbId, season, episode)
        
        if (vidSrcUrl.isNotEmpty()) {
            // Gunakan mainUrl sebagai referer
            loadExtractor(vidSrcUrl, mainUrl, subtitleCallback, callback)
            return true
        }
        return false
    }

    override suspend fun load(url: String): LoadResponse? {
        val parts = url.split("|")
        if (parts.size < 3) return null
        
        val type = parts[0]
        val tmdbId = parts[1]
        val imdbId = parts[2]
        
        return if (type == "movie") {
            loadMovieContent(tmdbId, imdbId)
        } else {
            loadTVContent(tmdbId, imdbId)
        }
    }

    // Fungsi untuk mendapatkan konten langsung dari vidsrc
    private suspend fun getLatestMovies(page: Int): List<SearchResponse> {
        return try {
            val url = "$mainUrl/movies/latest/page-$page.json"
            val response = app.get(url).text
            val json = JSONObject(response)
            val results = json.getJSONArray("result")
            
            (0 until results.length()).mapNotNull { i ->
                val item = results.getJSONObject(i)
                parseVidsrcMovieResult(item)
            }
        } catch (e: Exception) {
            // Fallback ke TMDB trending
            getTMDBTrending("movie", page)
        }
    }

    private suspend fun getLatestTVShows(page: Int): List<SearchResponse> {
        return try {
            val url = "$mainUrl/tvshows/latest/page-$page.json"
            val response = app.get(url).text
            val json = JSONObject(response)
            val results = json.getJSONArray("result")
            
            (0 until results.length()).mapNotNull { i ->
                val item = results.getJSONObject(i)
                parseVidsrcTvResult(item)
            }
        } catch (e: Exception) {
            // Fallback ke TMDB trending
            getTMDBTrending("tv", page)
        }
    }

    private suspend fun getLatestEpisodes(page: Int): List<SearchResponse> {
        return try {
            val url = "$mainUrl/episodes/latest/page-$page.json"
            val response = app.get(url).text
            val json = JSONObject(response)
            val results = json.getJSONArray("result")
            
            (0 until results.length()).mapNotNull { i ->
                val item = results.getJSONObject(i)
                parseVidsrcEpisodeResult(item)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun searchVidsrc(query: String): List<SearchResponse> {
        return try {
            // Vidsrc tidak memiliki endpoint search, jadi kita gunakan TMDB
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseVidsrcMovieResult(item: JSONObject): SearchResponse? {
        return try {
            val tmdbId = item.optString("tmdb_id", "")
            val imdbId = item.optString("imdb_id", "")
            var title = item.optString("title", "")?.trim()
            val posterPath = item.optString("poster", "")
            
            // Validasi title
            if (title.isNullOrEmpty() || title == "n/A" || title == "N/A") {
                title = "Unknown Movie"
            }
            
            val posterUrl = if (posterPath.isNotEmpty() && posterPath != "n/A") {
                "$TMDB_IMAGE_BASE$posterPath"
            } else {
                ""
            }
            
            val year = item.optString("year", "")?.take(4)?.toIntOrNull()
            val dataId = "movie|$tmdbId|$imdbId"
            
            newMovieSearchResponse(title, dataId, TvType.Movie) {
                this.posterUrl = posterUrl
                this.year = year
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseVidsrcTvResult(item: JSONObject): SearchResponse? {
        return try {
            val tmdbId = item.optString("tmdb_id", "")
            val imdbId = item.optString("imdb_id", "")
            var title = item.optString("title", "")?.trim()
            val posterPath = item.optString("poster", "")
            
            // Validasi title
            if (title.isNullOrEmpty() || title == "n/A" || title == "N/A") {
                title = "Unknown TV Show"
            }
            
            val posterUrl = if (posterPath.isNotEmpty() && posterPath != "n/A") {
                "$TMDB_IMAGE_BASE$posterPath"
            } else {
                ""
            }
            
            val year = item.optString("year", "")?.take(4)?.toIntOrNull()
            val dataId = "tv|$tmdbId|$imdbId"
            
            newTvSeriesSearchResponse(title, dataId, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.year = year
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseVidsrcEpisodeResult(item: JSONObject): SearchResponse? {
        return try {
            val tmdbId = item.optString("tmdb_id", "")
            val imdbId = item.optString("imdb_id", "")
            var title = item.optString("title", "")?.trim()
            val season = item.optString("season", "1")
            val episode = item.optString("episode", "1")
            val posterPath = item.optString("poster", "")
            
            // Validasi title
            if (title.isNullOrEmpty() || title == "n/A" || title == "N/A") {
                title = "Unknown Episode"
            }
            
            val posterUrl = if (posterPath.isNotEmpty() && posterPath != "n/A") {
                "$TMDB_IMAGE_BASE$posterPath"
            } else {
                ""
            }
            
            val year = item.optString("year", "")?.take(4)?.toIntOrNull()
            val dataId = "tv|$tmdbId|$imdbId|$season|$episode"
            
            newTvSeriesSearchResponse("$title S${season}E${episode}", dataId, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.year = year
            }
        } catch (e: Exception) {
            null
        }
    }

    // TMDB functions sebagai fallback
    private suspend fun getTMDBTrending(mediaType: String, page: Int): List<SearchResponse> {
        return try {
            val url = "$TMDB_BASE_URL/trending/$mediaType/week?api_key=$tmdbApiKey&page=$page"
            val response = app.get(url).text
            val json = JSONObject(response)
            val results = json.getJSONArray("results")
            
            (0 until results.length()).mapNotNull { i ->
                val item = results.getJSONObject(i)
                parseTMDBResult(item, mediaType)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun searchTMDB(query: String): List<SearchResponse> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$TMDB_BASE_URL/search/multi?api_key=$tmdbApiKey&query=$encodedQuery&page=1"
            val response = app.get(url).text
            val json = JSONObject(response)
            val results = json.getJSONArray("results")
            
            (0 until results.length()).mapNotNull { i ->
                val item = results.getJSONObject(i)
                val mediaType = item.optString("media_type")
                if (mediaType == "movie" || mediaType == "tv") {
                    parseTMDBResult(item, mediaType)
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun parseTMDBResult(item: JSONObject, mediaType: String): SearchResponse? {
        return try {
            val id = item.getInt("id")
            val title = when (mediaType) {
                "movie" -> item.getString("title")
                "tv" -> item.getString("name")
                else -> return null
            }
            
            val posterPath = item.optString("poster_path")
            val posterUrl = if (posterPath.isNotEmpty()) "$TMDB_IMAGE_BASE$posterPath" else ""
            
            val releaseDate = item.optString(if (mediaType == "movie") "release_date" else "first_air_date")
            
            // Get IMDB ID
            val imdbId = getIMDBId(mediaType, id.toString())
            
            val dataId = "$mediaType|$id|$imdbId"
            
            if (mediaType == "movie") {
                newMovieSearchResponse(title, dataId, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.year = releaseDate.take(4).toIntOrNull()
                }
            } else {
                newTvSeriesSearchResponse(title, dataId, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.year = releaseDate.take(4).toIntOrNull()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getIMDBId(mediaType: String, tmdbId: String): String {
        return try {
            val url = "$TMDB_BASE_URL/$mediaType/$tmdbId/external_ids?api_key=$tmdbApiKey"
            val response = app.get(url).text
            val json = JSONObject(response)
            json.optString("imdb_id", "").takeIf { it.isNotEmpty() } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun loadMovieContent(tmdbId: String, imdbId: String): LoadResponse? {
        return try {
            val url = "$TMDB_BASE_URL/movie/$tmdbId?api_key=$tmdbApiKey"
            val response = app.get(url).text
            val json = JSONObject(response)
            
            val title = json.getString("title")
            val posterPath = json.optString("poster_path")
            val posterUrl = if (posterPath.isNotEmpty()) "$TMDB_IMAGE_BASE$posterPath" else ""
            val overview = json.optString("overview", "No description available")
            val releaseDate = json.optString("release_date")
            val runtime = json.optInt("runtime", 0)
            val rating = (json.optDouble("vote_average", 0.0) * 10).toInt()
            val genres = json.optJSONArray("genres")?.let { genresArray ->
                (0 until genresArray.length()).map { 
                    genresArray.getJSONObject(it).getString("name") 
                }
            } ?: emptyList()

            newMovieLoadResponse(title, "movie|$tmdbId|$imdbId", TvType.Movie, "movie|$tmdbId|$imdbId") {
                this.posterUrl = posterUrl
                this.year = releaseDate.take(4).toIntOrNull()
                this.plot = overview
                this.duration = runtime
                this.tags = genres
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun loadTVContent(tmdbId: String, imdbId: String): LoadResponse? {
        return try {
            val url = "$TMDB_BASE_URL/tv/$tmdbId?api_key=$tmdbApiKey"
            val response = app.get(url).text
            val json = JSONObject(response)
            
            val title = json.getString("name")
            val posterPath = json.optString("poster_path")
            val posterUrl = if (posterPath.isNotEmpty()) "$TMDB_IMAGE_BASE$posterPath" else ""
            val overview = json.optString("overview", "No description available")
            val firstAirDate = json.optString("first_air_date")
            val numberOfSeasons = json.optInt("number_of_seasons", 0)
            val rating = (json.optDouble("vote_average", 0.0) * 10).toInt()
            val genres = json.optJSONArray("genres")?.let { genresArray ->
                (0 until genresArray.length()).map { 
                    genresArray.getJSONObject(it).getString("name") 
                }
            } ?: emptyList()

            // Get episodes for all seasons
            val allEpisodes = mutableListOf<Episode>()
            
            for (seasonNumber in 1..numberOfSeasons) {
                try {
                    val seasonEpisodes = getSeasonEpisodes(tmdbId, seasonNumber, imdbId)
                    allEpisodes.addAll(seasonEpisodes)
                } catch (e: Exception) {
                    // Skip season if there's an error
                    continue
                }
            }
            
            newTvSeriesLoadResponse(title, "tv|$tmdbId|$imdbId", TvType.TvSeries, allEpisodes) {
                this.posterUrl = posterUrl
                this.year = firstAirDate.take(4).toIntOrNull()
                this.plot = overview
                this.tags = genres
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDate(dateString: String?): Long? {
        return try {
            if (dateString.isNullOrEmpty()) return null
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            format.parse(dateString)?.time
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getSeasonEpisodes(tmdbId: String, seasonNumber: Int, imdbId: String): List<Episode> {
        return try {
            val url = "$TMDB_BASE_URL/tv/$tmdbId/season/$seasonNumber?api_key=$tmdbApiKey"
            val response = app.get(url).text
            val json = JSONObject(response)
            val episodesArray = json.optJSONArray("episodes") ?: return emptyList()
            
            val episodes = mutableListOf<Episode>()
            
            for (i in 0 until episodesArray.length()) {
                val episode = episodesArray.getJSONObject(i)
                val episodeNumber = episode.optInt("episode_number", 0)
                if (episodeNumber == 0) continue // Skip episodes without number
                
                val episodeTitle = episode.optString("name", "Episode $episodeNumber")
                val overview = episode.optString("overview", "No description available")
                val stillPath = episode.optString("still_path")
                val stillUrl = if (stillPath.isNotEmpty()) "$TMDB_IMAGE_BASE$stillPath" else ""
                val airDate = episode.optString("air_date", "")
                
                episodes.add(
                    newEpisode("tv|$tmdbId|$imdbId|$seasonNumber|$episodeNumber") {
                        this.name = episodeTitle
                        this.season = seasonNumber
                        this.episode = episodeNumber
                        this.posterUrl = stillUrl
                        this.description = overview
                        this.date = parseDate(airDate)
                    }
                )
            }
            
            episodes
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildVidSrcUrl(type: String, tmdbId: String, imdbId: String, season: String?, episode: String?): String {
        return when (type) {
            "movie" -> {
                // Gunakan format yang benar: /embed/movie/{id}
                if (imdbId.isNotEmpty() && imdbId != "null") {
                    "$mainUrl/embed/movie/$imdbId"
                } else if (tmdbId.isNotEmpty() && tmdbId != "null") {
                    "$mainUrl/embed/movie/$tmdbId"
                } else {
                    ""
                }
            }
            "tv" -> {
                if (season != null && episode != null) {
                    // Gunakan format: /embed/tv/{id}/{season}-{episode}
                    if (imdbId.isNotEmpty() && imdbId != "null") {
                        "$mainUrl/embed/tv/$imdbId/$season-$episode"
                    } else if (tmdbId.isNotEmpty() && tmdbId != "null") {
                        "$mainUrl/embed/tv/$tmdbId/$season-$episode"
                    } else {
                        ""
                    }
                } else {
                    // Untuk TV show main page (shouldn't happen)
                    ""
                }
            }
            else -> ""
        }
    }
}
