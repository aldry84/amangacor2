package com.AdiDrakor

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.AdiDrakor.AdiDrakorExtractor.invokeAllExtractors

open class AdiDrakor : TmdbProvider() {
    override var name = "AdiDrakor"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    
    private val kisskhUrl = "https://kisskh.ovh"

    override val supportedTypes = setOf(
        TvType.AsianDrama,
        TvType.Anime,
        TvType.TvSeries,
        TvType.Movie
    )

    companion object {
        private const val tmdbAPI = "https://api.themoviedb.org/3"
        private const val apiKey = "b030404650f279792a8d3287232358e3"

        fun getType(t: String?): TvType {
            return when (t) {
                "movie" -> TvType.Movie
                else -> TvType.TvSeries
            }
        }
    }

    // === 1. KATEGORI & TAMPILAN ALA KISSKH ===
    override val mainPage = mainPageOf(
        "$kisskhUrl/api/DramaList/List?page=1&type=0&sub=0&country=0&status=0&order=2&pageSize=40" to "Latest",
        "$kisskhUrl/api/DramaList/List?page=1&type=0&sub=0&country=2&status=0&order=1&pageSize=40" to "Top K-Drama",
        "$kisskhUrl/api/DramaList/List?page=1&type=0&sub=0&country=1&status=0&order=1&pageSize=40" to "Top C-Drama",
        "$kisskhUrl/api/DramaList/List?page=1&type=3&sub=0&country=0&status=0&order=1&pageSize=40" to "Anime Popular",
        "$kisskhUrl/api/DramaList/List?page=1&type=4&sub=0&country=0&status=0&order=1&pageSize=40" to "Hollywood Popular",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.replace("page=1", "page=$page")
        val responseText = app.get(url).text
        val results = tryParseJson<KisskhResponse>(responseText)?.data ?: emptyList()

        val home = results.mapNotNull { media ->
            newMovieSearchResponse(
                media.title ?: return@mapNotNull null,
                // Menandai data ini berasal dari Kisskh agar diload via Bridge nanti
                Data(title = media.title, year = media.id, type = "kisskh_bridge").toJson(),
                TvType.AsianDrama,
            ) {
                this.posterUrl = media.thumbnail
            }
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true // Mengikuti gaya Kisskh
            ),
            hasNext = true
        )
    }

    // === 2. SEARCH VIA TMDB ===
    override suspend fun search(query: String): List<SearchResponse>? {
        return app.get("$tmdbAPI/search/multi?api_key=$apiKey&language=en-US&query=$query&page=1&include_adult=${settingsForProvider.enableAdult}")
            .parsedSafe<TmdbResults>()?.results?.mapNotNull { media ->
                media.toSearchResponse()
            }
    }

    private fun TmdbMedia.toSearchResponse(): SearchResponse? {
        return newMovieSearchResponse(
            title ?: name ?: return null,
            Data(id = id, type = mediaType ?: "tv").toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = "https://image.tmdb.org/t/p/w500$posterPath"
            this.score = Score.from10(voteAverage)
        }
    }

    // === 3. LOAD: JEMBATAN (BRIDGE) KISSKH -> TMDB ===
    override suspend fun load(url: String): LoadResponse? {
        val data = try { parseJson<Data>(url) } catch (e: Exception) { null }

        // [BRIDGE] Cari ID TMDB jika data dari Kisskh
        if (data?.type == "kisskh_bridge" && data.title != null) {
            val cleanTitle = data.title.replace(Regex("\\(.*?\\)"), "").trim()
            val searchUrl = "$tmdbAPI/search/multi?api_key=$apiKey&query=$cleanTitle&page=1"
            val searchRes = app.get(searchUrl).parsedSafe<TmdbResults>()
            
            val bestMatch = searchRes?.results?.firstOrNull() 
                ?: throw ErrorLoadingException("Konten tidak ditemukan di database TMDB.")

            return load(Data(id = bestMatch.id, type = bestMatch.mediaType ?: "tv").toJson())
        }

        // [NORMAL] Load Metadata TMDB
        val tmdbId = data?.id ?: throw ErrorLoadingException("Invalid ID")
        val type = getType(data.type)
        val apiType = if (type == TvType.Movie) "movie" else "tv"

        val append = "alternative_titles,credits,external_ids,keywords,videos,recommendations"
        val resUrl = "$tmdbAPI/$apiType/$tmdbId?api_key=$apiKey&append_to_response=$append"
        val res = app.get(resUrl).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Gagal memuat metadata TMDB")

        val title = res.title ?: res.name ?: return null
        val poster = if (res.posterPath != null) "https://image.tmdb.org/t/p/original/${res.posterPath}" else null
        val bgPoster = if (res.backdropPath != null) "https://image.tmdb.org/t/p/original/${res.backdropPath}" else null
        val year = (res.releaseDate ?: res.firstAirDate)?.split("-")?.first()?.toIntOrNull()
        
        val actors = res.credits?.cast?.mapNotNull { cast ->
            ActorData(Actor(cast.name ?: return@mapNotNull null, 
            if(cast.profilePath != null) "https://image.tmdb.org/t/p/w500${cast.profilePath}" else null), roleString = cast.character)
        }
        
        val trailer = res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" }

        if (type == TvType.TvSeries) {
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/$apiType/$tmdbId/season/${season.seasonNumber}?api_key=$apiKey")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                        newEpisode(
                            data = LinkData(tmdbId, res.external_ids?.imdb_id, type.name, season.seasonNumber, eps.episodeNumber, title = title, year = year).toJson()
                        ) {
                            this.name = eps.name
                            this.season = eps.seasonNumber
                            this.episode = eps.episodeNumber
                            this.posterUrl = if(eps.stillPath != null) "https://image.tmdb.org/t/p/w500${eps.stillPath}" else null
                            this.description = eps.overview
                            this.addDate(eps.airDate)
                        }
                    }
            }?.flatten() ?: listOf()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.actors = actors
                this.recommendations = res.recommendations?.results?.mapNotNull { it.toSearchResponse() }
                addTrailer(trailer)
                addTMDbId(tmdbId.toString())
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, 
                LinkData(tmdbId, res.external_ids?.imdb_id, type.name, title = title, year = year).toJson()) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.actors = actors
                this.recommendations = res.recommendations?.results?.mapNotNull { it.toSearchResponse() }
                addTrailer(trailer)
                addTMDbId(tmdbId.toString())
            }
        }
    }

    // === 4. LOAD LINKS: MEMANGGIL SEMUA EXTRACTOR ===
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LinkData>(data)
        // Memanggil fungsi sentral di AdiDrakorExtractor
        invokeAllExtractors(res, subtitleCallback, callback)
        return true
    }
    
    data class Data(val id: Int? = null, val type: String? = null, val title: String? = null, val year: Int? = null)
    data class LinkData(
        val id: Int? = null, val imdbId: String? = null, val type: String? = null, 
        val season: Int? = null, val episode: Int? = null, 
        val title: String? = null, val year: Int? = null
    )
}
