package com.Adicinema

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

class Adicinema : MainAPI() {
    // === KONFIGURASI API ===
    override var mainUrl = "https://api.themoviedb.org/3"
    private val tmdbApiKey = "1d8730d33fc13ccbd8cdaaadb74892c7" // KUNCI TMDB Anda
    private val fmoviesApiUrl = "https://fmoviesto.cc" // API Streaming (Fmovies)

    override var name = "Adicinema (TMDB)"
    override val hasMainPage = false
    override val hasQuickSearch = true
    override val instantLinkLoading = false
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Headers yang sering diperlukan oleh situs streaming
    private val apiHeaders = mapOf(
        "x-requested-with" to "XMLHttpRequest"
    )

    private fun getTmdbApiUrl(path: String, query: String = ""): String {
        return "$mainUrl$path?api_key=$tmdbApiKey&language=en-US$query"
    }

    // === PENCARIAN (TMDB MULTI-SEARCH) ===
    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query) ?: emptyList()

    override suspend fun search(query: String): List<SearchResponse>? {
        val encoded = URLEncoder.encode(query, "utf-8")
        val url = getTmdbApiUrl("/search/multi", "&query=$encoded")
        
        val response = app.get(url).parsedSafe<TmdbSearch>()?.results
        
        return response?.mapNotNull { it.toSearchResponse(this) }
    }

    // === LOAD DETAIL FILM / SERIAL (TMDB) ===
    override suspend fun load(url: String): LoadResponse {
        val parts = url.split("|||")
        val tmdbID = parts[0]
        val mediaType = parts[1] // 'movie' atau 'tv'
        
        val detailsUrl = getTmdbApiUrl("/$mediaType/$tmdbID", "&append_to_response=videos,credits")

        val detail = app.get(detailsUrl).parsedSafe<TmdbDetail>()
            ?: throw ErrorLoadingException("TMDB detail not found for $mediaType/$tmdbID")

        val title = detail.name ?: detail.title ?: throw ErrorLoadingException("Title not found in TMDB")
        val year = detail.release_date?.substringBefore("-")?.toIntOrNull() ?: detail.first_air_date?.substringBefore("-")?.toIntOrNull()
        val plot = detail.overview
        val tags = detail.genres?.mapNotNull { it.name }
        val score = Score.from10(detail.vote_average?.toFloatOrNull())
        val posterUrl = detail.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
        
        val trailer = detail.videos?.results?.firstOrNull { it.type == "Trailer" && it.site == "YouTube" }
        
        val cast = detail.credits?.cast?.take(10)?.mapNotNull { 
            Cast(it.name ?: return@mapNotNull null, it.character ?: return@mapNotNull null, null, null)
        }

        // BAGIAN KRITIS: Mencari ID Fmovies berdasarkan judul TMDB
        val fmoviesID = findFmoviesID(title) ?: throw ErrorLoadingException("No Fmovies ID found for $title (Streaming API mungkin down/berubah)")
        
        return if (mediaType == "tv") {
            val episodes = mutableListOf<Episode>()
            val totalSeasons = detail.number_of_seasons ?: 1

            for (season in 1..totalSeasons) {
                val seasonUrl = getTmdbApiUrl("/tv/$tmdbID/season/$season")
                val seasonDetail = app.get(seasonUrl).parsedSafe<TmdbSeason>()

                seasonDetail?.episodes?.forEach { ep ->
                    val episodeNum = ep.episode_number ?: 1
                    val epData = EpisodeData(tmdbID, season, episodeNum, fmoviesID)
                    episodes.add(
                        newEpisode(epData.toJson()) {
                            this.name = ep.name
                            this.season = season
                            this.episode = episodeNum
                        }
                    )
                }
            }

            newTvSeriesLoadResponse(title, tmdbID, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
                this.tags = tags
                this.score = score
                this.trailer = trailer?.key?.let { "https://www.youtube.com/watch?v=$it" }
                this.set = cast
            }
        } else {
            val epData = EpisodeData(tmdbID, 1, 1, fmoviesID)
            newMovieLoadResponse(title, tmdbID, TvType.Movie, epData.toJson()) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
                this.tags = tags
                this.score = score
                this.trailer = trailer?.key?.let { "https://www.youtube.com/watch?v=$it" }
                this.set = cast
            }
        }
    }

    // === PENGAMBILAN LINK STREAMING (FMOVIES) ===
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val episodeData = parseJson<EpisodeData>(data)
        val fmoviesID = episodeData.streamingPath
        val seasonNum = episodeData.seasonNum
        val episodeNum = episodeData.episodeNum

        val referer = "$fmoviesApiUrl/"

        // STREAMING
        val streams = app.get(
            "$fmoviesApiUrl/wefeed-h5-bff/web/subject/play?subjectId=$fmoviesID&se=$seasonNum&ep=$episodeNum",
            referer = referer,
            headers = apiHeaders
        ).parsedSafe<Media>()?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.forEach { src ->
            val url = src.url ?: return@forEach
            callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    url,
                    INFER_TYPE
                ) {
                    this.referer = "$fmoviesApiUrl/"
                    this.quality = src.resolutions?.let { getQualityFromName(it) } ?: Qualities.Unknown.value
                }
            )
        }

        // SUBTITLE
        val first = streams?.firstOrNull()
        val id = first?.id
        val format = first?.format

        if (id != null && format != null) {
            app.get(
                "$fmoviesApiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=$fmoviesID",
                referer = referer,
                headers = apiHeaders
            ).parsedSafe<Media>()?.data?.captions?.forEach { sub ->
                val url = sub.url ?: return@forEach
                subtitleCallback(
                    newSubtitleFile(sub.lanName ?: "English", url)
                )
            }
        }

        return true
    }

    // === FUNGSI PEMBANTU (Mencari ID Fmovies menggunakan Judul TMDB) ===
    private suspend fun findFmoviesID(query: String): String? {
        for (type in listOf("0", "1")) {
            val bodyWithType = mapOf(
                "keyword" to query,
                "page" to "1",
                "perPage" to "1",
                "subjectType" to type
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

            val result = app.post(
                "$fmoviesApiUrl/wefeed-h5-bff/web/subject/search", 
                requestBody = bodyWithType,
                headers = apiHeaders
            )
                .parsedSafe<Media>()
                ?.data
                ?.items
                ?.firstOrNull()
                ?.subjectId

            if (result != null) return result
        }
        return null
    }

    // === DATA CLASS (TMDB) ===
    data class TmdbSearch(
        @JsonProperty("results") val results: List<TmdbItem>? = null
    )
    
    data class TmdbItem(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("media_type") val media_type: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("release_date") val release_date: String? = null,
        @JsonProperty("first_air_date") val first_air_date: String? = null,
        @JsonProperty("poster_path") val poster_path: String? = null,
    ) {
        fun toSearchResponse(provider: Adicinema): SearchResponse? {
            if (id == null || media_type == null || (title.isNullOrBlank() && name.isNullOrBlank())) return null
            if (media_type != "movie" && media_type != "tv") return null

            val finalTitle = title ?: name ?: return null
            val finalYear = release_date?.substringBefore("-")?.toIntOrNull() ?: first_air_date?.substringBefore("-")?.toIntOrNull()
            val type = if (media_type == "tv") TvType.TvSeries else TvType.Movie
            
            val url = "$id|||$media_type"

            return provider.newMovieSearchResponse(finalTitle, url, type, true) {
                this.posterUrl = poster_path?.let { "https://image.tmdb.org/t/p/w185$it" }
                this.year = finalYear
            }
        }
    }

    data class TmdbDetail(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("release_date") val release_date: String? = null,
        @JsonProperty("first_air_date") val first_air_date: String? = null,
        @JsonProperty("poster_path") val poster_path: String? = null,
        @JsonProperty("genres") val genres: List<TmdbGenre>? = null,
        @JsonProperty("vote_average") val vote_average: String? = null,
        @JsonProperty("number_of_seasons") val number_of_seasons: Int? = null,
        @JsonProperty("videos") val videos: TmdbVideos? = null,
        @JsonProperty("credits") val credits: TmdbCredits? = null
    )

    data class TmdbGenre(@JsonProperty("name") val name: String? = null)
    
    data class TmdbVideos(@JsonProperty("results") val results: List<TmdbVideoItem>? = null)
    data class TmdbVideoItem(
        @JsonProperty("key") val key: String? = null,
        @JsonProperty("site") val site: String? = null,
        @JsonProperty("type") val type: String? = null
    )
    
    data class TmdbCredits(@JsonProperty("cast") val cast: List<TmdbCastItem>? = null)
    data class TmdbCastItem(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("character") val character: String? = null,
    )

    data class TmdbSeason(
        @JsonProperty("episodes") val episodes: List<TmdbEpisode>? = null
    )

    data class TmdbEpisode(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("episode_number") val episode_number: Int? = null
    )

    // === DATA CLASS STREAMING (FMOVIES) ===
    data class EpisodeData(
        val tmdbID: String,
        val seasonNum: Int,
        val episodeNum: Int,
        val streamingPath: String // Ini adalah subjectId Fmovies
    )

    data class Media(
        @JsonProperty("data") val data: Data? = null,
    ) {
        data class Data(
            @JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
            @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
            @JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf(),
        ) {
            data class Streams(
                @JsonProperty("id") val id: String? = null,
                @JsonProperty("format") val format: String? = null,
                @JsonProperty("url") val url: String? = null,
                @JsonProperty("resolutions") val resolutions: String? = null,
            )

            data class Captions(
                @JsonProperty("lanName") val lanName: String? = null,
                @JsonProperty("url") val url: String? = null,
            )
        }
    }

    data class Items(
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("cover") val cover: Cover? = null,
    ) {
        data class Cover(
            @JsonProperty("url") val url: String? = null,
        )
    }
}
