package com.AdicinemaxNew

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.AdicinemaxNew.Utils.getApiBase
import com.AdicinemaxNew.Utils.getImageUrl
import com.AdicinemaxNew.AdicinemaxExtractor.invokeSubtitleAPI
import com.AdicinemaxNew.AdicinemaxExtractor.invokeVidSrcXyz
import com.AdicinemaxNew.AdicinemaxExtractor.invokeXPrimeAPI
import com.AdicinemaxNew.AdicinemaxExtractor.invokeVidsrccc
import com.AdicinemaxNew.AdicinemaxExtractor.invokeHubCloudGDFlix
import com.AdicinemaxNew.AdicinemaxExtractor.invokeTorrentio

// ASUMSI: Konstanta TMDB_API dan TMDB Proxy berada di BuildConfig

open class AdicinemaxNew(val sharedPref: SharedPreferences? = null) : TmdbProvider() {
    override var name = "Adicinemax"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    
    val token: String? = sharedPref?.getString("token", null) 
    val langCode = sharedPref?.getString("tmdb_language_code", "en-US") ?: "en-US"
    
    companion object {
        const val apiKey = BuildConfig.TMDB_API
        const val OFFICIAL_TMDB_URL = "https://api.themoviedb.org/3"
    }
    
    override val mainPage = mainPageOf(
        "/trending/all/day?api_key=$apiKey&region=US" to "Trending Now",
        "/movie/popular?api_key=$apiKey&region=US" to "Popular Movies",
        "/tv/popular?api_key=$apiKey&region=US" to "Popular TV Shows"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val tmdbAPI = getApiBase()
        val type = if (request.data.contains("/movie")) "movie" else "tv"
        val home = app.get("$tmdbAPI${request.data}&language=$langCode&page=$page", timeout = 10000)
            .parsedSafe<com.lagradost.cloudstream3.StreamPlay.Results>()?.results?.mapNotNull { media ->
                (media as? com.lagradost.cloudstream3.StreamPlay.Media)?.toSearchResponse(type) 
            } ?: throw ErrorLoadingException("Invalid Json response")
        return newHomePageResponse(request.name, home)
    }
    
    private fun com.lagradost.cloudstream3.StreamPlay.Media.toSearchResponse(type: String? = null): SearchResponse? {
        // Harus menggunakan TmdbProvider.getImageUrl atau menuliskannya di sini. Kita tulis di sini:
        return newMovieSearchResponse(
            title ?: name ?: originalTitle ?: return null,
            LinkData(id = id, type = mediaType ?: type).toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = getImageUrl(posterPath)
        }
    }


    override suspend fun load(url: String): LoadResponse? {
        val tmdbAPI = getApiBase()
        val data = parseJson<LinkData>(url)
        val type = com.lagradost.cloudstream3.StreamPlay.getType(data.type)
        val append = "alternative_titles,credits,external_ids,videos,recommendations"

        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$apiKey&language=$langCode&append_to_response=$append"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$apiKey&language=$langCode&append_to_response=$append"
        }

        val res = app.get(resUrl).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")
        val title = res.title ?: res.name ?: return null
        
        val imdbId = res.external_ids?.imdb_id
        val year = res.releaseDate?.split("-")?.first()?.toIntOrNull()
        
        val linkData = LinkData(
            id = data.id,
            imdbId = imdbId,
            type = data.type,
            season = null,
            episode = null,
            title = title,
            year = year
        )

        if (type == TvType.TvSeries) {
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey&language=$langCode")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                        newEpisode(
                            linkData.copy(
                                season = eps.seasonNumber,
                                episode = eps.episodeNumber
                            ).toJson()
                        ) {
                            this.name = eps.name
                            this.season = eps.seasonNumber
                            this.episode = eps.episodeNumber
                        }.apply {
                            this.addDate(eps.airDate)
                        }
                    }
            }?.flatten() ?: listOf()
            
             return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                 addTMDbId(data.id.toString())
                 addImdbId(imdbId)
             }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, linkData.toJson()) {
                addTMDbId(data.id.toString())
                addImdbId(imdbId)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LinkData>(data)
        
        runAllAsync(
            { invokeVidsrccc(res.id, res.season, res.episode, callback) },
            { invokeVidSrcXyz(res.imdbId, res.season, res.episode, callback) },
            { invokeXPrimeAPI(res.title, res.year, res.imdbId, res.id, res.season, res.episode, subtitleCallback, callback) },
            { invokeHubCloudGDFlix(res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeTorrentio(res.imdbId, res.season, res.episode, callback) },
            
            { invokeSubtitleAPI(res.imdbId, res.season, res.episode, subtitleCallback) }
        )

        return true
    }

    data class LinkData(
        val id: Int? = null,
        val imdbId: String? = null,
        val type: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val title: String? = null,
        val year: Int? = null,
    )
}
