package com.LayarKacaProvider

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class LayarKacaProvider : MainAPI() {
    // --- KONFIGURASI ---
    override var mainUrl = "https://tv8.lk21official.cc"
    override var name = "LayarKaca21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // --- MAIN PAGE (HOME) ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val items = ArrayList<HomePageList>()

        fun addWidget(title: String, selector: String) {
            val list = document.select(selector).mapNotNull { toSearchResult(it) }
            if (list.isNotEmpty()) items.add(HomePageList(title, list))
        }

        addWidget("Film Terbaru", "div.widget[data-type='latest-movies'] li.slider article")
        addWidget("Series Unggulan", "div.widget[data-type='top-series-today'] li.slider article")
        addWidget("Horror Terbaru", "div.widget[data-type='latest-horror'] li.slider article")
        addWidget("Daftar Lengkap", "div#post-container article")

        // FIX: Menggunakan newHomePageResponse
        return newHomePageResponse(items)
    }

    // --- SEARCH ---
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search?s=$query"
        val document = app.get(searchUrl).document
        
        // Selector lebih luas untuk menangkap berbagai tampilan hasil search
        return document.select("div.search-result article, div.grid-archive article, div#post-container article, div.movie-list article")
            .mapNotNull { toSearchResult(it) }
    }

    // --- HELPER: HTML -> SearchResponse ---
    private fun toSearchResult(element: Element): SearchResponse? {
        val title = element.select("h3.poster-title, h2.entry-title, h1.page-title, div.title").text().trim()
        if (title.isEmpty()) return null
        
        val href = fixUrl(element.select("a").first()?.attr("href") ?: return null)
        
        val imgElement = element.select("img").first()
        val posterUrl = imgElement?.attr("src") ?: imgElement?.attr("data-src")
        
        val quality = getQualityFromString(element.select("span.label").text())

        val isSeries = element.select("span.episode").isNotEmpty() ||
                element.select("span.duration").text().contains("S.")

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    // --- LOAD DETAIL ---
    data class NontonDramaEpisode(
        val s: Int? = null,
        val episode_no: Int? = null,
        val title: String? = null,
        val slug: String? = null
    )

    override suspend fun load(url: String): LoadResponse {
        var cleanUrl = fixUrl(url)
        var response = app.get(cleanUrl)
        var document = response.document

        // 1. CEK REDIRECT (Anti-Gocek)
        val redirectButton = document.select("a:contains(Buka Sekarang), a.btn:contains(Nontondrama)").first()
        if (redirectButton != null) {
            val newUrl = redirectButton.attr("href")
            if (newUrl.isNotEmpty()) {
                cleanUrl = fixUrl(newUrl)
                response = app.get(cleanUrl)
                document = response.document
            }
        }

        // 2. PARSING DATA UMUM
        val title = document.select("h1.entry-title, h1.page-title, div.movie-info h1").text().trim()
        val plot = document.select("div.synopsis, div.entry-content p, blockquote").text().trim()

        val poster = document.select("meta[property='og:image']").attr("content").ifEmpty {
            document.select("div.poster img, div.detail img").attr("src")
        }

        val ratingText = document.select("span.rating-value").text().ifEmpty {
            document.select("div.info-tag").text()
        }
        val ratingScore = Regex("(\\d\\.\\d)").find(ratingText)?.value

        val year = document.select("span.year").text().toIntOrNull() ?:
        Regex("(\\d{4})").find(document.select("div.info-tag").text())?.value?.toIntOrNull()

        val tags = document.select("div.tag-list a, div.genre a").map { it.text() }
        val actors = document.select("div.detail p:contains(Bintang Film) a, div.cast a").map {
            ActorData(Actor(it.text(), ""))
        }
        val recommendations = document.select("div.related-video li.slider article, div.mob-related-series li.slider article").mapNotNull {
            toSearchResult(it)
        }

        // 3. DETEKSI TIPE KONTEN & EPISODE
        val episodes = ArrayList<Episode>()
        val jsonScript = document.select("script#season-data").html()

        if (jsonScript.isNotBlank()) {
            // Parsing JSON Episode (Untuk Series NontonDrama)
            tryParseJson<Map<String, List<NontonDramaEpisode>>>(jsonScript)?.forEach { (_, epsList) ->
                epsList.forEach { epData ->
                    val epUrl = fixUrl(epData.slug ?: "")
                    episodes.add(
                        newEpisode(epUrl) {
                            this.name = epData.title ?: "Episode ${epData.episode_no}"
                            this.season = epData.s
                            this.episode = epData.episode_no
                        }
                    )
                }
            }
        } else {
            // Fallback manual (Untuk Movie/Series Biasa)
            document.select("ul.episodes li a").forEach {
                val epTitle = it.text()
                val epHref = fixUrl(it.attr("href"))
                val epNum = Regex("(?i)Episode\\s+(\\d+)").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                episodes.add(
                    newEpisode(epHref) {
                        this.name = epTitle
                        this.episode = epNum
                    }
                )
            }
        }

        // 4. RETURN RESPONSE
        if (episodes.isNotEmpty()) {
            return newTvSeriesLoadResponse(title, cleanUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.score = Score.from(ratingScore, 10)
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
            }
        } else {
            return newMovieLoadResponse(title, cleanUrl, TvType.Movie, cleanUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.score = Score.from(ratingScore, 10)
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
            }
        }
    }

    // --- LOAD LINKS (PLAYER FIXED) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // 1. Ambil link dari tombol ganti player
        val playerLinks = document.select("ul#player-list li a").map { 
            val url = it.attr("data-url").ifEmpty { it.attr("href") }
            fixUrl(url)
        }
        // 2. Ambil dari iframe utama
        val mainIframe = fixUrl(document.select("iframe#main-player").attr("src"))
        val allSources = (playerLinks + mainIframe).filter { it.isNotBlank() }.distinct()

        allSources.forEach { url ->
            // Coba extract langsung (siapa tau support)
            val directLoaded = loadExtractor(url, data, subtitleCallback, callback)
            
            if (!directLoaded) {
                // Jika tidak support (wrapper), coba buka isinya (Unwrap)
                try {
                    val iframePage = app.get(url, referer = data).document
                    
                    // Cari nested iframe
                    iframePage.select("iframe").forEach { nestedIframe ->
                        val nestedSrc = fixUrl(nestedIframe.attr("src"))
                        loadExtractor(nestedSrc, url, subtitleCallback, callback)
                    }
                    
                    // Cari link manual (P2P/M3U8) di dalam script wrapper
                    val scriptHtml = iframePage.html()
                    Regex("(?i)https?://[^\"]+\\.(m3u8|mp4)").findAll(scriptHtml).forEach { match ->
                        val streamUrl = match.value
                        val qualityVal = if(streamUrl.contains("m3u8")) Qualities.Unknown.value else Qualities.P480.value
                        val typeVal = if(streamUrl.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        
                        // FIX: Referer dimasukkan di dalam block lambda
                        callback.invoke(
                            newExtractorLink(
                                source = "LK21 P2P",
                                name = "LK21 P2P",
                                url = streamUrl,
                                type = typeVal
                            ) {
                                this.referer = url
                                this.quality = qualityVal
                            }
                        )
                    }
                } catch (e: Exception) {
                    // Ignore error saat unwrap
                }
            }
        }
        return true
    }
}
