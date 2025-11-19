package com.AdiDrakor

import com.fasterxml.jackson.annotation.JsonProperty
// Pastikan Anda menyalin file helper Adicinemax21 ke package ini atau import dari package aslinya
import com.Adicinemax21.Adicinemax21Extractor.invokeIdlix
import com.Adicinemax21.Adicinemax21Extractor.invokeVidsrccc
import com.Adicinemax21.Adicinemax21Extractor.invokeVidsrc
import com.Adicinemax21.Adicinemax21Extractor.invokeWatchsomuch
import com.Adicinemax21.Adicinemax21Extractor.invokeVixsrc
import com.Adicinemax21.Adicinemax21Extractor.invokeVidlink
import com.Adicinemax21.Adicinemax21Extractor.invokeVidfast
import com.Adicinemax21.Adicinemax21Extractor.invokeMapple
import com.Adicinemax21.Adicinemax21Extractor.invokeWyzie
import com.Adicinemax21.Adicinemax21Extractor.invokeVidsrccx
import com.Adicinemax21.Adicinemax21Extractor.invokeSuperembed
import com.Adicinemax21.Adicinemax21Extractor.invokeVidrock
import com.Adicinemax21.Adicinemax21 // Import class parent jika diperlukan referensi statis
import com.Adicinemax21.getDate // Import fungsi utilitas
import com.Adicinemax21.isUpcoming // Import fungsi utilitas

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink

