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
        "$mainUrl/populer/page/" to "Film Terplopuler",
        "$mainUrl/rating/page/" to "Film Berdasarkan IMDb Rating",
        "$mainUrl/latest-series/page/" to "Series Terbaru",
        "$mainUrl/latest/page/" to "Film Upload Terbaru",
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
        val rawHref = this.selectFirst("a")!!.attr("href")
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
        val res = app.get(
            "$searchApiUrl/search.php?s=$query",
            headers = mapOf(
                "Origin" to mainUrl,
                "Referer" to "$mainUrl/"
            )
        ).text
        
        val results = mutableListOf<SearchResponse>()

        try {
            val root = JSONObject(res)
            if (root.has("data")) {
                val arr = root.getJSONArray("data")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val title = item.getString("title")
                    val slug = item.getString("slug")
                    val type = item.getString("type") 
                    
                    var posterUrl = item.optString("poster")
                    if (!posterUrl.startsWith("http")) {
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
        
        // --- FIX UTAMA: DETEKSI & LOMPATI HALAMAN REDIRECT ---
        // Kita cek judul halamannya. Kalau isinya "Anda akan dialihkan...", kita cari link tujuannya.
        val pageTitle = document.select("h1").text()
        if (pageTitle.contains("dialihkan", ignoreCase = true) || pageTitle.contains("Redirect", ignoreCase = true)) {
            Log.d("LayarKaca", "Halaman Redirect Terdeteksi!")
            
            // Cari link yang menuju ke nontondrama atau link tombol "Buka Sekarang"
            val redirectLink = document.select("a").firstOrNull { 
                it.attr("href").contains("nontondrama") || it.text().contains("Buka", ignoreCase = true)
            }?.attr("href")

            if (!redirectLink.isNullOrEmpty()) {
                finalUrl = fixUrl(redirectLink)
                Log.d("LayarKaca", "Melompat ke: $finalUrl")
                // LOAD ULANG dokumen dari URL baru
                response = app.get(finalUrl)
                document = response.documentLarge
            }
        }
        // -----------------------------------------------------

        val title = document.selectFirst("div.movie-info h1")?.text()?.trim() 
            ?: document.selectFirst("h1.entry-title")?.text()?.trim()
            ?: document.selectFirst("header h1")?.text()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim() 
            ?: "Unknown Title"

        var poster = document.select("meta[property=og:image]").attr("content")
        if (poster.isNullOrEmpty()) {
             poster = document.selectFirst("div.poster img")?.getImageAttr() ?: ""
        }
        
        val tags = document.select("div.tag-list span").map { it.text() }
        val posterheaders = mapOf("Referer" to getBaseUrl(finalUrl))

        val year = Regex("\\d, (\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
        
        val description = document.selectFirst("div.meta-info")?.text()?.trim() 
            ?: document.selectFirst("div.desc")?.text()?.trim()
            ?: document.selectFirst("blockquote")?.text()?.trim()

        val trailer = document.selectFirst("ul.action-left > li:nth-child(3) > a")?.attr("href")
        val rating = document.selectFirst("div.info-tag strong")?.text()

        val recommendations = document.select("li.slider article").mapNotNull {
            it.toSearchResult()
        }

        // Logic penentuan Series vs Movie
        val hasSeasonData = document.selectFirst("#season-data") != null
        val tvType = if (finalUrl.contains("nontondrama") || hasSeasonData) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            val json = document.selectFirst("script#season-data")?.data()
            if (!json.isNullOrEmpty()) {
                val root = JSONObject(json)
                root.keys().forEach { seasonKey ->
                    val seasonArr = root.getJSONArray(seasonKey)
                    for (i in 0 until seasonArr.length()) {
                        val ep = seasonArr.getJSONObject(i)
                        val slug = ep.getString("slug")
                        val href = fixUrl(if (slug.startsWith("http")) slug else "${getBaseUrl(finalUrl)}/$slug")
                        val episodeNo = ep.optInt("episode_no")
                        val seasonNo = ep.optInt("s")
                        episodes.add(newEpisode(href) {
                            this.name = "Episode $episodeNo"
                            this.season = seasonNo
                            this.episode = episodeNo
                        })
                    }
                }
            } else {
                val episodeLinks = document.select("ul.episodios li a, div.list-episode a, a[href*=episode]")
                episodeLinks.forEach { 
                    val epHref = fixUrl(it.attr("href"))
                    val epName = it.text().trim()
                    if(epHref.contains(getBaseUrl(finalUrl)) || epHref.contains("episode")) {
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

        playerNodes.amap { element ->
            val linkElement = element.selectFirst("a")
            val rawHref = linkElement?.attr("href") ?: return@amap
            val serverName = linkElement.text().trim()
            val href = fixUrl(rawHref)

            Log.d("LayarKaca", "Found server: $serverName -> $href")

            // --- PERBAIKAN PENCARIAN IFRAME ---
            var iframeUrl = href.getIframe(referer = data)
            
            // Penanganan Redirect (HYDRAX / SHORT.ICU)
            if (iframeUrl.contains("short.icu") || iframeUrl.contains("hydrax")) {
                iframeUrl = resolveRedirect(iframeUrl)
                Log.d("LayarKaca", "Resolved Redirect ($serverName): $iframeUrl")
            }
            
            if(iframeUrl.isNotEmpty()) {
                loadExtractor(iframeUrl, data, subtitleCallback, callback)
            } else {
                loadExtractor(href, data, subtitleCallback, callback)
            }
        }
        return true
    }

    private suspend fun String.getIframe(referer: String): String {
        if (this.isEmpty() || this.contains("javascript:void")) return ""

        try {
            val response = app.get(this, referer = referer)
            val document = response.documentLarge
            val responseText = response.text

            var src = document.select("iframe").attr("src")
            
            if (src.startsWith("//")) {
                src = "https:$src"
            }

            if (src.isEmpty() || src.contains("javascript")) {
                // Regex super lengkap untuk menangkap link
                val regex = """["'](https?://[^"']*(?:turbovid|hydrax|short|embed|player|watch|hownetwork|cloud|dood|mixdrop|f16px|emturbovid)[^"']*)["']""".toRegex()
                src = regex.find(responseText)?.groupValues?.get(1) ?: ""
            }

             if (src.isEmpty() && response.url.contains("hownetwork")) {
                return response.url
            }

            return fixUrl(src)

        } catch (e: Exception) {
            Log.e("LayarKaca", "Error getting iframe from $this : ${e.message}")
            return ""
        }
    }

    private suspend fun resolveRedirect(url: String): String {
        return try {
            val response = app.get(url, allowRedirects = false)
            if (response.code == 301 || response.code == 302) {
                response.headers["Location"] ?: url
            } else {
                url
            }
        } catch (e: Exception) { url }
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("src") -> this.attr("src")
            this.hasAttr("data-src") -> this.attr("data-src")
            else -> this.attr("src")
        }
    }

    fun getBaseUrl(url: String?): String {
        return try {
            URI(url).let {
                "${it.scheme}://${it.host}"
            }
        } catch (e: Exception) {
            ""
        }
    }
}
