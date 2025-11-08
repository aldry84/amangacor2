package com.AdicinemaxNew

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
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
        
        // Latest Movies from vidsrc
        val latestMovies = parseLatestMovies(page)
        if (latestMovies.isNotEmpty()) {
            responses.add(HomePageList("Latest Movies", latestMovies))
        }
        
        // Latest TV Shows from vidsrc
        val latestTVShows = parseLatestTVShows(page)
        if (latestTVShows.isNotEmpty()) {
            responses.add(HomePageList("Latest TV Shows", latestTVShows))
        }
        
        // Latest Episodes from vidsrc
        val latestEpisodes = parseLatestEpisodes(page)
        if (latestEpisodes.isNotEmpty()) {
            responses.add(HomePageList("Latest Episodes", latestEpisodes))
        }
        
        // Trending from TMDB as fallback
        if (responses.isEmpty()) {
            val trendingMovies = getTMDBTrending("movie", page)
            if (trendingMovies.isNotEmpty()) {
                responses.add(HomePageList("Trending Movies", trendingMovies))
            }
            
            val trendingTV = getTMDBTrending("tv", page)
            if (trendingTV.isNotEmpty()) {
                responses.add(HomePageList("Trending TV Shows", trendingTV))
            }
        }
        
        return newHomePageResponse(responses)
    }

    override suspend fun search(query: String): List<SearchResponse> {
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
        
        val embedUrl = buildEmbedUrl(type, tmdbId, imdbId, season, episode)
        
        return if (embedUrl.isNotBlank()) {
            getStreamLinks(embedUrl, mainUrl, subtitleCallback, callback)
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
            loadMovieContent(tmdbId, imdbId)
        } else {
            loadTVContent(tmdbId, imdbId)
        }
    }

    // Vidsrc API Functions
    private suspend fun parseLatestMovies(page: Int): List<SearchResponse> {
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
            emptyList()
        }
    }

    private suspend fun parseLatestTVShows(page: Int): List<SearchResponse> {
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
            emptyList()
        }
    }

    private suspend fun parseLatestEpisodes(page: Int): List<SearchResponse> {
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

    private fun parseVidsrcMovieResult(item: JSONObject): SearchResponse? {
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

    private fun parseVidsrcTvResult(item: JSONObject): SearchResponse? {
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

    private fun parseVidsrcEpisodeResult(item: JSONObject): SearchResponse? {
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

    // TMDB Functions
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
            val posterUrl = if (posterPath.isNotBlank()) "$TMDB_IMAGE_BASE$posterPath" else ""
            
            val releaseDate = item.optString(if (mediaType == "movie") "release_date" else "first_air_date")
            
            // Get IMDB ID
            val imdbId = getIMDBId(mediaType, id.toString())
            
            val dataId = buildDataId(mediaType, id.toString(), imdbId)
            
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
            json.optString("imdb_id", "").takeIf { it.isNotBlank() } ?: ""
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

            newMovieLoadResponse(title, buildDataId("movie", tmdbId, imdbId), TvType.Movie, buildDataId("movie", tmdbId, imdbId)) {
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

            // Get episodes for all seasons
            val allEpisodes = mutableListOf<Episode>()
            
            for (seasonNumber in 1..numberOfSeasons) {
                try {
                    val seasonEpisodes = getSeasonEpisodes(tmdbId, seasonNumber, imdbId)
                    allEpisodes.addAll(seasonEpisodes)
                } catch (e: Exception) {
                    continue
                }
            }
            
            newTvSeriesLoadResponse(title, buildDataId("tv", tmdbId, imdbId), TvType.TvSeries, allEpisodes) {
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
            if (dateString.isNullOrBlank()) return null
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
                if (episodeNumber == 0) continue
                
                val episodeTitle = episode.optString("name", "Episode $episodeNumber")
                val overview = episode.optString("overview", "No description available")
                val stillPath = episode.optString("still_path")
                val stillUrl = if (stillPath.isNotBlank()) "$TMDB_IMAGE_BASE$stillPath" else ""
                val airDate = episode.optString("air_date", "")
                
                episodes.add(
                    newEpisode(buildDataId("tv", tmdbId, imdbId, seasonNumber.toString(), episodeNumber.toString())) {
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

    // Utility Functions
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

    private suspend fun getStreamLinks(
        embedUrl: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            // Dapatkan HTML dari embed URL
            val document = app.get(embedUrl, referer = referer).document
            
            // Cari iframe utama
            val iframe = document.selectFirst("iframe")
            val iframeSrc = iframe?.attr("src")
            
            if (iframeSrc != null) {
                // Jika iframeSrc adalah URL lengkap
                if (iframeSrc.startsWith("http")) {
                    loadExtractor(iframeSrc, embedUrl, subtitleCallback, callback)
                } else {
                    // Jika iframeSrc relative, buat URL lengkap
                    val fullIframeUrl = if (iframeSrc.startsWith("//")) {
                        "https:${iframeSrc}"
                    } else if (iframeSrc.startsWith("/")) {
                        "https://vidsrc-embed.ru${iframeSrc}"
                    } else {
                        "$referer/$iframeSrc"
                    }
                    loadExtractor(fullIframeUrl, embedUrl, subtitleCallback, callback)
                }
                true
            } else {
                // Coba cari video player langsung
                val videoElement = document.selectFirst("video")
                val videoSource = videoElement?.selectFirst("source[src]")
                val videoUrl = videoSource?.attr("src")
                
                if (videoUrl != null) {
                    // PERBAIKAN: Gunakan ExtractorLink constructor langsung
                    val linkType = if (videoUrl.contains(".m3u8")) {
                        ExtractorLinkType.HLS
                    } else {
                        ExtractorLinkType.VIDEO
                    }
                    
                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = "Vidsrc Direct",
                            url = videoUrl,
                            referer = referer,
                            quality = getQualityFromUrl(videoUrl),
                            type = linkType
                        )
                    )
                    true
                } else {
                    // Fallback ke extractor biasa
                    loadExtractor(embedUrl, referer, subtitleCallback, callback)
                    true
                }
            }
        } catch (e: Exception) {
            // Fallback ke extractor biasa jika ada error
            try {
                loadExtractor(embedUrl, referer, subtitleCallback, callback)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    // PERBAIKAN: Fungsi getQualityFromUrl sekarang mengembalikan Int
    private fun getQualityFromUrl(url: String): Int {
        return when {
            url.contains("1080") -> Qualities.P1080.value
            url.contains("720") -> Qualities.P720.value
            url.contains("480") -> Qualities.P480.value
            url.contains("360") -> Qualities.P360.value
            url.contains("240") -> Qualities.P240.value
            else -> Qualities.Unknown.value
        }
    }
}
