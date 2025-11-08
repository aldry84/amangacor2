package com.AdicinemaxNew

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.app
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class TMDBHelper(private val apiKey: String) {
    companion object {
        const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
        const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    }
    
    suspend fun searchTMDB(query: String): List<SearchResponse> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$TMDB_BASE_URL/search/multi?api_key=$apiKey&query=$encodedQuery&page=1"
            val response = app.get(url).text
            val json = JSONObject(response)
            val results = json.getJSONArray("results")
            
            (0 until results.length()).mapNotNull { i ->
                val item = results.getJSONObject(i)
                val mediaType = item.optString("media_type")
                if (mediaType == "movie" || mediaType == "tv") {
                    parseTMDBSearchResult(item, mediaType)
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun getTrending(mediaType: String, page: Int): List<SearchResponse> {
        return try {
            val url = "$TMDB_BASE_URL/trending/$mediaType/week?api_key=$apiKey&page=$page"
            val response = app.get(url).text
            val json = JSONObject(response)
            val results = json.getJSONArray("results")
            
            (0 until results.length()).mapNotNull { i ->
                val item = results.getJSONObject(i)
                parseTMDBSearchResult(item, mediaType)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun getIMDBId(mediaType: String, tmdbId: String): String {
        return try {
            val url = "$TMDB_BASE_URL/$mediaType/$tmdbId/external_ids?api_key=$apiKey"
            val response = app.get(url).text
            val json = JSONObject(response)
            json.optString("imdb_id", "").takeIf { it.isNotBlank() } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    suspend fun loadMovieContent(tmdbId: String, imdbId: String): LoadResponse? {
        return try {
            val url = "$TMDB_BASE_URL/movie/$tmdbId?api_key=$apiKey"
            val response = app.get(url).text
            val json = JSONObject(response)
            
            val title = json.getString("title")
            val posterPath = json.optString("poster_path")
            val posterUrl = if (posterPath.isNotBlank()) "$TMDB_IMAGE_BASE$posterPath" else ""
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
    
    suspend fun loadTVContent(tmdbId: String, imdbId: String): LoadResponse? {
        return try {
            val url = "$TMDB_BASE_URL/tv/$tmdbId?api_key=$apiKey"
            val response = app.get(url).text
            val json = JSONObject(response)
            
            val title = json.getString("name")
            val posterPath = json.optString("poster_path")
            val posterUrl = if (posterPath.isNotBlank()) "$TMDB_IMAGE_BASE$posterPath" else ""
            val overview = json.optString("overview", "No description available")
            val firstAirDate = json.optString("first_air_date")
            val numberOfSeasons = json.optInt("number_of_seasons", 0)
            val rating = (json.optDouble("vote_average", 0.0) * 10).toInt()
            val genres = json.optJSONArray("genres")?.let { genresArray ->
                (0 until genresArray.length()).map { 
                    genresArray.getJSONObject(it).getString("name") 
                }
            } ?: emptyList()

            val allEpisodes = mutableListOf<Episode>()
            
            for (seasonNumber in 1..numberOfSeasons) {
                try {
                    val seasonEpisodes = getSeasonEpisodes(tmdbId, seasonNumber, imdbId)
                    allEpisodes.addAll(seasonEpisodes)
                } catch (e: Exception) {
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
    
    private suspend fun getSeasonEpisodes(tmdbId: String, seasonNumber: Int, imdbId: String): List<Episode> {
        return try {
            val url = "$TMDB_BASE_URL/tv/$tmdbId/season/$seasonNumber?api_key=$apiKey"
            val response = app.get(url).text
            val json = JSONObject(response)
            val episodesArray = json.optJSONArray("episodes") ?: return emptyList()
            
            val episodes = mutableListOf<Episode>()
            
            for (i in 0 until episodesArray.length()) {
                val episode = episodesArray.getJSONObject(i)
                val episodeNumber = episode.optInt("episode_number", 0)
                if (episodeNumber == 0) continue
                
                val episodeTitle = episode.optString("name", "Episode $episodeNumber")
                val overview = episode.optString("overview", "No description available")
                val stillPath = episode.optString("still_path")
                val stillUrl = if (stillPath.isNotBlank()) "$TMDB_IMAGE_BASE$stillPath" else ""
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
    
    private fun parseTMDBSearchResult(item: JSONObject, mediaType: String): SearchResponse? {
        return try {
            val id = item.getInt("id")
            val title = when (mediaType) {
                "movie" -> item.getString("title")
                "tv" -> item.getString("name")
                else -> return null
            }
            
            val posterPath = item.optString("poster_path")
            val posterUrl = if (posterPath.isNotBlank()) "$TMDB_IMAGE_BASE$posterPath" else ""
            val releaseDate = item.optString(if (mediaType == "movie") "release_date" else "first_air_date")
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
    
    private fun parseDate(dateString: String?): Long? {
        return try {
            if (dateString.isNullOrBlank()) return null
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            format.parse(dateString)?.time
        } catch (e: Exception) {
            null
        }
    }
}
