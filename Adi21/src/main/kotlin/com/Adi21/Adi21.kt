package com.Adi21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.encode
import com.lagradost.cloudstream3.utils.ExtractorLink

class Adi21 : MainAPI() {
    override var mainUrl = "https://api.themoviedb.org/3"
    override var name = "TMDB + Vidsrc"
    override var lang = "id"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val apiKey = "1cfadd9dbfc534abf6de40e1e7eaf4c7"

    override suspend fun search(query: String): List<SearchResponse> {
        val movieUrl = "$mainUrl/search/movie?query=${query.encode()}&api_key=$apiKey"
        val tvUrl = "$mainUrl/search/tv?query=${query.encode()}&api_key=$apiKey"

        val movieJson = app.get(movieUrl).parsed<TmdbSearchResult>()
        val tvJson = app.get(tvUrl).parsed<TmdbSearchResult>()

        val movieResults = movieJson.results.map {
            MovieSearchResponse(
                name = it.title ?: "Unknown",
                url = "https://vidsrc.cc/embed/${it.id}",
                posterUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}",
                year = it.release_date?.take(4)?.toIntOrNull(),
                apiName = name,
                type = TvType.Movie
            )
        }

        val tvResults = tvJson.results.map {
            TvSeriesSearchResponse(
                name = it.name ?: "Unknown",
                url = "https://vidsrc.cc/embed/${it.id}",
                posterUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}",
                apiName = name,
                type = TvType.TvSeries
            )
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

        return MovieLoadResponse(
            name = detail.title ?: detail.name ?: "Unknown",
            url = url,
            posterUrl = "https://image.tmdb.org/t/p/w500${detail.poster_path}",
            year = detail.release_date?.take(4)?.toIntOrNull() ?: detail.first_air_date?.take(4)?.toIntOrNull(),
            plot = detail.overview,
            rating = detail.vote_average.toFloatOrNull(),
            tags = detail.genres.map { it.name },
            type = if (isTv) TvType.TvSeries else TvType.Movie,
            trailerUrl = trailer,
            episodes = listOf(Episode(name = "Watch", url = url))
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(data).document
        val videoUrl = doc.select("video source").attr("src")
        if (videoUrl.isNotBlank()) {
            callback(
                ExtractorLink(
                    name = "Vidsrc",
                    source = "vidsrc.cc",
                    url = videoUrl,
                    referer = data,
                    quality = Qualities.Unknown.value,
                    isM3u8 = videoUrl.endsWith(".m3u8")
                )
            )
        }
    }
}
