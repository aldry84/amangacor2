package com.LayarKacaProvider

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.loadExtractor
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

        // Helper untuk ambil widget
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
        return app.get(searchUrl).document.select("div.search-result article, div#post-container article")
            .mapNotNull { toSearchResult(it) }
    }

    // --- HELPER: HTML -> SearchResponse ---
    private fun toSearchResult(element: Element): SearchResponse? {
        val title = element.select("h3.poster-title").text().trim()
        if (title.isEmpty()) return null
        val href = fixUrl(element.select("a").first()?.attr("href") ?: return null)
        val posterUrl = element.select("img").attr("src")
        val quality = getQualityFromString(element.select("span.label").text())

        // Cek Series/Movie dari label EPS atau durasi S.
        val isSeries = element.select("span.episode").isNotEmpty() ||
                element.select("span.duration").text().contains("S.")

        return if (isSeries) {
            // FIX: Menggunakan newTvSeriesSearchResponse
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            // FIX: Menggunakan newMovieSearchResponse
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    // --- LOAD DETAIL (INTI LOGIKA) ---

    // Struct buat JSON Series NontonDrama
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
        val plot = document.select("div.synopsis, div.entry-content p blockquote").text().trim()

        // Ambil poster
        val poster = document.select("meta[property='og:image']").attr("content").ifEmpty {
            document.select("div.poster img, div.detail img").attr("src")
        }

        // Ambil Rating (String)
        val ratingText = document.select("span.rating-value").text().ifEmpty {
            document.select("div.info-tag").text()
        }
        val ratingScore = Regex("(\\d\\.\\d)").find(ratingText)?.value

        // Ambil Tahun
        val year = document.select("span.year").text().toIntOrNull() ?:
        Regex("(\\d{4})").find(document.select("div.info-tag").text())?.value?.toIntOrNull()

        // Ambil Genre & Aktor
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
            // Parsing JSON Episode untuk Series NontonDrama
            tryParseJson<Map<String, List<NontonDramaEpisode>>>(jsonScript)?.forEach { (_, epsList) ->
                epsList.forEach { epData ->
                    val epUrl = fixUrl(epData.slug ?: "")
                    // FIX: Menggunakan newEpisode dengan lambda block
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
            // Fallback manual untuk Movie/Series biasa
            document.select("ul.episodes li a").forEach {
                val epTitle = it.text()
                val epHref = fixUrl(it.attr("href"))
                val epNum = Regex("(?i)Episode\\s+(\\d+)").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                // FIX: Menggunakan newEpisode
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
            // -- TV SERIES --
            // FIX: Menggunakan newTvSeriesLoadResponse
            return newTvSeriesLoadResponse(title, cleanUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                // FIX: Menggunakan Score.from langsung ke properti score
                this.score = Score.from(ratingScore, 10)
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
            }
        } else {
            // -- MOVIE --
            // FIX: Menggunakan newMovieLoadResponse
            return newMovieLoadResponse(title, cleanUrl, TvType.Movie, cleanUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                // FIX: Menggunakan Score.from langsung
                this.score = Score.from(ratingScore, 10)
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
            }
        }
    }

    // --- LOAD LINKS (PLAYER) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Cari semua iframe player
        document.select("iframe[src*='player'], iframe#main-player").forEach { iframe ->
            var src = iframe.attr("src")
            // Kalau src diawali // (protocol relative), tambahkan https:
            if (src.startsWith("//")) src = "https:$src"

            // Panggil extractor bawaan CloudStream
            loadExtractor(src, data, subtitleCallback, callback)
        }
        return true
    }
}
