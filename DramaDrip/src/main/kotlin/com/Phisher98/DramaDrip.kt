package com.Phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Element

class DramaDrip : MainAPI() {
    override var mainUrl: String = runBlocking {
        DramaDripProvider.getDomains()?.dramadrip ?: "https://dramadrip.com"
    }
    override var name = "DramaDrip"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.AsianDrama, TvType.TvSeries)
    
    private val cinemeta_url = "https://v3-cinemeta.strem.io/meta"
    
    // --- API DARI ADICINEMAX21 ---
    private val wyzieAPI = "https://sub.wyzie.ru"
    private val vidrockSubAPI = "https://sub.vdrk.site"

    // ... (Main Page & Search functions biarkan sama) ...

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun load(url: String): LoadResponse {
        // ... (Bagian awal load sama seperti sebelumnya sampai pengambilan ID TMDB) ...
        
        // Simpan referensi kode lama Anda untuk parsing HTML disini...
        val document = app.get(url).documentLarge
        // ... Logika ekstraksi imdbId dan tmdbId ...
        var imdbId: String? = null
        var tmdbId: String? = null
        var tmdbType: String? = null

        document.select("div.su-spoiler-content ul.wp-block-list > li").forEach { li ->
            val text = li.text()
            if (imdbId == null && "imdb.com/title/tt" in text) {
                imdbId = Regex("tt\\d+").find(text)?.value
            }
            if (tmdbId == null && tmdbType == null && "themoviedb.org" in text) {
                Regex("/(movie|tv)/(\\d+)").find(text)?.let { match ->
                    tmdbType = match.groupValues[1]
                    tmdbId = match.groupValues[2]
                }
            }
        }
        
        // ... (Logika Cinemeta load, cast, trailer, dll tetap sama) ...
        // ... (Pastikan Anda menyalin logika parsing HTML DramaDrip yang asli di sini) ...
        
        // --- PERUBAHAN PENTING DI SINI ---
        // Kita membungkus Link + Metadata (TMDB ID) agar bisa dibaca di loadLinks
        
        if (tvType == TvType.TvSeries) {
            // ... (Logika parsing episode TvSeries tetap sama) ...
            
            // Contoh implementasi di bagian return TvSeries:
            val finalEpisodes = tvSeriesEpisodes.map { (seasonEpisode, links) ->
                val (season, epNo) = seasonEpisode
                val info = responseData?.meta?.videos?.find { it.season == season && it.episode == epNo }

                // BUNGKUS DATA KE LinkData
                val linkData = LinkData(
                    links = links.distinct(),
                    tmdbId = tmdbId,
                    season = season,
                    episode = epNo,
                    type = "tv"
                )

                newEpisode(linkData.toJson()) {
                    this.name = info?.name ?: "Episode $epNo"
                    this.posterUrl = info?.thumbnail
                    this.season = season
                    this.episode = epNo
                    this.description = info?.overview
                }
            }
            
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, finalEpisodes) {
                // ... properti lain ...
                addTMDbId(tmdbId)
            }

        } else {
            // MOVIE
            val linkData = LinkData(
                links = hrefs, // variabel hrefs dari parsing HTML Anda
                tmdbId = tmdbId,
                season = null,
                episode = null,
                type = "movie"
            )

            return newMovieLoadResponse(title, url, TvType.Movie, linkData.toJson()) {
                // ... properti lain ...
                addTMDbId(tmdbId)
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
        val parsedData = tryParseJson<LinkData>(data)
        val links = parsedData?.links ?: tryParseJson<List<String>>(data).orEmpty()
        
        // --- INTEGRASI SUBTITLE DARI ADICINEMAX21 ---
        if (parsedData?.tmdbId != null) {
            val tmdbId = parsedData.tmdbId
            val season = parsedData.season
            val episode = parsedData.episode
            val type = parsedData.type ?: "movie"

            // 1. Panggil Wyzie (Dari Adicinemax21)
            invokeWyzie(tmdbId, season, episode, subtitleCallback)
            
            // 2. Panggil Vidrock Subtitles (Dari Adicinemax21)
            invokeVidrockSubs(tmdbId, season, episode, type, subtitleCallback)
        }
        // --------------------------------------------

        if (links.isEmpty()) return false

        for (link in links) {
            // ... (Logika bypass link tetap sama) ...
             try {
                val finalLink = when {
                    "safelink=" in link -> cinematickitBypass(link)
                    "unblockedgames" in link -> bypassHrefli(link)
                    "examzculture" in link -> bypassHrefli(link)
                    else -> link
                }
                if (finalLink != null) {
                    loadExtractor(finalLink, subtitleCallback, callback)
                }
            } catch (_: Exception) {}
        }
        return true
    }

    // --- FUNGSI SUBTITLE BARU (Diadaptasi dari Adicinemax21Extractor.kt) ---

    private suspend fun invokeWyzie(
        tmdbId: String,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val url = if (season == null) {
            "$wyzieAPI/search?id=$tmdbId"
        } else {
            "$wyzieAPI/search?id=$tmdbId&season=$season&episode=$episode"
        }

        try {
            val res = app.get(url).text
            tryParseJson<ArrayList<WyzieSubtitle>>(res)?.map { subtitle ->
                val lang = getLanguageNameFromCode(subtitle.display) ?: subtitle.display ?: "Unknown"
                subtitleCallback.invoke(
                    newSubtitleFile(
                        lang,
                        subtitle.url ?: return@map,
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("Wyzie", "Error: ${e.message}")
        }
    }

    private suspend fun invokeVidrockSubs(
        tmdbId: String,
        season: Int?,
        episode: Int?,
        type: String,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        // Logika URL Vidrock dari Adicinemax21
        val url = "$vidrockSubAPI/$type/$tmdbId${if (type == "movie") "" else "/$season/$episode"}"
        
        try {
            val res = app.get(url).text
            tryParseJson<ArrayList<VidrockSubtitle>>(res)?.map { subtitle ->
                // Bersihkan label menggunakan Regex dari Adicinemax21
                val rawLabel = subtitle.label?.replace(Regex("\\d"), "")?.replace(Regex("\\s+Hi"), "")?.trim()
                val lang = getLanguageNameFromCode(rawLabel) ?: rawLabel ?: "Unknown"
                
                subtitleCallback.invoke(
                    newSubtitleFile(
                        "Vidrock - $lang", // Tambah prefix agar tahu sumbernya
                        subtitle.file ?: return@map
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("Vidrock", "Error: ${e.message}")
        }
    }

    // Data Class Pembantu
    data class LinkData(
        val links: List<String>,
        val tmdbId: String?,
        val season: Int?,
        val episode: Int?,
        val type: String? // "movie" atau "tv"
    )

    data class WyzieSubtitle(
        val display: String? = null,
        val url: String? = null,
    )

    data class VidrockSubtitle(
        val label: String? = null,
        val file: String? = null,
    )
}
