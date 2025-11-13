// File: SoraStream.kt - versi optimasi lengkap

package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.hexated.SoraExtractor.invokeGomovies
import com.hexated.SoraExtractor.invokeIdlix
import com.hexated.SoraExtractor.invokeMapple
import com.hexated.SoraExtractor.invokeSuperembed
import com.hexated.SoraExtractor.invokeVidfast
import com.hexated.SoraExtractor.invokeVidlink
import com.hexated.SoraExtractor.invokeVidrock
import com.hexated.SoraExtractor.invokeVidsrc
import com.hexated.SoraExtractor.invokeVidsrccc
import com.hexated.SoraExtractor.invokeVidsrccx
import com.hexated.SoraExtractor.invokeVixsrc
import com.hexated.SoraExtractor.invokeWatchsomuch
import com.hexated.SoraExtractor.invokeWyzie
import com.hexated.SoraExtractor.invokeXprime
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.withTimeout

open class SoraStream : TmdbProvider() {
    override var name = "SoraStream"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )

    // Settings untuk optimasi performa
    override var mainUrl = "https://api.themoviedb.org/3"
    private val enableIndonesianTranslation = true
    private val translateEpisodeDescriptions = false // NONAKTIFKAN untuk performa
    private val translationCache = mutableMapOf<String, String>()
    private val translationTimeout = 3000L // 3 detik timeout

    val wpRedisInterceptor by lazy { CloudflareKiller() }

    companion object {
        // ... (companion object tetap sama)
        private const val tmdbAPI = "https://api.themoviedb.org/3"
        const val translateAPI = "https://translate.googleapis.com/translate_a/single"
        private const val apiKey = "b030404650f279792a8d3287232358e3"
        // ... (source APIs lainnya)
    }

    // ... (mainPage, getImageUrl, getOriImageUrl tetap sama)

    // Fungsi translate yang dioptimalkan
    private suspend fun translateToIndonesian(text: String): String {
        if (!enableIndonesianTranslation || text.isBlank()) return text
        
        // Cek cache dulu
        val cached = translationCache[text]
        if (cached != null) return cached
        
        // Skip jika teks terlalu pendek (tidak perlu diterjemahkan)
        if (text.length < 10) return text
        
        return try {
            val translated = withTimeout(translationTimeout) {
                app.get(
                    "$translateAPI?client=gtx&sl=auto&tl=id&dt=t&q=${encode(text)}",
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                ).text
            }
            
            val result = parseTranslationResponse(translated) ?: text
            
            // Simpan ke cache (maksimal 50 entri)
            if (translationCache.size < 50) {
                translationCache[text] = result
            }
            
            result
        } catch (e: Exception) {
            // Jika timeout atau error, kembalikan teks asli
            text
        }
    }

    // Parse response dari Google Translate (tetap sama)
    private fun parseTranslationResponse(response: String): String? {
        return try {
            val jsonArray = parseJson<List<Any>>(response)
            val mainArray = jsonArray?.get(0) as? List<*>
            mainArray?.let { 
                val translationArray = it.firstOrNull() as? List<*>
                translationArray?.get(0) as? String
            }
        } catch (e: Exception) {
            null
        }
    }

    // Fungsi untuk mendapatkan sinopsis dalam Bahasa Indonesia dari TMDB
    private suspend fun getIndonesianOverview(tmdbId: Int?, type: String): String? {
        if (!enableIndonesianTranslation) return null
        
        return try {
            val url = if (type == "movie") {
                "$tmdbAPI/movie/$tmdbId?api_key=$apiKey&language=id-ID"
            } else {
                "$tmdbAPI/tv/$tmdbId?api_key=$apiKey&language=id-ID"
            }
            
            val response = app.get(url).parsedSafe<MediaDetail>()
            response?.overview?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    // Di dalam fungsi load, bagian episodes yang dioptimalkan:
    override suspend fun load(url: String): LoadResponse? {
        // ... (kode sebelumnya sampai episodes)
        
        return if (type == TvType.TvSeries) {
            val lastSeason = res.last_episode_to_air?.season_number
            val episodes = res.seasons?.mapNotNull { season ->
                val seasonEpisodes = app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes ?: return@mapNotNull null
                
                seasonEpisodes.map { eps ->
                    // OPTIMASI: Gunakan deskripsi asli untuk episode, tidak diterjemahkan
                    val episodeDescription = eps.overview
                    
                    newEpisode(
                        data = LinkData(
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
                        this.name = eps.name + if (isUpcoming(eps.airDate)) " • [UPCOMING]" else ""
                        this.season = eps.seasonNumber
                        this.episode = eps.episodeNumber
                        this.posterUrl = getImageUrl(eps.stillPath)
                        this.score = Score.from10(eps.voteAverage)
                        this.description = episodeDescription // Deskripsi asli (tidak diterjemahkan)
                    }.apply {
                        this.addDate(eps.airDate)
                    }
                }
            }?.flatten() ?: listOf()
            
            // ... (sisanya tetap sama)
        } else {
            // ... (movie part tetap sama)
        }
    }

    // ... (fungsi dan data classes lainnya tetap sama)
}
