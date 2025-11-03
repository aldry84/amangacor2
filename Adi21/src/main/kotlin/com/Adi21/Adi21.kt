package com.Adi21

import com.lagradost.cloudstream3.*
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
            newMovieSearchResponse(
                name = it.title ?: "Unknown",
                url = "https://vidsrc.cc/embed/${it.id}",
                apiName = name,
                type = TvType.Movie
            ) {
                posterUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}"
                year = it.release_date?.take(4)?.toIntOrNull()
            }
        }

        val tvResults = tvJson.results.map {
            newTvSeriesSearchResponse(
                name = it.name ?: "Unknown",
                url = "https://vidsrc.cc/embed/${it.id}",
                apiName = name,
                type = TvType.TvSeries
            ) {
                posterUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}"
            }
        }

        return movieResults + tvResults
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val isTv = url.contains("/tv")
        val detailUrl = if (isTv) "$mainUrl/tv/$id?api_key=$apiKey" else "$mainUrl/movie/$id?api_key=$apiKey"
        val videoUrl = if (isTv) "$mainUrl/tv/$id/videos?api_key=$apiKey" else "$mainUrl/movie/$id/videos?api_key=$apiKey"

        val detail = app.get(detailUrl).parsed<TmdbDetail>()
        val trailerKey = app.get(videoUrl).parsed<TmdbVideoResult>().results.firstOrNull {
            it.site == "YouTube" && it.type == "Trailer"
        }?.key

        val trailer = trailerKey?.let { "https://www.youtube.com/watch?v=$it" }

        return newMovieLoadResponse(
            name = detail.title ?: detail.name ?: "Unknown",
            url = url,
            apiName = name,
            type = if (isTv) TvType.TvSeries else TvType.Movie,
            dataUrl = url
        ) {
            posterUrl = "https://image.tmdb.org/t/p/w500${detail.poster_path}"
            year = detail.release_date?.take(4)?.toIntOrNull() ?: detail.first_air_date?.take(4)?.toIntOrNull()
            plot = detail.overview
            tags = detail.genres.map { it.name }
            trailers = trailer?.let { mutableListOf(TrailerData("Trailer", it)) } ?: mutableListOf()
            episodes = mutableListOf(newEpisode(url) {
                name = "Watch"
            })
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
                newExtractorLink(
                    source = "vidsrc.cc",
                    name = "Vidsrc",
                    url = videoUrl,
                    referer = data,
                    quality = 720,
                    isM3u8 = videoUrl.endsWith(".m3u8")
                )
            )
        }
        return true
    }
}
