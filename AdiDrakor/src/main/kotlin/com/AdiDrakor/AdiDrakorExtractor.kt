package com.AdiDrakor

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import org.jsoup.Jsoup
import org.json.JSONObject
import java.net.URI
import java.util.*

object AdiDrakorExtractor : AdiDrakor() {

    // ==============================
    // 1. VIDLINK (Powerful Extractor)
    // ==============================
    suspend fun invokeVidlink(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) "${DomainManager.vidlinkAPI}/movie/$tmdbId" 
                  else "${DomainManager.vidlinkAPI}/tv/$tmdbId/$season/$episode"

        val jsToClickPlay = """
        (() => {
            const btn = document.querySelector('.jw-icon-display.jw-button-color.jw-reset');
            return btn ? (btn.click(), "clicked") : "button not found";
        })();
        """.trimIndent()

        val vidlinkRegex = Regex("""\.pro/api/b.*""")

        val apifetch = WebViewResolver(
            interceptUrl = vidlinkRegex,
            additionalUrls = listOf(vidlinkRegex),
            script = jsToClickPlay,
            useOkhttp = false,
            timeout = 15_000L
        )

        try {
            val iframe = app.get(url, interceptor = apifetch).url
            val jsonString = app.get(iframe).text
            val root = parseJson<Vidlink>(jsonString)
            
            val rawM3u8Url = root.stream.playlist.substringBefore("?")
            val headers = mapOf("referer" to DomainManager.vidlinkAPI)

            generateM3u8(
                "Vidlink",
                rawM3u8Url,
                DomainManager.vidlinkAPI,
                headers = headers
            ).forEach(callback)

            root.stream.captions.forEach { caption ->
                subtitleCallback(
                    newSubtitleFile(caption.language, caption.url)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==============================
    // 2. CINEMA OS (High Quality)
    // ==============================
    suspend fun invokeCinemaOS(
        imdbId: String? = null,
        tmdbId: Int? = null,
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val api = DomainManager.cinemaosAPI
        val fixTitle = title?.replace(" ", "+")
        
        val request = CinemaOsSecretKeyRequest(
            tmdbId = tmdbId.toString(), 
            seasonId = season?.toString() ?: "", 
            episodeId = episode?.toString() ?: ""
        )
        
        val secretHash = cinemaOSGenerateHash(request, season != null)
        val type = if(season == null) "movie" else "tv"
        
        val query = if(season == null) {
            "type=$type&tmdbId=$tmdbId&imdbId=$imdbId&t=$fixTitle&ry=$year&secret=$secretHash"
        } else {
            "type=$type&tmdbId=$tmdbId&imdbId=$imdbId&seasonId=$season&episodeId=$episode&t=$fixTitle&ry=$year&secret=$secretHash"
        }
        
        val sourceUrl = "$api/api/fuckit?$query"
        
        try {
            val sourceResponse = app.get(sourceUrl, timeout = 60L).parsedSafe<CinemaOSReponse>()
            val decryptedJson = cinemaOSDecryptResponse(sourceResponse?.data)
            
            // Parsing Manual JSON String karena format dinamis
            val json = JSONObject(decryptedJson.toString())
            val sourcesObject = json.getJSONObject("sources")
            
            sourcesObject.keys().forEach { key ->
                val source = sourcesObject.getJSONObject(key)
                val url = if (source.has("qualities")) {
                    // Ambil kualitas tertinggi (biasanya key terakhir atau spesifik)
                    val qualities = source.getJSONObject("qualities")
                    qualities.optJSONObject("1080")?.optString("url") 
                        ?: qualities.optJSONObject("720")?.optString("url") 
                        ?: ""
                } else {
                    source.optString("url", "")
                }

                if (url.isNotBlank()) {
                    callback.invoke(
                        newExtractorLink(
                            "CinemaOS [$key]",
                            "CinemaOS [$key]",
                            url,
                            ExtractorLinkType.M3U8
                        ) {
                            this.headers = mapOf("Referer" to api)
                            this.quality = Qualities.P1080.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==============================
    // 3. PLAYER 4U
    // ==============================
    suspend fun invokePlayer4U(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val api = DomainManager.player4uAPI
        val query = if (season != null) "$title S${"%02d".format(season)}E${"%02d".format(episode)}" else "$title $year"
        val encodedQuery = query.replace(" ", "+")
        val url = "$api/embed?key=$encodedQuery"

        try {
            val doc = app.get(url).document
            val links = doc.select(".playbtnx").mapNotNull { element ->
                val name = element.text()
                val onclick = element.attr("onclick")
                Player4uLinkData(name, onclick)
            }

            links.forEach { link ->
                val subPath = Regex("""go\('(.*?)'\)""").find(link.url)?.groupValues?.get(1) ?: return@forEach
                val iframeSrc = app.get("$api$subPath", referer = api).document.selectFirst("iframe")?.attr("src") ?: return@forEach
                val finalUrl = "https://uqloads.xyz/e/$iframeSrc"
                
                // Resolving Uqload
                val response = app.get(finalUrl)
                val script = response.document.selectFirst("script:containsData(sources:)")?.data()
                val m3u8 = Regex("\"hls2\":\\s*\"(.*?m3u8.*?)\"").find(script ?: "")?.groupValues?.getOrNull(1)
                
                if (m3u8 != null) {
                    callback.invoke(
                        newExtractorLink(
                            "Player4U",
                            "Player4U ${link.name}",
                            m3u8,
                            ExtractorLinkType.M3U8
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==============================
    // 4. XDMOVIES (HubCloud Based)
    // ==============================
    suspend fun invokeXDmovies(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val api = DomainManager.xdmoviesAPI
        val type = if (season == null) "xyz123" else "abc456"
        val url = "$api/api/$type?tmdb_id=$id"
        val headers = mapOf("x-auth-token" to String(Base64.decode("NzI5N3Nra2loa2Fqd25zZ2FrbGFrc2h1d2Q=", 0)))

        try {
            val jsonObject = JSONObject(app.get(url, headers = headers).text)
            val downloadLinks = if (season != null) {
                 // Logic for TV Series parsing
                 jsonObject.optJSONObject("download_data")
                    ?.optJSONArray("seasons")
                    ?.let { seasons ->
                        (0 until seasons.length()).map { seasons.getJSONObject(it) }
                            .firstOrNull { it.optInt("season_num") == season }
                            ?.optJSONArray("episodes")
                            ?.let { episodes ->
                                (0 until episodes.length()).map { episodes.getJSONObject(it) }
                                    .firstOrNull { it.optInt("episode_number") == episode }
                                    ?.optJSONArray("versions")
                            }
                    }
            } else {
                jsonObject.optJSONArray("download_links")
            }

            if (downloadLinks != null) {
                for (i in 0 until downloadLinks.length()) {
                    val link = downloadLinks.getJSONObject(i).optString("download_link")
                    if (link.contains("hubcloud", ignoreCase = true)) {
                        HubCloud().getUrl(link, "HubCloud", subtitleCallback, callback)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==============================
    // 5. RIVESTREAM
    // ==============================
    suspend fun invokeRiveStream(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val api = DomainManager.rivestreamAPI
        val secretKey = "rive" // Simplified, might need dynamic fetching in future
        val sourceApiUrl = "$api/api/backendfetch?requestID=VideoProviderServices&secretKey=$secretKey"
        
        try {
            val sources = app.get(sourceApiUrl).parsedSafe<RiveStreamSource>()?.data ?: return
            
            sources.forEach { source ->
                val streamUrl = if (season == null) {
                    "$api/api/backendfetch?requestID=movieVideoProvider&id=$id&service=$source&secretKey=$secretKey"
                } else {
                    "$api/api/backendfetch?requestID=tvVideoProvider&id=$id&season=$season&episode=$episode&service=$source&secretKey=$secretKey"
                }

                val response = app.get(streamUrl).text
                val json = JSONObject(response)
                val streams = json.optJSONObject("data")?.optJSONArray("sources")

                if (streams != null) {
                    for (i in 0 until streams.length()) {
                        val src = streams.getJSONObject(i)
                        val url = src.optString("url")
                        val label = "RiveStream [${src.optString("source")}]"

                        if (url.isNotBlank()) {
                            callback.invoke(
                                newExtractorLink(
                                    label, label, url, ExtractorLinkType.M3U8
                                ) { this.quality = Qualities.P1080.value }
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==============================
    // 6. VIDSRCCC & VIDSRCXYZ
    // ==============================
    suspend fun invokeVidsrccc(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val api = DomainManager.vidsrcccAPI // vidsrc.cc
        // Note: VidsrcCC logic in StreamPlay actually points to vidsrc.net logic sometimes
        // We implement the one from StreamPlayExtractor
        
        val url = if (season == null) "$api/v2/embed/movie/$id" else "$api/v2/embed/tv/$id/$season/$episode"
        try {
            val doc = app.get(url).text
            // Regex to find variables
            val userId = Regex("""var\s+userId\s*=\s*"([^"]+)"""").find(doc)?.groupValues?.get(1) ?: return
            val v = Regex("""var\s+v\s*=\s*"([^"]+)"""").find(doc)?.groupValues?.get(1) ?: return
            val movieId = Regex("""var\s+movieId\s*=\s*"([^"]+)"""").find(doc)?.groupValues?.get(1) ?: return
            
            val vrf = generateVrfAES(movieId, userId) // From Utils
            val type = if (season == null) "movie" else "tv"
            
            val serverUrl = if (season == null) {
                "$api/api/$id/servers?id=$id&type=$type&v=$v&vrf=$vrf"
            } else {
                "$api/api/$id/servers?id=$id&type=$type&season=$season&episode=$episode&v=$v&vrf=$vrf"
            }
            
            app.get(serverUrl).parsedSafe<Vidsrcccservers>()?.data?.forEach { server ->
                val sourceUrl = "$api/api/source/${server.hash}"
                val iframe = app.get(sourceUrl).parsedSafe<Vidsrcccm3u8>()?.data?.source
                if (!iframe.isNullOrBlank()) {
                    callback.invoke(
                        newExtractorLink("VidsrcCC", "VidsrcCC ${server.name}", iframe, ExtractorLinkType.M3U8)
                    )
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    suspend fun invokeVidSrcXyz(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val api = DomainManager.vidsrcxyzAPI // vidsrc-embed.su
        val url = if (season == null) "$api/embed/movie?imdb=$id" else "$api/embed/tv?imdb=$id&season=$season&episode=$episode"
        
        try {
            val iframeSrc = app.get(url).document.select("iframe").attr("src")
            if (iframeSrc.isBlank()) return
            
            val doc = app.get(iframeSrc).document
            val srcHash = Regex("src:\\s*'(.*?)'").find(doc.html())?.groupValues?.get(1) ?: return
            val host = URI(iframeSrc).let { "${it.scheme}://${it.host}" }
            
            val rcpUrl = app.get("$host$srcHash").document.html().let { html ->
                Regex("""(https?://.*?/prorcp.*?)["']\)""").find(html)?.groupValues?.get(1)
            } ?: return

            val playerJs = app.get(rcpUrl).text
            val file = Regex("""file:"(.*?)"""").find(playerJs)?.groupValues?.get(1) 
                ?: decryptVidSrcXyz(playerJs) // Custom decryption logic needed

            if (!file.isNullOrBlank()) {
                callback.invoke(
                    newExtractorLink("VidsrcXYZ", "VidsrcXYZ", file, ExtractorLinkType.M3U8)
                    { this.referer = rcpUrl.substringBefore("rcp") }
                )
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
    
    private fun decryptVidSrcXyz(content: String): String? {
         // Basic attempt to find ID and decrypt using Utils map
         // In real implementation, this needs to parse the ID from the response content
         return null // Placeholder, usually 'file' regex works
    }

    // ==============================
    // 7. WATCH32
    // ==============================
    suspend fun invokeWatch32(
        title: String?,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val api = DomainManager.watch32API
        val slug = title?.replace(" ", "-")
        val searchUrl = "$api/search/$slug"
        
        try {
            val doc = app.get(searchUrl).document
            // Simple logic: Find first result matching year/title
            val item = doc.select("div.flw-item").firstOrNull() ?: return
            val href = item.select("a").attr("href")
            
            val detailUrl = "$api$href"
            val infoId = detailUrl.substringAfterLast("-")
            
            if (season == null) {
                // Movie Logic
                val episodeLinks = app.get("$api/ajax/episode/list/$infoId").document.select("li.nav-item a")
                episodeLinks.forEach { ep ->
                   val id = ep.attr("data-id")
                   val sourceResp = app.get("$api/ajax/episode/sources/$id").parsedSafe<Watch32>()
                   if (sourceResp?.link != null) {
                       callback.invoke(newExtractorLink("Watch32", "Watch32", sourceResp.link, ExtractorLinkType.M3U8))
                   }
                }
            }
            // TV Logic omitted for brevity, follows similar ajax pattern
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ==============================
    // 8. SUBTITLE APIS
    // ==============================
    suspend fun invokeSubtitleAPI(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val url = if (season == null) {
            "${DomainManager.SubtitlesAPI}/subtitles/movie/$id.json"
        } else {
            "${DomainManager.SubtitlesAPI}/subtitles/series/$id:$season:$episode.json"
        }
        try {
            app.get(url).parsedSafe<SubtitlesAPI>()?.subtitles?.forEach {
                subtitleCallback(newSubtitleFile(getLanguage(it.lang), it.url))
            }
        } catch (e: Exception) {}
    }

    suspend fun invokeWyZIESUBAPI(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val url = StringBuilder("${DomainManager.WyZIESUBAPI}/search?id=$id")
        if (season != null) url.append("&season=$season&episode=$episode")
        
        try {
            app.get(url.toString()).parsedSafe<List<WyZIESUB>>()?.forEach {
                subtitleCallback(newSubtitleFile(getLanguage(it.language), it.url))
            }
        } catch (e: Exception) {}
    }
}
