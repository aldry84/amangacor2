package com.AdicinemaxNew

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.mvvm.safeApiCall
import org.json.JSONObject
import java.net.URLEncoder

class AdicinemaxNew : MainAPI() {
    override var mainUrl = "https://vidsrc-embed.ru"
    override var name = "AdicinemaxNew"
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = false
    override val hasQuickSearch = true

    private val tmdbApiKey = "1cfadd9dbfc534abf6de40e1e7eaf4c7"
    
    companion object {
        const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
        const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    }

    override fun getMainPage(page: Int, request: MainPageRequest): List<MainPageResponse> {
        val responses = mutableListOf<MainPageResponse>()
        
        // Trending Movies
        val trendingMovies = getTMDBTrending("movie", page)
        if (trendingMovies.isNotEmpty()) {
            responses.add(MainPageResponse("Trending Movies", trendingMovies, true))
        }
        
        // Trending TV Shows
        val trendingTV = getTMDBTrending("tv", page)
        if (trendingTV.isNotEmpty()) {
            responses.add(MainPageResponse("Trending TV Shows", trendingTV, true))
        }
        
        // Now Playing Movies
        val nowPlayingMovies = getTMDBNowPlaying(page)
        if (nowPlayingMovies.isNotEmpty()) {
            responses.add(MainPageResponse("Now Playing Movies", nowPlayingMovies, true))
        }
        
        // Popular TV Shows
        val popularTV = getTMDBPopular("tv", page)
        if (popularTV.isNotEmpty()) {
            responses.add(MainPageResponse("Popular TV Shows", popularTV, true))
        }
        
        return responses
    }

    override fun search(query: String): List<SearchResponse> {
        return searchTMDB(query)
    }

    override fun loadLinks(
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
            loadExtractor(vidSrcUrl, "$mainUrl/", subtitleCallback, callback)
            return true
        }
        return false
    }

    override fun loadContent(id: String): LoadResponse? {
        val parts = id.split("|")
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

    private fun getTMDBTrending(mediaType: String, page: Int): List<SearchResponse> {
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

    private fun getTMDBPopular(mediaType: String, page: Int): List<SearchResponse> {
        return try {
            val url = "$TMDB_BASE_URL/$mediaType/popular?api_key=$tmdbApiKey&page=$page"
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

    private fun getTMDBNowPlaying(page: Int): List<SearchResponse> {
        return try {
            val url = "$TMDB_BASE_URL/movie/now_playing?api_key=$tmdbApiKey&page=$page"
            val response = app.get(url).text
            val json = JSONObject(response)
            val results = json.getJSONArray("results")
            
            (0 until results.length()).mapNotNull { i ->
                val item = results.getJSONObject(i)
                parseTMDBResult(item, "movie")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun searchTMDB(query: String): List<SearchResponse> {
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

    private fun parseTMDBResult(item: JSONObject, mediaType: String): SearchResponse? {
        return try {
            val id = item.getInt("id")
            val title = when (mediaType) {
                "movie" -> item.getString("title")
                "tv" -> item.getString("name")
                else -> return null
            }
            
            val posterPath = item.optString("poster_path")
            val posterUrl = if (posterPath.isNotEmpty()) "$TMDB_IMAGE_BASE$posterPath" else ""
            
            val overview = item.optString("overview", "No description available")
            val releaseDate = item.optString(if (mediaType == "movie") "release_date" else "first_air_date")
            val rating = (item.optDouble("vote_average", 0.0) * 10).toInt()
            
            // Get IMDB ID
            val imdbId = getIMDBId(mediaType, id.toString())
            
            val dataId = "$mediaType|$id|$imdbId"
            
            if (mediaType == "movie") {
                MovieResponse(
                    name = title,
                    url = dataId,
                    apiName = name,
                    type = TvType.Movie,
                    posterUrl = posterUrl,
                    year = releaseDate.take(4).toIntOrNull(),
                    plot = overview,
                    rating = rating
                )
            } else {
                TvSeriesResponse(
                    name = title,
                    url = dataId,
                    apiName = name,
                    type = TvType.TvSeries,
                    posterUrl = posterUrl,
                    year = releaseDate.take(4).toIntOrNull(),
                    plot = overview,
                    rating = rating
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getIMDBId(mediaType: String, tmdbId: String): String {
        return try {
            val url = "$TMDB_BASE_URL/$mediaType/$tmdbId/external_ids?api_key=$tmdbApiKey"
            val response = app.get(url).text
            val json = JSONObject(response)
            json.optString("imdb_id", "").takeIf { it.isNotEmpty() } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun loadMovieContent(tmdbId: String, imdbId: String): MovieLoadResponse? {
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
                (0 until genresArray.length()).joinToString(", ") { 
                    genresArray.getJSONObject(it).getString("name") 
                }
            } ?: ""

            MovieLoadResponse(
                title,
                "movie|$tmdbId|$imdbId",
                TvType.Movie,
                posterUrl,
                releaseDate.take(4).toIntOrNull(),
                overview,
                rating,
                null,
                genres.split(", "),
                duration = runtime
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun loadTVContent(tmdbId: String, imdbId: String): TvSeriesLoadResponse? {
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
                (0 until genresArray.length()).joinToString(", ") { 
                    genresArray.getJSONObject(it).getString("name") 
                }
            } ?: ""

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
            
            TvSeriesLoadResponse(
                title,
                "tv|$tmdbId|$imdbId",
                TvType.TvSeries,
                allEpisodes,
                posterUrl,
                firstAirDate.take(4).toIntOrNull(),
                overview,
                rating,
                null,
                genres.split(", "),
                seasons = numberOfSeasons
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun getSeasonEpisodes(tmdbId: String, seasonNumber: Int, imdbId: String): List<Episode> {
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
                val rating = (episode.optDouble("vote_average", 0.0) * 10).toInt()
                
                episodes.add(
                    Episode(
                        data = "tv|$tmdbId|$imdbId|$seasonNumber|$episodeNumber",
                        episode = episodeNumber,
                        season = seasonNumber,
                        name = episodeTitle,
                        posterUrl = stillUrl,
                        date = airDate,
                        description = overview,
                        rating = rating
                    )
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
                // Prioritize IMDB ID if available, otherwise use TMDB
                if (imdbId.isNotEmpty()) {
                    "$mainUrl/embed/movie/$imdbId"
                } else {
                    "$mainUrl/embed/movie/$tmdbId"
                }
            }
            "tv" -> {
                if (season != null && episode != null) {
                    // Use the format: /embed/tv/{id}/{season}-{episode}
                    if (imdbId.isNotEmpty()) {
                        "$mainUrl/embed/tv/$imdbId/$season-$episode"
                    } else {
                        "$mainUrl/embed/tv/$tmdbId/$season-$episode"
                    }
                } else {
                    // This shouldn't normally happen for episode loading
                    ""
                }
            }
            else -> ""
        }
    }
}
