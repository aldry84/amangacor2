package com.Adi21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

class Adi21 : MainAPI() {
    override var mainUrl = "https://api.themoviedb.org/3"
    override var name = "TMDB + Vidsrc"
    override var lang = "id"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val apiKey = "1cfadd9dbfc534abf6de40e1e7eaf4c7"

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val movieUrl = "$mainUrl/search/movie?query=$encodedQuery&api_key=$apiKey"
        val tvUrl = "$mainUrl/search/tv?query=$encodedQuery&api_key=$apiKey"

        val movieJson = app.get(movieUrl).parsed<TmdbSearchResult>()
        val tvJson = app.get(tvUrl).parsed<TmdbSearchResult>()

        val movieResults = movieJson.results.map {
            MovieSearchResponse(
                it.title ?: "Unknown",
                "https://vidsrc.cc/embed/${it.id}",
                name,
                TvType.Movie
            ).apply {
                posterUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}"
                addYear(it.release_date?.take(4)?.toIntOrNull())
            }
        }

        val tvResults = tvJson.results.map {
            TvSeriesSearchResponse(
                it.name ?: "Unknown",
                "https://vidsrc.cc/embed/${it.id}",
                name,
                TvType.TvSeries
            ).apply {
                posterUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}"
            }
        }

        return movieResults + tvResults
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/").toIntOrNull() ?: throw ErrorLoadingException("Invalid ID")
        val isTv = url.contains("/tv")
        val detailUrl = if (isTv) "$mainUrl/tv/$id?api_key=$apiKey" else "$mainUrl/movie/$id?api_key=$apiKey"
        val videoUrl = if (isTv) "$mainUrl/tv/$id/videos?api_key=$apiKey" else "$mainUrl/movie/$id/videos?api_key=$apiKey"
        val creditsUrl = if (isTv) "$mainUrl/tv/$id/credits?api_key=$apiKey" else "$mainUrl/movie/$id/credits?api_key=$apiKey"
        val reviewUrl = if (isTv) "$mainUrl/tv/$id/reviews?api_key=$apiKey" else "$mainUrl/movie/$id/reviews?api_key=$apiKey"

        val detail = app.get(detailUrl).parsed<TmdbDetail>()
        val trailerKey = app.get(videoUrl).parsed<TmdbVideoResult>().results.firstOrNull {
            it.site == "YouTube" && it.type == "Trailer"
        }?.key

        val trailer = trailerKey?.let { "https://www.youtube.com/watch?v=$it" }
        val episodes = if (isTv) getEpisodes(id) else listOf(Episode(url, "Watch"))
        val recommendations = getRecommendations(id, isTv)
        val cast = getCast(creditsUrl)
        val (userRating, userReview) = getReviews(reviewUrl)

        return MovieLoadResponse(
            detail.title ?: detail.name ?: "Unknown",
            url,
            name,
            if (isTv) TvType.TvSeries else TvType.Movie
        ).apply {
            posterUrl = "https://image.tmdb.org/t/p/w500${detail.poster_path}"
            addYear(detail.release_date?.take(4)?.toIntOrNull() ?: detail.first_air_date?.take(4)?.toIntOrNull())
            plot = detail.overview + if (userReview != null) "\n\n💬 Review: $userReview" else ""
            tags = detail.genres.map { it.name }
            trailer?.let { addTrailer("Trailer", it) }
            addActors(cast)
            addEpisodes(episodes)
            addRecommendations(recommendations)
            userRating?.let { addScore(it.toInt()) }
        }
    }

    private suspend fun getCast(url: String): List<Actor> {
        val credits = app.get(url).parsed<TmdbCredits>()
        return credits.cast.take(10).map {
            Actor(it.name, ActorRole(it.character))
        }
    }

    private suspend fun getReviews(url: String): Pair<Float?, String?> {
        val json = app.get(url).parsed<TmdbReviewResult>()
        val firstReview = json.results.firstOrNull()
        val rating = firstReview?.author_details?.rating?.toFloat()
        val reviewText = firstReview?.content?.take(300)?.plus("…")
        return Pair(rating, reviewText)
    }

    private suspend fun getEpisodes(tvId: Int): List<Episode> {
        val seasonListUrl = "$mainUrl/tv/$tvId?api_key=$apiKey"
        val tvDetail = app.get(seasonListUrl).parsed<TmdbDetail>()
        val seasons = tvDetail.seasons ?: return emptyList()

        val episodes = mutableListOf<Episode>()
        for (season in seasons) {
            val seasonId = season.season_number
            val seasonDetailUrl = "$mainUrl/tv/$tvId/season/$seasonId?api_key=$apiKey"
            val seasonDetail = app.get(seasonDetailUrl).parsed<TmdbSeasonDetail>()
            seasonDetail.episodes.forEach {
                episodes.add(
                    Episode("https://vidsrc.cc/embed/$tvId", "S${seasonId}E${it.episode_number} - ${it.name}").apply {
                        posterUrl = "https://image.tmdb.org/t/p/w500${it.still_path}"
                        description = it.overview
                    }
                )
            }
        }
        return episodes
    }

    private suspend fun getRecommendations(id: Int, isTv: Boolean): List<SearchResponse> {
        val url = if (isTv) "$mainUrl/tv/$id/recommendations?api_key=$apiKey"
                  else "$mainUrl/movie/$id/recommendations?api_key=$apiKey"
        val json = app.get(url).parsed<TmdbSearchResult>()
        return json.results.map {
            MovieSearchResponse(
                it.title ?: it.name ?: "Unknown",
                "https://vidsrc.cc/embed/${it.id}",
                name,
                if (isTv) TvType.TvSeries else TvType.Movie
            ).apply {
                posterUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}"
                addYear(it.release_date?.take(4)?.toIntOrNull() ?: it.first_air_date?.take(4)?.toIntOrNull())
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val videoUrl = doc.select("video source").attr("src")
        if (videoUrl.isNotBlank()) {
            callback(
                ExtractorLink(
                    name = "Vidsrc",
                    source = "vidsrc.cc",
                    url = videoUrl,
                    referer = data,
                    quality = getQualityFromUrl(videoUrl),
                    isM3u8 = videoUrl.endsWith(".m3u8")
                )
            )
        }

        val subtitleElements = doc.select("track[kind=subtitles]")
        subtitleElements.forEach {
            val subUrl = it.attr("src")
            val lang = it.attr("label") ?: "Unknown"
            if (subUrl.isNotBlank()) {
                subtitleCallback(SubtitleFile(lang, subUrl))
            }
        }

        val altUrl = data.replace("vidsrc.cc", "vidplay.to")
        val vidplayLinks = VidplayExtractor().getUrl(altUrl, null)
        vidplayLinks.forEach { callback(it) }

        return true
    }

    private fun getQualityFromUrl(url: String): Int {
        return when {
            "1080" in url -> 1080
            "720" in url -> 720
            "480" in url -> 480
            "360" in url -> 360
            else -> Qualities.Unknown.value
        }
    }
}
