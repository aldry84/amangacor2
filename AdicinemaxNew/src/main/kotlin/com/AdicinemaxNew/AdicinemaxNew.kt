package com.AdicinemaxNew

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.AdicinemaxNew.extractors.VidsrcExtractor
import com.AdicinemaxNew.parsers.VidsrcParser
import com.AdicinemaxNew.utils.TMDBHelper

class AdicinemaxNew : MainAPI() {
    override var mainUrl = "https://vidsrc-embed.ru"
    override var name = "AdicinemaxNew"
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = false
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val tmdbApiKey = "1cfadd9dbfc534abf6de40e1e7eaf4c7"
    private val tmdbHelper = TMDBHelper(tmdbApiKey)
    private val vidsrcParser = VidsrcParser

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val responses = mutableListOf<HomePageList>()
        
        // Latest Movies from vidsrc
        val latestMovies = vidsrcParser.parseLatestMovies(page)
        if (latestMovies.isNotEmpty()) {
            responses.add(HomePageList("Latest Movies", latestMovies))
        }
        
        // Latest TV Shows from vidsrc
        val latestTVShows = vidsrcParser.parseLatestTVShows(page)
        if (latestTVShows.isNotEmpty()) {
            responses.add(HomePageList("Latest TV Shows", latestTVShows))
        }
        
        // Latest Episodes from vidsrc
        val latestEpisodes = vidsrcParser.parseLatestEpisodes(page)
        if (latestEpisodes.isNotEmpty()) {
            responses.add(HomePageList("Latest Episodes", latestEpisodes))
        }
        
        // Trending from TMDB as fallback
        if (responses.isEmpty()) {
            val trendingMovies = tmdbHelper.getTrending("movie", page)
            if (trendingMovies.isNotEmpty()) {
                responses.add(HomePageList("Trending Movies", trendingMovies))
            }
            
            val trendingTV = tmdbHelper.getTrending("tv", page)
            if (trendingTV.isNotEmpty()) {
                responses.add(HomePageList("Trending TV Shows", trendingTV))
            }
        }
        
        return newHomePageResponse(responses)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return tmdbHelper.searchTMDB(query)
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
        
        val embedUrl = buildEmbedUrl(type, tmdbId, imdbId, season, episode)
        
        return if (embedUrl.isNotBlank()) {
            VidsrcExtractor.getStreamLinks(embedUrl, mainUrl, subtitleCallback, callback)
        } else {
            false
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val parts = url.split("|")
        if (parts.size < 3) return null
        
        val type = parts[0]
        val tmdbId = parts[1]
        val imdbId = parts[2]
        
        return if (type == "movie") {
            tmdbHelper.loadMovieContent(tmdbId, imdbId)
        } else {
            tmdbHelper.loadTVContent(tmdbId, imdbId)
        }
    }

    private fun buildEmbedUrl(type: String, tmdbId: String, imdbId: String, season: String?, episode: String?): String {
        return when (type) {
            "movie" -> {
                when {
                    imdbId.isNotBlank() && imdbId != "null" -> "$mainUrl/embed/movie?imdb=$imdbId"
                    tmdbId.isNotBlank() && tmdbId != "null" -> "$mainUrl/embed/movie?tmdb=$tmdbId"
                    else -> ""
                }
            }
            "tv" -> {
                if (season != null && episode != null) {
                    when {
                        imdbId.isNotBlank() && imdbId != "null" -> "$mainUrl/embed/tv?imdb=$imdbId&season=$season&episode=$episode"
                        tmdbId.isNotBlank() && tmdbId != "null" -> "$mainUrl/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode"
                        else -> ""
                    }
                } else {
                    // Untuk TV show main page (tanpa episode spesifik)
                    when {
                        imdbId.isNotBlank() && imdbId != "null" -> "$mainUrl/embed/tv?imdb=$imdbId"
                        tmdbId.isNotBlank() && tmdbId != "null" -> "$mainUrl/embed/tv?tmdb=$tmdbId"
                        else -> ""
                    }
                }
            }
            else -> ""
        }
    }
}
