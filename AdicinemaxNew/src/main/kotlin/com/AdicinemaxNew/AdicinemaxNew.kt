package com.AdicinemaxNew

import cloudstream.*
import cloudstream.utils.*
import cloudstream.utils.ExtractorLink
import org.json.JSONObject
import java.net.URLEncoder

class AdicinemaxNew : MainAPI() {
    override var mainUrl = "https://vidsrc-embed.ru"
    override var name = "AdicinemaxNew"
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = false
    override val hasQuickSearch = true
    override val useM3U8Parse = true

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
            responses.add(MainPageResponse("Trending Movies", trendingMovies))
        }
        
        // Trending TV Shows
        val trendingTV = getTMDBTrending("tv", page)
        if (trendingTV.isNotEmpty()) {
            responses.add(MainPageResponse("Trending TV Shows", trendingTV))
        }
        
        // Now Playing Movies
        val nowPlayingMovies = getTMDBNowPlaying(page)
        if (nowPlayingMovies.isNotEmpty()) {
            responses.add(MainPageResponse("Now Playing Movies", nowPlayingMovies))
        }
        
        // Popular TV Shows
        val popularTV = getTMDBPopular("tv", page)
        if (popularTV.isNotEmpty()) {
            responses.add(MainPageResponse("Popular TV Shows", popularTV))
        }
        
        return responses
    }

    override fun search(query: String): List<SearchResponse> {
        return searchTMDB(query)
    }

    override fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val parts = data.split("|")
        if (parts.size < 3) return false
        
        val type = parts[0]
        val tmdbId = parts[1]
        val imdbId = parts[2]
        val season = if (parts.size > 3) parts[3] else null
        val episode = if (parts.size > 4) parts[4] else null
        
        val vidSrcUrl = buildVidSrcUrl(type, tmdbId, imdbId, season, episode)
        
        if (vidSrcUrl.isNotEmpty()) {
            // Directly use the VidSrc URL for extraction
            app.get(vidSrcUrl).document.let { doc ->
                // Look for video sources in the iframe
                val iframe = doc.selectFirst("iframe")?.attr("src")
                if (iframe != null) {
                    loadExtractor(iframe, mainUrl, subtitleCallback, callback)
                    return true
                }
                
                // Alternative: try to find direct video sources
                val videoSources = doc.select("source[type^=video]")
                for (source in videoSources) {
                    val videoUrl = source.attr("src")
                    if (videoUrl.isNotEmpty()) {
                        callback(
                            ExtractorLink(
                                name,
                                name,
                                videoUrl,
                                mainUrl,
                                getQualityFromName(videoUrl),
                                false
                            )
                        )
                        return true
                    }
                }
            }
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
            
            results.mapNotNull { item ->
                if (item is JSONObject) {
                    parseTMDBResult(item, mediaType)
                } else null
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
            
            results.mapNotNull { item ->
                if (item is JSONObject) {
                    parseTMDBResult(item, mediaType)
                } else null
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
            
            results.mapNotNull { item ->
                if (item is JSONObject) {
                    parseTMDBResult(item, "movie")
                } else null
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
            
            results.mapNotNull { item ->
                if (item is JSONObject) {
                    val mediaType = item.optString("media_type")
                    if (mediaType == "movie" || mediaType == "tv") {
                        parseTMDBResult(item, mediaType)
                    } else null
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
            val rating = item.optDouble("vote_average", 0.0)
            
            // Get IMDB ID
            val imdbId = getIMDBId(mediaType, id.toString())
            
            val dataId = "$mediaType|$id|$imdbId"
            
            if (mediaType == "movie") {
                MovieResponse(
                    name = title,
                    url = dataId,
                    posterUrl = posterUrl,
                    plot = overview,
                    year = releaseDate.take(4).toIntOrNull(),
                    rating = rating.toInt()
                )
            } else {
                TvSeriesResponse(
                    name = title,
                    url = dataId,
                    posterUrl = posterUrl,
                    plot = overview,
                    year = releaseDate.take(4).toIntOrNull(),
                    rating = rating.toInt()
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
            val rating = json.optDouble("vote_average", 0.0)
            val genres = json.optJSONArray("genres")?.let { genresArray ->
                (0 until genresArray.length()).joinToString(", ") { 
                    genresArray.getJSONObject(it).getString("name") 
                }
            } ?: ""

            MovieLoadResponse(
                name = title,
                url = "movie|$tmdbId|$imdbId",
                posterUrl = posterUrl,
                plot = overview,
                year = releaseDate.take(4).toIntOrNull(),
                duration = runtime.toString(),
                rating = rating.toInt(),
                tags = listOf(genres)
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
            val rating = json.optDouble("vote_average", 0.0)
            val genres = json.optJSONArray("genres")?.let { genresArray ->
                (0 until genresArray.length()).joinToString(", ") { 
                    genresArray.getJSONObject(it).getString("name") 
                }
            } ?: ""

            // Get episodes for all seasons
            val allEpisodes = mutableListOf<EpisodeInfo>()
            
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
                name = title,
                url = "tv|$tmdbId|$imdbId",
                posterUrl = posterUrl,
                plot = overview,
                year = firstAirDate.take(4).toIntOrNull(),
                rating = rating.toInt(),
                episodes = allEpisodes,
                seasons = numberOfSeasons,
                tags = listOf(genres)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun getSeasonEpisodes(tmdbId: String, seasonNumber: Int, imdbId: String): List<EpisodeInfo> {
        return try {
            val url = "$TMDB_BASE_URL/tv/$tmdbId/season/$seasonNumber?api_key=$tmdbApiKey"
            val response = app.get(url).text
            val json = JSONObject(response)
            val episodesArray = json.optJSONArray("episodes") ?: return emptyList()
            
            val episodes = mutableListOf<EpisodeInfo>()
            
            for (i in 0 until episodesArray.length()) {
                val episode = episodesArray.getJSONObject(i)
                val episodeNumber = episode.optInt("episode_number", 0)
                if (episodeNumber == 0) continue // Skip episodes without number
                
                val episodeTitle = episode.optString("name", "Episode $episodeNumber")
                val overview = episode.optString("overview", "No description available")
                val stillPath = episode.optString("still_path")
                val stillUrl = if (stillPath.isNotEmpty()) "$TMDB_IMAGE_BASE$stillPath" else ""
                val airDate = episode.optString("air_date", "")
                val rating = episode.optDouble("vote_average", 0.0)
                
                episodes.add(
                    EpisodeInfo(
                        season = seasonNumber,
                        episode = episodeNumber,
                        title = episodeTitle,
                        description = overview,
                        posterUrl = stillUrl,
                        data = "tv|$tmdbId|$imdbId|$seasonNumber|$episodeNumber",
                        date = airDate,
                        rating = rating.toInt()
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

    private fun getQualityFromName(url: String): Qualities {
        return when {
            url.contains("1080") -> Qualities.P1080
            url.contains("720") -> Qualities.P720
            url.contains("480") -> Qualities.P480
            url.contains("360") -> Qualities.P360
            else -> Qualities.Unknown
        }
    }
}
