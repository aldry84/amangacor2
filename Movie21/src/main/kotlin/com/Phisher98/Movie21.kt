// File: com.Phisher98.Movie21.kt
package com.Phisher98 // PERBAIKAN: Menggunakan package yang benar sesuai error log

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
    private val embedBase = "https://vidsrc-embed.ru" 

    private fun getDataUrl(id: Int, mediaType: String): String = "$id|$mediaType"

    // [BAGIAN MAIN PAGE DAN SEARCH TIDAK PERLU PERUBAHAN KECUALI PENGGUNAAN newMovieSearchResponse]
    
    // ... (Hapus Main Page & Search untuk singkatnya, asumsikan menggunakan new[Type]SearchResponse) ...
    
    // =============================
    // LOAD DETAIL PAGE (PERBAIKAN KRITIS UNTUK EPISODE/SEASON)
    // =============================
    override suspend fun load(url: String): LoadResponse? {
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
            val embedUrl = "$embedBase/embed/movie?tmdb=$tmdbId"
            
            return newMovieLoadResponse(title, url, TvType.Movie, embedUrl) {
                this.posterUrl = poster
                this.plot = overview
                this.year = year
            }
        } else {
            // Perbaikan TvSeries: Menggunakan newEpisode dan TvSeason builder
            val seasonsArray = json.optJSONArray("seasons")
            val seasons = if (seasonsArray != null) {
                (0 until seasonsArray.length()).mapNotNull { i ->
                    val seasonObj = seasonsArray.getJSONObject(i)
                    val seasonNumber = seasonObj.optInt("season_number")
                    val episodeCount = seasonObj.optInt("episode_count")
                    
                    if (seasonNumber >= 1 && episodeCount > 0) {
                        val episodes = (1..episodeCount).map { episodeNumber ->
                            // PERBAIKAN: Menggunakan newEpisode builder
                            newEpisode(data = "$tmdbId|$seasonNumber|$episodeNumber", name = "Eps $episodeNumber") {
                                this.season = seasonNumber
                                this.episode = episodeNumber
                            }
                        }
                        
                        // PERBAIKAN: Menggunakan builder TvSeason
                        newTvSeason(seasonObj.optString("name", "Musim $seasonNumber"), episodes) {
                            this.season = seasonNumber
                        }
                    } else null
                }
            } else listOf()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, seasons) {
                this.posterUrl = poster
                this.plot = overview
                this.year = year
            }
        }
    }
    
    // ... (Fungsi loadLinks tetap sama) ...
}
