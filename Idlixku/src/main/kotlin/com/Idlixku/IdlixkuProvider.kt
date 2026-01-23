@file:Suppress("DEPRECATION")

package com.Idlixku

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson 
import org.jsoup.nodes.Element

class IdlixkuProvider : MainAPI() {
    override var mainUrl = "https://tv12.idlixku.com"
    override var name = "Idlixku"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    data class DooplayResponse(
        @JsonProperty("embed_url") val embed_url: String?,
        @JsonProperty("type") val type: String?
    )

    // --- 1. HOME PAGE ---
    override val mainPage = mainPageOf(
        "$mainUrl/" to "Featured",
        "$mainUrl/" to "Film Terbaru",
        "$mainUrl/" to "Drama Korea",
        "$mainUrl/" to "Anime",
        "$mainUrl/" to "Serial TV",
        "$mainUrl/" to "Episode Terbaru"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        // Gaya FourKHDHub: Bungkus dengan runCatching
        return runCatching {
            val document = app.get(request.data).document
            val sectionSelector = when (request.name) {
                "Featured" -> ".items.featured article"
                "Film Terbaru" -> "#dt-movies article"
                "Drama Korea" -> "#genre_drama-korea article"
                "Anime" -> "#genre_anime article"
                "Serial TV" -> "#dt-tvshows article"
                "Episode Terbaru" -> ".items.full article"
                else -> return null
            }
            val home = document.select(sectionSelector).mapNotNull { toSearchResult(it) }
            newHomePageResponse(request.name, home)
        }.getOrNull()
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val titleElement = element.selectFirst("h3 > a") ?: return null
        val title = titleElement.text()
        
        // Gaya FourKHDHub: Gunakan absUrl dan fallback ke attr
        val href = titleElement.absUrl("href").ifBlank { titleElement.attr("href") }
        
        val imgTag = element.selectFirst("img")
        val posterUrl = imgTag?.absUrl("src")?.ifBlank { imgTag.attr("src") }
            ?.replace("/w185/", "/w500/")
            ?.replace("/w92/", "/w500/")
            
        val quality = element.selectFirst(".quality")?.text() ?: ""
        
        val isTvSeries = element.classNames().any { it in listOf("tvshows", "seasons", "episodes") } 
                         || href.contains("/tvseries/") || href.contains("/season/")

        return if (isTvSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                val episodeInfo = element.selectFirst(".data span")?.text()
                if (!episodeInfo.isNullOrEmpty()) addQuality(episodeInfo)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    // --- 2. SEARCH ---
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        return runCatching {
            val document = app.get(url).document
            document.select("div.result-item article").mapNotNull {
                toSearchResult(it)
            }
        }.getOrElse { emptyList() }
    }

    // --- 3. LOAD (DETAIL) ---
    override suspend fun load(url: String): LoadResponse? {
        return runCatching {
            val document = app.get(url).document

            val title = document.selectFirst(".data h1")?.text()?.trim() ?: return null
            val imgTag = document.selectFirst(".poster img")
            val poster = imgTag?.absUrl("src")?.ifBlank { imgTag.attr("src") }
                ?.replace("/w185/", "/w780/")
                
            val description = document.selectFirst(".wp-content p")?.text()?.trim() 
                ?: document.selectFirst("center p")?.text()?.trim()
            
            val ratingText = document.selectFirst(".dt_rating_vgs")?.text()?.trim()
            val ratingDouble = ratingText?.toDoubleOrNull()

            val year = document.selectFirst(".date")?.text()?.split(",")?.last()?.trim()?.toIntOrNull()
            val tags = document.select(".sgeneros a").map { it.text() }

            val isTvSeries = document.select("body").hasClass("single-tvshows") || url.contains("/tvseries/") || document.select("#seasons").isNotEmpty()

            val episodes = if (isTvSeries) {
                document.select("#seasons .se-c").flatMap { seasonElement ->
                    val seasonNum = seasonElement.selectFirst(".se-t")?.text()?.toIntOrNull() ?: 1
                    seasonElement.select("ul.episodios li").map { ep ->
                        val epImgTag = ep.selectFirst("img")
                        val epImg = epImgTag?.absUrl("src")?.ifBlank { epImgTag.attr("src") }
                        
                        val epNum = ep.selectFirst(".numerando")?.text()?.split("-")?.last()?.trim()?.toIntOrNull()
                        val epTitle = ep.selectFirst(".episodiotitle a")?.text()
                        
                        val epLink = ep.selectFirst(".episodiotitle a")
                        val epUrl = epLink?.absUrl("href")?.ifBlank { epLink.attr("href") } ?: ""
                        val date = ep.selectFirst(".date")?.text()

                        newEpisode(epUrl) {
                            this.name = epTitle
                            this.season = seasonNum
                            this.episode = epNum
                            this.posterUrl = epImg
                            this.addDate(date)
                        }
                    }
                }
            } else {
                listOf(newEpisode(url) {
                    this.name = title
                    this.posterUrl = poster
                })
            }

            if (isTvSeries) {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = description
                    this.year = year
                    this.score = Score.from10(ratingDouble)
                    this.tags = tags
                }
            } else {
                newMovieLoadResponse(title, url, TvType.Movie, episodes) {
                    this.posterUrl = poster
                    this.plot = description
                    this.year = year
                    this.score = Score.from10(ratingDouble)
                    this.tags = tags
                }
            }
        }.getOrNull()
    }

    // --- 4. LOAD LINKS ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Gaya FourKHDHub: Safety check di awal
        val document = runCatching { app.get(data).document }.getOrNull() ?: return false

        document.select("ul#playeroptionsul li").forEach { element ->
            val type = element.attr("data-type")
            val post = element.attr("data-post")
            val nume = element.attr("data-nume")
            val title = element.select(".title").text()

            if (nume == "trailer") return@forEach

            val formData = mapOf(
                "action" to "doo_player_ajax",
                "post" to post,
                "nume" to nume,
                "type" to type
            )

            runCatching {
                val response = app.post(
                    "$mainUrl/wp-admin/admin-ajax.php",
                    data = formData,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    referer = data
                )
                
                // Parse Manual agar aman dari library yg missing
                val dooplayResponse = AppUtils.parseJson<DooplayResponse>(response.text)
                var embedUrl = dooplayResponse.embed_url ?: return@forEach

                if (embedUrl.contains("<iframe")) {
                    val iframeDoc = org.jsoup.Jsoup.parse(embedUrl)
                    embedUrl = iframeDoc.select("iframe").attr("src")
                }

                if (embedUrl.contains("jeniusplay.com")) {
                    JeniusPlayExtractor().getUrl(embedUrl, data, subtitleCallback, callback)
                } else {
                    loadExtractor(embedUrl, "IDLIX $title", subtitleCallback, callback)
                }
            }.onFailure { 
                // Error handling diam
            }
        }
        return true
    }
}
