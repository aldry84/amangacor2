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

    // --- UPDATE KATEGORI LENGKAP ---
    override val mainPage = mainPageOf(
        // Menu Utama
        "$mainUrl/latest/page/" to "Film Terbaru",
        "$mainUrl/populer/page/" to "Film Terpopuler",
        "$mainUrl/rating/page/" to "Rating Tertinggi",
        "$mainUrl/latest-series/page/" to "Series Terbaru",
        
        // Genre
        "$mainUrl/genre/action/page/" to "Genre: Action",
        "$mainUrl/genre/adventure/page/" to "Genre: Adventure",
        "$mainUrl/genre/animation/page/" to "Genre: Animation",
        "$mainUrl/genre/comedy/page/" to "Genre: Comedy",
        "$mainUrl/genre/crime/page/" to "Genre: Crime",
        "$mainUrl/genre/drama/page/" to "Genre: Drama",
        "$mainUrl/genre/fantasy/page/" to "Genre: Fantasy",
        "$mainUrl/genre/horror/page/" to "Genre: Horror",
        "$mainUrl/genre/mystery/page/" to "Genre: Mystery",
        "$mainUrl/genre/romance/page/" to "Genre: Romance",
        "$mainUrl/genre/sci-fi/page/" to "Genre: Sci-Fi",
        "$mainUrl/genre/thriller/page/" to "Genre: Thriller",
        
        // Negara (Sangat penting untuk pecinta drakor/donghua)
        "$mainUrl/country/korea/page/" to "Negara: Korea",
        "$mainUrl/country/china/page/" to "Negara: China",
        "$mainUrl/country/japan/page/" to "Negara: Jepang",
        "$mainUrl/country/thailand/page/" to "Negara: Thailand",
        "$mainUrl/country/indonesia/page/" to "Negara: Indonesia",
        "$mainUrl/country/usa/page/" to "Negara: USA"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        // Request halaman kategori + nomor halaman
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
        var finalUrl = url
        var document = app.get(finalUrl).documentLarge
        
        // --- AUTO-JUMP LOGIC ---
        if (document.text().contains("dialihkan ke", ignoreCase = true)) {
            val redirectButton = document.select("a").find { 
                it.text().contains("Buka Sekarang", ignoreCase = true) || 
                it.attr("href").contains("nontondrama", ignoreCase = true) 
            }
            if (redirectButton != null) {
                finalUrl = fixUrl(redirectButton.attr("href"))
                document = app.get(finalUrl).documentLarge
            }
        }

        val baseurl = getBaseUrl(finalUrl)
        
        var title = document.selectFirst("div.movie-info h1")?.text()?.trim() 
            ?: document.selectFirst("h1.entry-title")?.text()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim() 
            ?: "Unknown Title"

        var poster = document.select("meta[property=og:image]").attr("content")
        if (poster.isNullOrEmpty()) {
             poster = document.selectFirst("div.poster img")?.getImageAttr() ?: ""
        }
        
        val tags = document.select("div.tag-list span").map { it.text() }
        val posterheaders = mapOf("Referer" to baseurl)

        val year = Regex("\\d, (\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
        
        val description = document.selectFirst("div.meta-info")?.text()?.trim() 
            ?: document.selectFirst("div.desc")?.text()?.trim()
            ?: document.selectFirst("blockquote")?.text()?.trim()

        val trailer = document.selectFirst("ul.action-left > li:nth-child(3) > a")?.attr("href")
        val rating = document.selectFirst("div.info-tag strong")?.text()

        val recommendations = document.select("li.slider article").map {
            val recName = it.selectFirst("h3")?.text()?.trim().toString()
            val recHref = fixUrl(it.selectFirst("a")!!.attr("href"))
            val recPosterUrl = fixUrl(it.selectFirst("img")?.attr("src").toString())
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
                this.posterHeaders = posterheaders
            }
        }

        val hasSeasonData = document.selectFirst("#season-data") != null
        val hasEpisodeList = document.selectFirst("ul.episodios") != null || document.selectFirst("div.list-episode") != null
        
        val tvType = if (finalUrl.contains("nontondrama") || hasSeasonData || hasEpisodeList) 
                     TvType.TvSeries else TvType.Movie

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
                } catch (e: Exception) { }
            } 
            
            if (episodes.isEmpty()) {
                val episodeLinks = document.select("ul.episodios li a, div.list-episode a, table.table tbody tr td a")
                episodeLinks.forEach { 
                    val epHref = fixUrl(it.attr("href"))
                    val epText = it.text().trim()
                    if ((epHref.contains(baseurl) || epText.contains("Episode", true)) && !epHref.contains("#")) {
                        episodes.add(newEpisode(epHref) {
                            this.name = epText
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

        playerNodes.map {
            fixUrl(it.select("a").attr("href"))
        }.amap {
            val iframeUrl = it.getIframe(referer = data)
            val extractorReferer = getBaseUrl(it)
            
            if(iframeUrl.isNotEmpty()) {
                loadExtractor(iframeUrl, extractorReferer, subtitleCallback, callback)
            }
        }
        return true
    }

    private suspend fun String.getIframe(referer: String): String {
        val response = app.get(this, referer = referer)
        val document = response.documentLarge
        val responseText = response.text

        var src = document.selectFirst("div.embed-container iframe")?.attr("src")

        if (src.isNullOrEmpty()) {
            src = document.selectFirst("iframe[src^=http]")?.attr("src")
        }

        if (src.isNullOrEmpty()) {
            val regex = """["'](https?://[^"']+)["']""".toRegex()
            val foundLinks = regex.findAll(responseText).map { it.groupValues[1] }.toList()
            
            src = foundLinks.firstOrNull { link -> 
                !link.contains(".js") && 
                !link.contains(".css") && 
                !link.contains(".png") && 
                !link.contains(".jpg") &&
                (link.contains("embed") || link.contains("player") || link.contains("streaming") || link.contains("hownetwork"))
            }
        }

        return fixUrl(src ?: "")
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
