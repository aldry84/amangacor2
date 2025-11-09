package com.AdicinemaxNew

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.AdicinemaxNew.AdicinemaxExtractor.invokeSubtitleAPI
import com.AdicinemaxNew.AdicinemaxExtractor.invokeVidSrcXyz
import com.AdicinemaxNew.AdicinemaxExtractor.invokeXPrimeAPI
import com.AdicinemaxNew.AdicinemaxExtractor.invokeVidsrccc
import com.AdicinemaxNew.AdicinemaxExtractor.invokeHubCloudGDFlix
import com.AdicinemaxNew.AdicinemaxExtractor.invokeTorrentio

// ASUMSI: Konstanta TMDB_API dan TMDB Proxy berada di BuildConfig
// ASUMSI: getApiBase dan getImageUrl ada di file utils

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
    
    // Asumsi token FebBox disimpan di sini jika diperlukan oleh ekstraktor
    val token: String? = sharedPref?.getString("token", null) 
    val langCode = sharedPref?.getString("tmdb_language_code", "en-US") ?: "en-US"
    
    // Konstanta yang diperlukan (diambil dari StreamPlay)
    companion object {
        const val apiKey = BuildConfig.TMDB_API
        const val OFFICIAL_TMDB_URL = "https://api.themoviedb.org/3"
        // ASUMSI: Fungsi getApiBase() ada di Utils.kt
    }
    
    // --- TMDB Logikanya sama dengan StreamPlay, hanya dicantumkan bagian inti: ---

    override val mainPage = mainPageOf(
        "/trending/all/day?api_key=$apiKey&region=US" to "Trending Now",
        "/movie/popular?api_key=$apiKey&region=US" to "Popular Movies",
        "/tv/popular?api_key=$apiKey&region=US" to "Popular TV Shows"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val tmdbAPI = getApiBase()
        val type = if (request.data.contains("/movie")) "movie" else "tv"
        val home = app.get("$tmdbAPI${request.data}&language=$langCode&page=$page", timeout = 10000)
            .parsedSafe<StreamPlay.Results>()?.results?.mapNotNull { media ->
                (media as? StreamPlay.Media)?.toSearchResponse(type) 
            } ?: throw ErrorLoadingException("Invalid Json response")
        return newHomePageResponse(request.name, home)
    }
    
    private fun StreamPlay.Media.toSearchResponse(type: String? = null): SearchResponse? {
        // ... (Logika mapping ke SearchResponse)
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
        val type = StreamPlay.getType(data.type)
        val append = "alternative_titles,credits,external_ids,videos,recommendations"

        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$apiKey&language=$langCode&append_to_response=$append"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$apiKey&language=$langCode&append_to_response=$append"
        }

        val res = app.get(resUrl).parsedSafe<StreamPlay.MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")
        val title = res.title ?: res.name ?: return null
        // ... (Logika mapping ke Movie/TV LoadResponse)
        val imdbId = res.external_ids?.imdb_id
        val year = res.releaseDate?.split("-")?.first()?.toIntOrNull()
        
        // Hanya kode inti untuk LinkData
        val linkData = LinkData(
            id = data.id,
            imdbId = imdbId,
            type = data.type,
            season = null, // Akan diisi di episode
            episode = null, // Akan diisi di episode
            title = title,
            year = year
        )

        if (type == TvType.TvSeries) {
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey&language=$langCode")
                    .parsedSafe<StreamPlay.MediaDetailEpisodes>()?.episodes?.map { eps ->
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
        
        // Panggil semua sumber terbaik yang direkomendasikan secara paralel
        runAllAsync(
            { invokeVidsrccc(res.id, res.season, res.episode, callback) },
            { invokeVidSrcXyz(res.imdbId, res.season, res.episode, callback) },
            { invokeXPrimeAPI(res.title, res.year, res.imdbId, res.id, res.season, res.episode, subtitleCallback, callback) },
            { invokeHubCloudGDFlix(res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeTorrentio(res.imdbId, res.season, res.episode, callback) },
            
            // Subtitles
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
