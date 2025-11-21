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

open class DramaDrip : MainAPI() {
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

    companion object {
        // Konstanta API untuk Extractor
        const val idlixAPI = "https://tv6.idlixku.com"
        const val vidsrcccAPI = "https://vidsrc.cc"
        const val vidlinkAPI = "https://vidlink.pro"
        const val vixsrcAPI = "https://vixsrc.to"
        const val mappleAPI = "https://mapple.uk" 
    }

    // Data Class untuk mengirim info lengkap dari Load ke LoadLinks
    data class LinkData(
        val tmdbId: Int? = null,
        val imdbId: String? = null,
        val title: String? = null,
        val year: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val type: String? = null
    )

    override val mainPage = mainPageOf(
        "drama/ongoing" to "Ongoing Dramas",
        "latest" to "Latest Releases",
        "drama/chinese-drama" to "Chinese Dramas",
        "drama/japanese-drama" to "Japanese Dramas",
        "drama/korean-drama" to "Korean Dramas",
        "movies" to "Movies",
        "web-series" to "Web Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page").documentLarge
        val home = document.select("article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title =
            this.selectFirst("h2.entry-title")?.text()?.substringAfter("Download") ?: return null
        val href = this.select("h2.entry-title > a").attr("href")
        val imgElement = this.selectFirst("img")
        val srcset = imgElement?.attr("srcset")

        val highestResUrl = srcset
            ?.split(",")
            ?.map { it.trim() }
            ?.mapNotNull {
                val parts = it.split(" ")
                if (parts.size == 2) parts[0] to parts[1].removeSuffix("w").toIntOrNull() else null
            }
            ?.maxByOrNull { it.second ?: 0 }
            ?.first

        val posterUrl = highestResUrl ?: imgElement?.attr("src")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").documentLarge
        val results = document.select("article").mapNotNull {
            it.toSearchResult()
        }
        return results
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge

        var imdbId: String? = null
        var tmdbId: String? = null
        var tmdbType: String? = null

        // 1. Ambil ID dari teks halaman (Regex)
        document.select("div.su-spoiler-content ul.wp-block-list > li").forEach { li ->
            val text = li.text()
            if (imdbId == null && "imdb.com/title/tt" in text) {
                imdbId = Regex("tt\\d+").find(text)?.value
            }

            if (tmdbId == null && tmdbType == null && "themoviedb.org" in text) {
                Regex("/(movie|tv)/(\\d+)").find(text)?.let { match ->
                    tmdbType = match.groupValues[1] // movie or tv
                    tmdbId = match.groupValues[2]   // numeric ID
                }
            }
        }

        val tvType = when (true) {
            (tmdbType?.contains("Movie", ignoreCase = true) == true) -> TvType.Movie
            else -> TvType.TvSeries
        }

        // Metadata dasar dari web DramaDrip
        val image = document.select("meta[property=og:image]").attr("content")
        val title = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringBefore("(")?.trim().toString()
        val tags = document.select("div.mt-2 span.badge").map { it.text() }
        val year = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringAfter("(")?.substringBefore(")")?.toIntOrNull()
        val webDescription = document.selectFirst("div.content-section p.mt-4")?.text()?.trim()
        
        // 2. Ambil Metadata Lengkap via Cinemeta (Wajib untuk Episode List yang benar)
        val typeset = if (tvType == TvType.TvSeries) "series" else "movie"
        val responseData = if (tmdbId?.isNotEmpty() == true || imdbId?.isNotEmpty() == true) {
            val metaId = imdbId ?: return throw ErrorLoadingException("No IMDB/TMDB ID found")
            try {
                val jsonResponse = app.get("$cinemeta_url/$typeset/$metaId.json").text
                if (jsonResponse.isNotEmpty() && jsonResponse.startsWith("{")) {
                    val gson = Gson()
                    gson.fromJson(jsonResponse, ResponseData::class.java)
                } else null
            } catch (e: Exception) { null }
        } else null
        
        var cast: List<String> = emptyList()
        var background: String = image
        var description: String? = webDescription
        
        if (responseData != null) {
            description = responseData.meta?.description ?: webDescription
            cast = responseData.meta?.cast ?: emptyList()
            background = responseData.meta?.background ?: image
        }

        val trailer = document.selectFirst("div.wp-block-embed__wrapper > iframe")?.attr("src")
        val recommendations = document.select("div.entry-related-inner-content article").mapNotNull {
            val recName = it.select("h3").text().substringAfter("Download")
            val recHref = it.select("h3 a").attr("href")
            val recPosterUrl = it.select("img").attr("src")
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
            }
        }

        // 3. Logika Episode Baru (Bersih dari bug "Episode 720")
        if (tvType == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()

            if (responseData?.meta?.videos != null && responseData.meta.videos.isNotEmpty()) {
                // Menggunakan data dari Cinemeta untuk membuat list episode
                responseData.meta.videos.forEach { video ->
                    val epData = LinkData(
                        tmdbId = tmdbId?.toIntOrNull(),
                        imdbId = imdbId,
                        title = title,
                        year = year,
                        season = video.season,
                        episode = video.episode,
                        type = "tv"
                    )
                    episodes.add(
                        newEpisode(epData.toJson()) {
                            this.name = video.name ?: "Episode ${video.episode}"
                            this.season = video.season
                            this.episode = video.episode
                            this.posterUrl = video.thumbnail
                            this.description = video.overview
                            this.addDate(video.released)
                        }
                    )
                }
            } else {
                // Jika tidak ada metadata, jangan scraping tombol download yang rusak
                throw ErrorLoadingException("Metadata episode tidak ditemukan. Cek koneksi internet.")
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
                addActors(cast)
                addImdbId(imdbId)
                addTMDbId(tmdbId)
            }
        } else {
             val linkData = LinkData(
                 tmdbId = tmdbId?.toIntOrNull(),
                 imdbId = imdbId,
                 title = title,
                 year = year,
                 season = null,
                 episode = null,
                 type = "movie"
             )
            
            return newMovieLoadResponse(title, url, TvType.Movie, linkData.toJson()) {
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
                addActors(cast)
                addImdbId(imdbId)
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
        
        val req = tryParseJson<LinkData>(data) ?: return false
        
        // 4. Logika LoadLinks Paralel (Cepat)
        // Menggunakan runAllAsync agar semua sumber dipanggil bersamaan
        runAllAsync(
            {
                // 1. JeniusPlay (via Idlix)
                DramaDripExtractor.invokeIdlix(
                    title = req.title,
                    year = req.year,
                    season = req.season,
                    episode = req.episode,
                    subtitleCallback = subtitleCallback,
                    callback = callback
                )
            },
            {
                // 2. Vidlink
                DramaDripExtractor.invokeVidlink(
                    tmdbId = req.tmdbId,
                    season = req.season,
                    episode = req.episode,
                    callback = callback
                )
            },
            {
                // 3. VidPlay (via Vidsrccc)
                DramaDripExtractor.invokeVidsrccc(
                    tmdbId = req.tmdbId,
                    imdbId = req.imdbId,
                    season = req.season,
                    episode = req.episode,
                    subtitleCallback = subtitleCallback,
                    callback = callback
                )
            },
            {
                // 4. Vixsrc Alpha
                DramaDripExtractor.invokeVixsrc(
                    tmdbId = req.tmdbId,
                    season = req.season,
                    episode = req.episode,
                    callback = callback
                )
            }
        )

        return true
    }
}
