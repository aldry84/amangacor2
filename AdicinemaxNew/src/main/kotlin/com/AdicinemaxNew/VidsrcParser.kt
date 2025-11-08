package com.AdicinemaxNew

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.app
import org.json.JSONObject

class VidsrcParser {
    companion object {
        private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
        
        suspend fun parseLatestMovies(page: Int): List<SearchResponse> {
            return try {
                val url = "https://vidsrc-embed.ru/movies/latest/page-$page.json"
                val response = app.get(url).text
                val json = JSONObject(response)
                val results = json.getJSONArray("result")
                
                (0 until results.length()).mapNotNull { i ->
                    val item = results.getJSONObject(i)
                    parseMovieItem(item)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        suspend fun parseLatestTVShows(page: Int): List<SearchResponse> {
            return try {
                val url = "https://vidsrc-embed.ru/tvshows/latest/page-$page.json"
                val response = app.get(url).text
                val json = JSONObject(response)
                val results = json.getJSONArray("result")
                
                (0 until results.length()).mapNotNull { i ->
                    val item = results.getJSONObject(i)
                    parseTvShowItem(item)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        suspend fun parseLatestEpisodes(page: Int): List<SearchResponse> {
            return try {
                val url = "https://vidsrc-embed.ru/episodes/latest/page-$page.json"
                val response = app.get(url).text
                val json = JSONObject(response)
                val results = json.getJSONArray("result")
                
                (0 until results.length()).mapNotNull { i ->
                    val item = results.getJSONObject(i)
                    parseEpisodeItem(item)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        private fun parseMovieItem(item: JSONObject): SearchResponse? {
            return try {
                val tmdbId = item.optString("tmdb_id", "").takeIf { it.isNotBlank() }
                val imdbId = item.optString("imdb_id", "").takeIf { it.isNotBlank() }
                var title = item.optString("title", "")?.trim()
                val posterPath = item.optString("poster", "")
                
                if (title.isNullOrEmpty() || title == "n/A") return null
                
                val posterUrl = if (posterPath.isNotBlank() && posterPath != "n/A") {
                    "$TMDB_IMAGE_BASE$posterPath"
                } else {
                    ""
                }
                
                val year = item.optString("year", "")?.take(4)?.toIntOrNull()
                val dataId = buildDataId("movie", tmdbId, imdbId)
                
                newMovieSearchResponse(title, dataId, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.year = year
                }
            } catch (e: Exception) {
                null
            }
        }
        
        private fun parseTvShowItem(item: JSONObject): SearchResponse? {
            return try {
                val tmdbId = item.optString("tmdb_id", "").takeIf { it.isNotBlank() }
                val imdbId = item.optString("imdb_id", "").takeIf { it.isNotBlank() }
                var title = item.optString("title", "")?.trim()
                val posterPath = item.optString("poster", "")
                
                if (title.isNullOrEmpty() || title == "n/A") return null
                
                val posterUrl = if (posterPath.isNotBlank() && posterPath != "n/A") {
                    "$TMDB_IMAGE_BASE$posterPath"
                } else {
                    ""
                }
                
                val year = item.optString("year", "")?.take(4)?.toIntOrNull()
                val dataId = buildDataId("tv", tmdbId, imdbId)
                
                newTvSeriesSearchResponse(title, dataId, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.year = year
                }
            } catch (e: Exception) {
                null
            }
        }
        
        private fun parseEpisodeItem(item: JSONObject): SearchResponse? {
            return try {
                val tmdbId = item.optString("tmdb_id", "").takeIf { it.isNotBlank() }
                val imdbId = item.optString("imdb_id", "").takeIf { it.isNotBlank() }
                var title = item.optString("title", "")?.trim()
                val season = item.optString("season", "1")
                val episode = item.optString("episode", "1")
                val posterPath = item.optString("poster", "")
                
                if (title.isNullOrEmpty() || title == "n/A") return null
                
                val posterUrl = if (posterPath.isNotBlank() && posterPath != "n/A") {
                    "$TMDB_IMAGE_BASE$posterPath"
                } else {
                    ""
                }
                
                val year = item.optString("year", "")?.take(4)?.toIntOrNull()
                val dataId = buildDataId("tv", tmdbId, imdbId, season, episode)
                
                newTvSeriesSearchResponse("$title S${season}E${episode}", dataId, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.year = year
                }
            } catch (e: Exception) {
                null
            }
        }
        
        private fun buildDataId(type: String, tmdbId: String?, imdbId: String?, season: String? = null, episode: String? = null): String {
            return buildString {
                append(type)
                append("|")
                append(tmdbId ?: "")
                append("|")
                append(imdbId ?: "")
                if (season != null && episode != null) {
                    append("|")
                    append(season)
                    append("|")
                    append(episode)
                }
            }
        }
    }
}
