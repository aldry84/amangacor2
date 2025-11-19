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
import com.AdiDrakor.AdiDrakorExtractor.invokeIdlix
import com.AdiDrakor.AdiDrakorExtractor.invokeVidsrccc
import com.AdiDrakor.AdiDrakorExtractor.invokeVidsrc
import com.AdiDrakor.AdiDrakorExtractor.invokeVixsrc
import com.AdiDrakor.AdiDrakorExtractor.invokeVidlink
import com.AdiDrakor.AdiDrakorExtractor.invokeVidfast
import com.AdiDrakor.AdiDrakorExtractor.invokeMapple
import com.AdiDrakor.AdiDrakorExtractor.invokeWyzie
import com.AdiDrakor.AdiDrakorExtractor.invokeVidsrccx
import com.AdiDrakor.AdiDrakorExtractor.invokeSuperembed
import com.AdiDrakor.AdiDrakorExtractor.invokeVidrock

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

    // === 1. MAIN PAGE MENGGUNAKAN API KISSKH ===
    override val mainPage = mainPageOf(
        "$kisskhUrl/api/DramaList/List?page=1&type=0&sub=0&country=0&status=0&order=1&pageSize=40" to "Drama Populer",
        "$kisskhUrl/api/DramaList/List?page=1&type=0&sub=0&country=0&status=0&order=2&pageSize=40" to "Drama Terbaru",
        "$kisskhUrl/api/DramaList/List?page=1&type=1&sub=0&country=0&status=0&order=1&pageSize=40" to "Ongoing Series",
        "$kisskhUrl/api/DramaList/List?page=1&type=3&sub=0&country=0&status=0&order=1&pageSize=40" to "Anime Populer",
        "$kisskhUrl/api/DramaList/List?page=1&type=4&sub=0&country=0&status=0&order=1&pageSize=40" to "Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Handle Pagination Kisskh
        val url = request.data.replace("page=1", "page=$page")
        val responseText = app.get(url).text
        val results = tryParseJson<KisskhResponse>(responseText)?.data ?: emptyList()

        val home = results.mapNotNull { media ->
            newMovieSearchResponse(
                media.title ?: return@mapNotNull null,
                // PENTING: Kita simpan Title di sini karena kita belum punya TMDB ID
                Data(title = media.title, year = media.id, type = "kisskh_bridge").toJson(),
                TvType.AsianDrama,
            ) {
                this.posterUrl = media.thumbnail
                // Kisskh menggunakan poster vertikal, set false agar rasio gambar benar
                this.posterHeaders = mapOf("Referer" to kisskhUrl)
            }
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false 
            ),
            hasNext = true
        )
    }

    // === 2. SEARCH TETAP VIA TMDB (Agar sinkron dengan Extractor) ===
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

    // === 3. LOAD: LOGIKA JEMBATAN (BRIDGE) ===
    override suspend fun load(url: String): LoadResponse? {
        val data = try { parseJson<Data>(url) } catch (e: Exception) { null }

        // [BRIDGE] Jika data datang dari Kisskh (type="kisskh_bridge"), cari ID-nya di TMDB
        if (data?.type == "kisskh_bridge" && data.title != null) {
            // Bersihkan judul dari karakter aneh agar pencarian akurat
            val cleanTitle = data.title.replace(Regex("\\(.*?\\)"), "").trim()
            val searchUrl = "$tmdbAPI/search/multi?api_key=$apiKey&query=$cleanTitle&page=1"
            val searchRes = app.get(searchUrl).parsedSafe<TmdbResults>()
            
            // Ambil hasil pertama (paling relevan)
            val bestMatch = searchRes?.results?.firstOrNull() 
                ?: throw ErrorLoadingException("Konten '${data.title}' tidak ditemukan di database TMDB.")

            // Panggil ulang load() dengan ID TMDB yang valid
            return load(Data(id = bestMatch.id, type = bestMatch.mediaType ?: "tv").toJson())
        }

        // [NORMAL] Jika sudah punya TMDB ID, load detail seperti biasa
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LinkData>(data)
        
        // Memanggil Extractor (Idlix, Vidsrc, dll)
        runAllAsync(
            { invokeIdlix(res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokeVidsrccc(res.id, res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeVidsrc(res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeVixsrc(res.id, res.season, res.episode, callback) },
            { invokeVidlink(res.id, res.season, res.episode, callback) },
            { invokeVidfast(res.id, res.season, res.episode, subtitleCallback, callback) },
            { invokeMapple(res.id, res.season, res.episode, subtitleCallback, callback) },
            { invokeWyzie(res.id, res.season, res.episode, subtitleCallback) },
            { invokeVidsrccx(res.id, res.season, res.episode, callback) },
            { invokeSuperembed(res.id, res.season, res.episode, subtitleCallback, callback) },
            { invokeVidrock(res.id, res.season, res.episode, subtitleCallback, callback) }
        )
        return true
    }
    
    // Data Class Internal
    data class Data(val id: Int? = null, val type: String? = null, val title: String? = null, val year: Int? = null)
    data class LinkData(
        val id: Int? = null, val imdbId: String? = null, val type: String? = null, 
        val season: Int? = null, val episode: Int? = null, 
        val title: String? = null, val year: Int? = null
    )
}
