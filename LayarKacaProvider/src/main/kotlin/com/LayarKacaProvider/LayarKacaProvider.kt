package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI

class LayarKacaProvider : MainAPI() {

    override var mainUrl = "https://tv7.lk21official.cc"
    private var seriesDomain = "https://tv3.nontondrama.my"
    private var searchApiUrl = "https://gudangvape.com"

    override var name = "LayarKaca"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Top Bulan Ini",
        "$mainUrl/latest/page/" to "Film Terbaru",
        "$mainUrl/top-series-today/page/" to "Series Hari Ini",
        "$mainUrl/latest-series/page/" to "Series Terbaru",
        "$mainUrl/nonton-bareng-keluarga/page/" to "Nobar Keluarga",
        "$mainUrl/genre/romance/page/" to "Romantis",
        "$mainUrl/country/thailand/page/" to "Thailand"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).documentLarge
        val home = document.select("article figure").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.ownText()?.trim() ?: return null
        // Gunakan safety call (?.) daripada assertion (!!)
        val rawHref = this.selectFirst("a")?.attr("href") ?: return null
        val href = fixUrl(rawHref) 
        
        val posterUrl = fixUrlNull(this.selectFirst("img")?.getImageAttr())
        val type = if (this.selectFirst("span.episode") == null) TvType.Movie else TvType.TvSeries
        
        return if (type == TvType.TvSeries) {
            val episode = this.selectFirst("span.episode strong")?.text()?.filter { it.isDigit() }
                ?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            val quality = this.select("div.quality").text().trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        try {
            val res = app.get(
                "$searchApiUrl/search.php?s=$query",
                headers = mapOf(
                    "Origin" to mainUrl,
                    "Referer" to "$mainUrl/"
                )
            ).text
            
            val root = JSONObject(res)
            if (root.has("data")) {
                val arr = root.getJSONArray("data")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val title = item.getString("title")
                    val slug = item.getString("slug")
                    val type = item.getString("type") 
                    
                    var posterUrl = item.optString("poster")
                    if (posterUrl.isNotEmpty() && !posterUrl.startsWith("http")) {
                        posterUrl = "https://poster.lk21.party/wp-content/uploads/$posterUrl"
                    }

                    val itemUrl = if (type == "series") "$seriesDomain/$slug" else "$mainUrl/$slug"

                    if (type == "series") {
                        results.add(newTvSeriesSearchResponse(title, itemUrl, TvType.TvSeries) {
                            this.posterUrl = posterUrl
                        })
                    } else {
                        results.add(newMovieSearchResponse(title, itemUrl, TvType.Movie) {
                            this.posterUrl = posterUrl
                        })
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LayarKacaSearch", "Error parsing JSON: ${e.message}")
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        var response = app.get(url)
        var document = response.documentLarge
        var finalUrl = response.url 
        
        val bodyText = document.body().text()
        if (bodyText.contains("dialihkan ke", ignoreCase = true) && bodyText.contains("Nontondrama", ignoreCase = true)) {
            val redirectLink = document.select("a").firstOrNull { 
                it.text().contains("Buka Sekarang", ignoreCase = true) ||
                it.attr("href").contains("nontondrama", ignoreCase = true)
            }?.attr("href")
            
            if (!redirectLink.isNullOrEmpty()) {
                finalUrl = fixUrl(redirectLink)
                document = app.get(finalUrl).documentLarge
            }
        }

        val baseurl = getBaseUrl(finalUrl)
        
        val title = document.selectFirst("div.movie-info h1, h1.entry-title, header h1, h1")?.text()?.trim() ?: "Unknown Title"

        var poster = document.select("meta[property=og:image]").attr("content")
        if (poster.isNullOrEmpty()) {
             poster = document.selectFirst("div.poster img")?.getImageAttr() ?: ""
        }
        
        val tags = document.select("div.tag-list span").map { it.text() }
        val posterheaders = mapOf("Referer" to baseurl)

        val year = Regex("\\d, (\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
        
        val description = document.selectFirst("div.meta-info, div.desc, blockquote")?.text()?.trim()

        val trailer = document.selectFirst("ul.action-left > li:nth-child(3) > a")?.attr("href")
        val rating = document.selectFirst("div.info-tag strong")?.text()

        val recommendations = document.select("li.slider article").mapNotNull {
            val recName = it.selectFirst("h3")?.text()?.trim() ?: return@mapNotNull null
            val recHref = fixUrl(it.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            val recPosterUrl = fixUrl(it.selectFirst("img")?.attr("src") ?: "")
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
                this.posterHeaders = posterheaders
            }
        }

        val hasSeasonData = document.selectFirst("#season-data") != null
        val tvType = if (finalUrl.contains("nontondrama") || hasSeasonData) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            val json = document.selectFirst("script#season-data")?.data()
            
            if (!json.isNullOrEmpty()) {
                try {
                    val root = JSONObject(json)
                    root.keys().forEach { seasonKey ->
                        val seasonArr = root.getJSONArray(seasonKey)
                        for (i in 0 until seasonArr.length()) {
                            val ep = seasonArr.getJSONObject(i)
                            val slug = ep.getString("slug")
                            val href = fixUrl(if (slug.startsWith("http")) slug else "$baseurl/$slug")
                            val episodeNo = ep.optInt("episode_no")
                            val seasonNo = ep.optInt("s")
                            episodes.add(newEpisode(href) {
                                this.name = "Episode $episodeNo"
                                this.season = seasonNo
                                this.episode = episodeNo
                            })
                        }
                    }
                } catch (e: Exception) { Log.e("LK21", "Season parsing error") }
            } else {
                val episodeLinks = document.select("ul.episodios li a, div.list-episode a, a[href*=episode]")
                episodeLinks.forEach { 
                    val epHref = fixUrl(it.attr("href"))
                    val epName = it.text().trim()
                    if(epHref.contains(baseurl) || epHref.contains("episode")) {
                        episodes.add(newEpisode(epHref) {
                            this.name = epName
                        })
                    }
                }
            }

            newTvSeriesLoadResponse(title, finalUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.posterHeaders = posterheaders
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, finalUrl, TvType.Movie, finalUrl) {
                this.posterUrl = poster
                this.posterHeaders = posterheaders
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).documentLarge
        
        var playerNodes = document.select("ul#player-list > li")
        if (playerNodes.isEmpty()) {
             playerNodes = document.select("div.player_nav ul li, ul.player-list li")
        }

        playerNodes.mapNotNull {
            val href = it.select("a").attr("href")
            if (href.isNotBlank()) fixUrl(href) else null
        }.amap { url ->
            val iframeUrl = url.getIframe(referer = data)
            // Hanya load jika url valid dan bukan link kosong
            if(iframeUrl.isNotEmpty() && iframeUrl.startsWith("http")) {
                 loadExtractor(iframeUrl, data, subtitleCallback, callback)
            }
        }
        return true
    }

    private suspend fun String.getIframe(referer: String): String {
        try {
            val response = app.get(this, referer = referer)
            val document = response.documentLarge
            val responseText = response.text

            var src = document.selectFirst("div.embed-container iframe")?.attr("src")

            if (src.isNullOrEmpty()) {
                src = document.selectFirst("iframe[src^=http]")?.attr("src")
            }

            // Fallback Regex (Diperketat agar tidak menangkap sampah)
            if (src.isNullOrEmpty()) {
                // Cari URL yang mengandung kata kunci embed/video dan diakhiri tipe umum atau tanpa ekstensi
                // Menghindari .js .css .jpg secara eksplisit
                val regex = """["'](https?://[^"']+)["']""".toRegex()
                val foundLinks = regex.findAll(responseText).map { it.groupValues[1] }.toList()
                
                src = foundLinks.firstOrNull { link -> 
                    !link.endsWith(".js") && 
                    !link.endsWith(".css") && 
                    !link.contains(".png") && 
                    !link.contains(".jpg") &&
                    !link.contains("google") && // Hindari tracker
                    !link.contains("facebook") &&
                    (link.contains("embed") || link.contains("player") || link.contains("streaming") || link.contains("hls"))
                }
            }
            return fixUrl(src ?: "")
        } catch (e: Exception) {
            return ""
        }
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("data-src")
            this.hasAttr("src") -> this.attr("src")
            else -> ""
        }
    }

    fun getBaseUrl(url: String?): String {
        return try {
            val uri = URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            ""
        }
    }
}
