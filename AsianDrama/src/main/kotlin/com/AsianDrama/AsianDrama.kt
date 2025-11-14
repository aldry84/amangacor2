package com.AsianDrama

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element

class AsianDrama : MainAPI() {
    override var mainUrl = "https://dramadrip.com"
    override var name = "AsianDrama"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie, 
        TvType.AsianDrama, 
        TvType.TvSeries,
        TvType.Anime
    )

    // Gunakan DramaDrip untuk halaman utama
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
        val document = app.get("$mainUrl/${request.data}/page/$page").document
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
        val title = this.selectFirst("h2.entry-title")?.text()?.substringAfter("Download") ?: return null
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
        
        return newMovieSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        // Gunakan DramaDrip untuk metadata dan UI
        val dramaDrip = DramaDripLoader()
        return dramaDrip.loadContent(url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Gunakan SoraStream sebagai sumber utama streaming
        val soraData = tryParseJson<SoraStreamData>(data)
        
        if (soraData != null) {
            // Gunakan SoraStream extractors
            SoraStreamExtractor().invokeAllExtractors(
                soraData,
                subtitleCallback,
                callback
            )
            return true
        }
        
        // Fallback ke DramaDrip extractors
        val dramaDrip = DramaDripLoader()
        return dramaDrip.loadLinks(data, isCasting, subtitleCallback, callback)
    }
}

// Data class untuk SoraStream
data class SoraStreamData(
    val title: String? = null,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val type: String? = null,
    val orgTitle: String? = null
)