open class AdiDrakor : TmdbProvider() {
    override var name = "AdiDrakor"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama 
    )

    val wpRedisInterceptor by lazy { CloudflareKiller() }

    companion object {
        private const val tmdbAPI = "https://api.themoviedb.org/3"
        private const val apiKey = "b030404650f279792a8d3287232358e3"
        
        // Menggunakan fungsi helper dari Adicinemax21Utils atau dicopy ke file Utils baru
        fun getType(t: String?): TvType {
            return when (t) {
                "movie" -> TvType.Movie
                else -> TvType.TvSeries
            }
        }
        
        fun getStatus(t: String?): ShowStatus {
            return when (t) {
                "Returning Series" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    // MENGUBAH MAIN PAGE AGAR SEPERTI KISSKH (Fokus Asia & Kategori Spesifik)
    override val mainPage = mainPageOf(
        // K-Drama (Korean)
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=ko&sort_by=popularity.desc" to "Top K-Drama",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=ko&air_date.lte=${getDate().today}&sort_by=first_air_date.desc" to "Latest K-Drama",
        
        // C-Drama (Chinese)
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=zh&sort_by=popularity.desc" to "Top C-Drama",
        
        // Anime
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_keywords=210024|222243&sort_by=popularity.desc" to "Anime Popular",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_keywords=210024|222243&air_date.lte=${getDate().today}&sort_by=first_air_date.desc" to "Anime Latest Update",
        
        // Movies (Global & Asian Mixed)
        "$tmdbAPI/movie/popular?api_key=$apiKey&region=US" to "Movie Popular",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_original_language=ko|zh|ja&sort_by=release_date.desc" to "Asian Movies Latest",
        
        // General TV
        "$tmdbAPI/tv/popular?api_key=$apiKey&region=US&with_original_language=en" to "TVSeries Popular",
    )

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    }

    private fun getOriImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }

    // MODIFIKASI UTAMA: Menggunakan isHorizontalImages = true ala Kisskh
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val adultQuery = if (settingsForProvider.enableAdult) "" else "&without_keywords=190370|13059|226161|195669"
        val type = if (request.data.contains("/movie")) "movie" else "tv"
        
        val home = app.get("${request.data}$adultQuery&page=$page")
            .parsedSafe<Adicinemax21.Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse(type)
            } ?: throw ErrorLoadingException("Invalid Json reponse")

        // Perubahan Disini: Menggunakan HomePageList dengan isHorizontalImages = true
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true // Style ala Kisskh
            ),
            hasNext = true
        )
    }

    // Fungsi Helper untuk SearchResponse (Disalin dari Adicinemax21)
    private fun Adicinemax21.Media.toSearchResponse(type: String? = null): SearchResponse? {
        return newMovieSearchResponse(
            title ?: name ?: originalTitle ?: return null,
            Adicinemax21.Data(id = id, type = mediaType ?: type).toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = getImageUrl(posterPath)
            this.score = Score.from10(voteAverage)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        return app.get("$tmdbAPI/search/multi?api_key=$apiKey&language=en-US&query=$query&page=1&include_adult=${settingsForProvider.enableAdult}")
            .parsedSafe<Adicinemax21.Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse()
            }
    }

    // BAGIAN LOAD & LOADLINKS TETAP SAMA DENGAN ADICINEMAX21
    // Agar fungsionalitas player tetap berjalan dengan baik
    override suspend fun load(url: String): LoadResponse? {
        val data = try {
            if (url.startsWith("https://www.themoviedb.org/")) {
                val segments = url.removeSuffix("/").split("/")
                val id = segments.lastOrNull()?.toIntOrNull()
                val type = when {
                    url.contains("/movie/") -> "movie"
                    url.contains("/tv/") -> "tv"
                    else -> null
                }
                Adicinemax21.Data(id = id, type = type)
            } else {
                parseJson<Adicinemax21.Data>(url)
            }
        } catch (e: Exception) {
            throw ErrorLoadingException("Invalid URL or JSON data: ${e.message}")
        } ?: throw ErrorLoadingException("Invalid data format")

        val type = getType(data.type)
        val append = "alternative_titles,credits,external_ids,keywords,videos,recommendations"
        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$apiKey&append_to_response=$append"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$apiKey&append_to_response=$append"
        }
        val res = app.get(resUrl).parsedSafe<Adicinemax21.MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")

        val title = res.title ?: res.name ?: return null
        val poster = getOriImageUrl(res.posterPath)
        val bgPoster = getOriImageUrl(res.backdropPath)
        val orgTitle = res.originalTitle ?: res.originalName ?: return null
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.first()?.toIntOrNull()
        val genres = res.genres?.mapNotNull { it.name }

        val isCartoon = genres?.contains("Animation") ?: false
        val isAnime = isCartoon && (res.original_language == "zh" || res.original_language == "ja")
        val isAsian = !isAnime && (res.original_language == "zh" || res.original_language == "ko")
        val isBollywood = res.production_countries?.any { it.name == "India" } ?: false

        val keywords = res.keywords?.results?.mapNotNull { it.name }.orEmpty()
            .ifEmpty { res.keywords?.keywords?.mapNotNull { it.name } }

        val actors = res.credits?.cast?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: cast.originalName
                    ?: return@mapNotNull null, getImageUrl(cast.profilePath)
                ), roleString = cast.character
            )
        } ?: return null
        val recommendations =
            res.recommendations?.results?.mapNotNull { media -> media.toSearchResponse() }

        val trailer = res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" }

        return if (type == TvType.TvSeries) {
            val lastSeason = res.last_episode_to_air?.season_number
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey")
                    .parsedSafe<Adicinemax21.MediaDetailEpisodes>()?.episodes?.map { eps ->
                        newEpisode(
                            data = Adicinemax21.LinkData(
                                data.id,
                                res.external_ids?.imdb_id,
                                res.external_ids?.tvdb_id,
                                data.type,
                                eps.seasonNumber,
                                eps.episodeNumber,
                                title = title,
                                year = season.airDate?.split("-")?.first()?.toIntOrNull(),
                                orgTitle = orgTitle,
                                isAnime = isAnime,
                                airedYear = year,
                                lastSeason = lastSeason,
                                epsTitle = eps.name,
                                jpTitle = res.alternative_titles?.results?.find { it.iso_3166_1 == "JP" }?.title,
                                date = season.airDate,
                                airedDate = res.releaseDate
                                    ?: res.firstAirDate,
                                isAsian = isAsian,
                                isBollywood = isBollywood,
                                isCartoon = isCartoon
                            ).toJson()
                        ) {
                            this.name =
                                eps.name + if (isUpcoming(eps.airDate)) " • [UPCOMING]" else ""
                            this.season = eps.seasonNumber
                            this.episode = eps.episodeNumber
                            this.posterUrl = getImageUrl(eps.stillPath)
                            this.score = Score.from10(eps.voteAverage)
                            this.description = eps.overview
                        }.apply {
                            this.addDate(eps.airDate)
                        }
                    }
            }?.flatten() ?: listOf()
            newTvSeriesLoadResponse(
                title,
                url,
                if (isAnime) TvType.Anime else TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.tags = keywords.takeIf { !it.isNullOrEmpty() } ?: genres
                this.score = Score.from10(res.vote_average?.toString())
                this.showStatus = getStatus(res.status)
                this.recommendations = recommendations
                this.actors = actors
                this.contentRating = fetchContentRating(data.id, "US")
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                Adicinemax21.LinkData(
                    data.id,
                    res.external_ids?.imdb_id,
                    res.external_ids?.tvdb_id,
                    data.type,
                    title = title,
                    year = year,
                    orgTitle = orgTitle,
                    isAnime = isAnime,
                    jpTitle = res.alternative_titles?.results?.find { it.iso_3166_1 == "JP" }?.title,
                    airedDate = res.releaseDate
                        ?: res.firstAirDate,
                    isAsian = isAsian,
                    isBollywood = isBollywood
                ).toJson(),
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.comingSoon = isUpcoming(releaseDate)
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = keywords.takeIf { !it.isNullOrEmpty() } ?: genres
                this.score = Score.from10(res.vote_average?.toString())
                this.recommendations = recommendations
                this.actors = actors
                this.contentRating = fetchContentRating(data.id, "US")
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val res = parseJson<Adicinemax21.LinkData>(data)

        // Memanggil semua extractor dari Adicinemax21
        // Catatan: Pastikan fungsi-fungsi ini di-import dengan benar dari file Extractor Adicinemax21
        runAllAsync(
            {
                invokeIdlix(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                invokeVidsrccc(
                    res.id,
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                invokeVidsrc(
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                invokeWatchsomuch(
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback
                )
            },
            {
                invokeVixsrc(res.id, res.season, res.episode, callback)
            },
            {
                invokeVidlink(res.id, res.season, res.episode, callback)
            },
            {
                invokeVidfast(res.id, res.season, res.episode, subtitleCallback, callback)
            },
            {
                invokeMapple(res.id, res.season, res.episode, subtitleCallback, callback)
            },
            {
                invokeWyzie(res.id, res.season, res.episode, subtitleCallback)
            },
            {
                invokeVidsrccx(res.id, res.season, res.episode, callback)
            },
            {
                invokeSuperembed(
                    res.id,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                invokeVidrock(
                    res.id,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            }
        )

        return true
    }
}
