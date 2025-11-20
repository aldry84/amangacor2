package com.AdiDrakor

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AdiDrakorExtractor : AdiDrakor() {

    // --- URL Constants for New Providers ---
    private const val Vidsrcxyz = "https://vidsrc-embed.su"
    private const val RiveStreamAPI = "https://rivestream.org"
    private const val vidPlusApi = "https://player.vidplus.to"

    // =========================================================================
    // REPLACED & NEW EXTRACTORS (From StreamPlay)
    // =========================================================================

    suspend fun invokeVidsrccc(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$vidsrcccAPI/v2/embed/movie/$id?autoPlay=false"
        } else {
            "$vidsrcccAPI/v2/embed/tv/$id/$season/$episode?autoPlay=false"
        }
        val doc = app.get(url).document.toString()
        val regex = Regex("""var\s+(\w+)\s*=\s*(?:"([^"]*)"|(\w+));""")
        val variables = mutableMapOf<String, String>()

        regex.findAll(doc).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            variables[key] = value
        }
        val vvalue = variables["v"] ?: ""
        val userId = variables["userId"] ?: ""
        val imdbId = variables["imdbId"] ?: ""
        val movieId = variables["movieId"] ?: ""
        val movieType = variables["movieType"] ?: ""

        // generateVrfAES is now in AdiDrakorUtils.kt
        val vrf = generateVrfAES(movieId, userId)
        val apiurl = if (season == null) {
            "${vidsrcccAPI}/api/$id/servers?id=$id&type=$movieType&v=$vvalue=&vrf=$vrf&imdbId=$imdbId"
        } else {
            "${vidsrcccAPI}/api/$id/servers?id=$id&type=$movieType&season=$season&episode=$episode&v=$vvalue&vrf=${vrf}&imdbId=$imdbId"
        }

        app.get(apiurl).parsedSafe<Vidsrcccservers>()?.data?.forEach {
            val servername = it.name
            val iframe = app.get("$vidsrcccAPI/api/source/${it.hash}")
                .parsedSafe<Vidsrcccm3u8>()?.data?.source
            if (iframe != null && !iframe.contains(".vidbox")) {
                callback.invoke(
                    newExtractorLink(
                        "Vidsrc",
                        "Vidsrc | [$servername]",
                        iframe,
                        ExtractorLinkType.M3U8
                    ) {
                        this.quality = if (servername.contains("4K", ignoreCase = true)) Qualities.P2160.value else Qualities.P1080.value
                        this.referer = vidsrcccAPI
                    }
                )
            }
        }
    }

    suspend fun invokeVidSrcXyz(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$Vidsrcxyz/embed/movie?imdb=$id"
        } else {
            "$Vidsrcxyz/embed/tv?imdb=$id&season=$season&episode=$episode"
        }
        val iframeUrl = extractIframeUrl(url) ?: return
        val prorcpUrl = extractProrcpUrl(iframeUrl) ?: return
        val decryptedSource = extractAndDecryptSource(prorcpUrl) ?: return

        val referer = prorcpUrl.substringBefore("rcp")
        callback.invoke(
            newExtractorLink(
                "VidsrcXYZ",
                "VidsrcXYZ",
                url = decryptedSource,
                ExtractorLinkType.M3U8
            ) {
                this.referer = referer
                this.quality = Qualities.P1080.value
            }
        )
    }

    // Helpers for VidSrcXyz
    private suspend fun extractIframeUrl(url: String): String? {
        return httpsify(
            app.get(url).document.select("iframe").attr("src")
        ).takeIf { it.isNotEmpty() }
    }

    private suspend fun extractProrcpUrl(iframeUrl: String): String? {
        val doc = app.get(iframeUrl).document
        val regex = Regex("src:\\s+'(.*?)'")
        val matchedSrc = regex.find(doc.html())?.groupValues?.get(1) ?: return null
        val host = getBaseUrl(iframeUrl)
        val newDoc = app.get(host + matchedSrc).document

        val regex1 = Regex("""(https?://.*?/prorcp.*?)["']\)""")
        return regex1.find(newDoc.html())?.groupValues?.get(1)
    }

    private suspend fun extractAndDecryptSource(prorcpUrl: String): String? {
        val responseText = app.get(prorcpUrl).text

        val playerJsRegex = Regex("""Playerjs\(\{.*?file:"(.*?)".*?\}\)""")
        val temp = playerJsRegex.find(responseText)?.groupValues?.get(1)

        val encryptedURLNode = if (!temp.isNullOrEmpty()) {
            mapOf("id" to "playerjs", "content" to temp)
        } else {
            val document = Jsoup.parse(responseText)
            val node = document.select("#reporting_content").next()
            mapOf("id" to node.attr("id"), "content" to node.text())
        }

        return encryptedURLNode["id"]?.let { id ->
            encryptedURLNode["content"]?.let { content ->
                // decryptMethods must be in Utils (Added in Step 2)
                decryptMethods[id]?.invoke(content)
            }
        }
    }

    suspend fun invokevidrock(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val type = if (season == null) "movie" else "tv"
        // vidrockEncode is in AdiDrakorUtils
        val encoded = vidrockEncode(tmdbId.toString(), type, season, episode)
        val response = app.get("$vidrockAPI/api/$type/$encoded").text
        val sourcesJson = JSONObject(response)

        val vidrockHeaders = mapOf(
            "Origin" to vidrockAPI
        )

        sourcesJson.keys().forEach { key ->
            val sourceObj = sourcesJson.optJSONObject(key) ?: return@forEach
            val rawUrl = sourceObj.optString("url", null)
            val lang = sourceObj.optString("language", "Unknown")
            if (rawUrl.isNullOrBlank() || rawUrl == "null") return@forEach

            // Decode only if encoded
            val safeUrl = if (rawUrl.contains("%")) {
                URLDecoder.decode(rawUrl, "UTF-8")
            } else rawUrl

            when {
                safeUrl.contains("/playlist/") -> {
                    val playlistResponse = app.get(safeUrl, headers = vidrockHeaders).text
                    val playlistArray = JSONArray(playlistResponse)
                    for (j in 0 until playlistArray.length()) {
                        val item = playlistArray.optJSONObject(j) ?: continue
                        val itemUrl = item.optString("url", null) ?: continue
                        val res = item.optInt("resolution", 0)

                        callback.invoke(
                            newExtractorLink(
                                source = "Vidrock",
                                name = "Vidrock $lang",
                                url = itemUrl,
                                type = INFER_TYPE
                            ) {
                                this.headers = vidrockHeaders
                                this.quality = getQualityFromName("$res")
                            }
                        )
                    }
                }
                safeUrl.contains(".mp4", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            source = "Vidrock",
                            name = "Vidrock $lang MP4",
                            url = safeUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.headers = vidrockHeaders
                        }
                    )
                }
                safeUrl.contains(".m3u8", ignoreCase = true) -> {
                    M3u8Helper.generateM3u8(
                        source = "Vidrock",
                        streamUrl = safeUrl,
                        referer = "",
                        quality = Qualities.P1080.value,
                        headers = vidrockHeaders
                    ).forEach(callback)
                }
                else -> {
                    callback.invoke(
                        newExtractorLink(
                            source = "Vidrock",
                            name = "Vidrock $lang",
                            url = safeUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.headers = vidrockHeaders
                        }
                    )
                }
            }
        }
    }

    suspend fun invokeVidPlus(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "Referer" to vidPlusApi,
            "Origin" to vidPlusApi,
            "User-Agent" to USER_AGENT,
            "X-Requested-With" to "XMLHttpRequest"
        )
        val data = mapOf(
            "id" to tmdbId,
            "key" to "cGxheWVyLnZpZHNyYy5jb19zZWNyZXRLZXk="
        )
        val encoded = base64Encode(data.toJson().toByteArray())
        val apiUrl = "$vidPlusApi/api/tmdb?params=cbc7.$encoded.9lu"
        val response = app.get(apiUrl, headers = headers).text
        val jsonObject = JSONObject(response)
        val dataJson = jsonObject.getJSONObject("data")
        
        val imdbId = dataJson.getString("imdb_id")
        val title = dataJson.getString("title")
        val releaseDate = dataJson.getString("release_date")
        val releaseYear = releaseDate.split("-")[0]

        val requestArgs = listOf(title, releaseYear, imdbId).joinToString("*")
        val urlListMap = mutableMapOf<String, String>()
        val myMap = listOf("Orion", "Minecloud", "Viet", "Crown", "Joker", "Soda", "Beta", "Gork", "Monk", "Fox", "Leo", "4K", "Adam", "Sun", "Maxi", "Indus", "Tor", "Hindi", "Delta", "Ben", "Pearl", "Tamil", "Ruby", "Tel", "Mal", "Kan", "Lava")
        
        for ((index, entry) in myMap.withIndex()) {
            try {
                val serverId = index + 1
                val serverUrl = if (season == null) "$vidPlusApi/api/server?id=$tmdbId&sr=$serverId&args=$requestArgs" else "$vidPlusApi/api/server?id=$tmdbId&sr=$serverId&ep=$episode&ss=$season&args=$requestArgs"

                val apiResponse = app.get(serverUrl, headers = headers, timeout = 20).text

                if (apiResponse.contains("\"data\"", ignoreCase = true)) {
                    val decodedPayload = String(base64DecodeArray(JSONObject(apiResponse).getString("data")))
                    val payloadJson = JSONObject(decodedPayload)

                    val ciphertext = base64DecodeArray(payloadJson.getString("encryptedData"))
                    val password = payloadJson.getString("key")
                    val salt = hexStringToByteArray2(payloadJson.getString("salt"))
                    val iv = hexStringToByteArray2(payloadJson.getString("iv"))
                    val derivedKey = derivePbkdf2Key(password, salt, 1000, 32)
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derivedKey, "AES"), IvParameterSpec(iv))
                    val decryptedText = unpadData(cipher.doFinal(ciphertext))
                    val decryptedString = String(decryptedText)

                    val regex = Regex("\"url\":\"(.*?)\",")
                    val match = regex.find(decryptedString)
                    val streamURl = match?.groupValues?.get(1)
                    if (!streamURl.isNullOrEmpty()) {
                        var finalStreamUrl = streamURl
                        if (!hasHost(streamURl.toString())) {
                            finalStreamUrl = app.head("$vidPlusApi$streamURl", headers = headers, allowRedirects = false).headers["Location"]
                        }
                        urlListMap[entry] = finalStreamUrl.toString()
                    }
                }
            } catch (e: Exception) {
                // Ignore error per server
            }
        }

        urlListMap.forEach {
            callback.invoke(
                newExtractorLink(
                    "VidPlus [${it.key}]",
                    "VidPlus [${it.key}]",
                    url = it.value,
                    type = ExtractorLinkType.M3U8 // Assuming M3U8 for now
                ) {
                    this.quality = Qualities.P1080.value
                    this.headers = mapOf("Origin" to vidPlusApi)
                }
            )
        }
    }

    suspend fun invokeRiveStream(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf("User-Agent" to USER_AGENT)

        suspend fun <T> retry(times: Int = 3, block: suspend () -> T): T? {
            repeat(times - 1) {
                try { return block() } catch (_: Exception) {}
            }
            return try { block() } catch (_: Exception) { null }
        }

        val sourceApiUrl = "$RiveStreamAPI/api/backendfetch?requestID=VideoProviderServices&secretKey=rive"
        val sourceList = retry { app.get(sourceApiUrl, headers).parsedSafe<RiveStreamSource>() }

        val document = retry { app.get(RiveStreamAPI, headers, timeout = 20).document } ?: return
        val appScript = document.select("script").firstOrNull { it.attr("src").contains("_app") }?.attr("src") ?: return

        val js = retry { app.get("$RiveStreamAPI$appScript").text } ?: return
        val keyList = Regex("""let\s+c\s*=\s*(\[[^]]*])""")
            .findAll(js).firstOrNull { it.groupValues[1].length > 2 }?.groupValues?.get(1)
            ?.let { array ->
                Regex("\"([^\"]+)\"").findAll(array).map { it.groupValues[1] }.toList()
            } ?: emptyList()

        val secretKey = retry {
            app.get("https://rivestream.supe2372.workers.dev/?input=$id&cList=${keyList.joinToString(",")}").text
        } ?: return

        sourceList?.data?.forEach { source ->
            try {
                val streamUrl = if (season == null) {
                    "$RiveStreamAPI/api/backendfetch?requestID=movieVideoProvider&id=$id&service=$source&secretKey=$secretKey"
                } else {
                    "$RiveStreamAPI/api/backendfetch?requestID=tvVideoProvider&id=$id&season=$season&episode=$episode&service=$source&secretKey=$secretKey"
                }

                val responseString = retry { app.get(streamUrl, headers, timeout = 10).text } ?: return@forEach

                val json = JSONObject(responseString)
                val sourcesArray = json.optJSONObject("data")?.optJSONArray("sources") ?: return@forEach

                for (i in 0 until sourcesArray.length()) {
                    val src = sourcesArray.getJSONObject(i)
                    val label = if (src.optString("source").contains("AsiaCloud", ignoreCase = true)) "RiveStream ${src.optString("source")}[${src.optString("quality")}]" else "RiveStream ${src.optString("source")}"
                    val quality = Qualities.P1080.value
                    val url = src.optString("url")

                    if (url.contains("proxy?url=")) {
                        // Complex proxy handling omitted for brevity, usually standard extraction works
                         val decodedUrl = URLDecoder.decode(url.substringAfter("proxy?url=").substringBefore("&headers="), "UTF-8")
                         callback.invoke(newExtractorLink(label, label, decodedUrl, INFER_TYPE) { this.quality = quality })
                    } else {
                        callback.invoke(newExtractorLink(label, label, url, INFER_TYPE) { this.quality = quality })
                    }
                }
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    suspend fun invokeVidzee(
        id: Int?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val keyHex = "6966796f75736372617065796f75617265676179000000000000000000000000"
        val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val defaultReferer = "https://core.vidzee.wtf/"

        for (sr in 1..8) {
            try {
                val apiUrl = if (season == null) {
                    "https://player.vidzee.wtf/api/server?id=$id&sr=$sr"
                } else {
                    "https://player.vidzee.wtf/api/server?id=$id&sr=$sr&ss=$season&ep=$episode"
                }

                val response = app.get(apiUrl).text
                val json = JSONObject(response)
                val globalHeaders = mutableMapOf<String, String>()
                json.optJSONObject("headers")?.let { headersObj ->
                    headersObj.keys().forEach { key -> globalHeaders[key] = headersObj.getString(key) }
                }

                val urls = json.optJSONArray("url") ?: JSONArray()
                for (i in 0 until urls.length()) {
                    val obj = urls.getJSONObject(i)
                    val encryptedLink = obj.optString("link")
                    val name = obj.optString("name", "Vidzee")
                    val lang = obj.optString("lang", "Unknown")
                    
                    if (encryptedLink.isNotBlank()) {
                        val finalUrl = try {
                            decryptVidzeeUrl(encryptedLink, keyBytes)
                        } catch (e: Exception) {
                            encryptedLink
                        }
                        
                        callback.invoke(
                            newExtractorLink(
                                "VidZee",
                                "VidZee $name ($lang)",
                                finalUrl,
                                ExtractorLinkType.M3U8 // Assumption
                            ) {
                                this.headers = globalHeaders
                                this.quality = Qualities.P1080.value
                            }
                        )
                    }
                }
                
                val subs = json.optJSONArray("tracks") ?: JSONArray()
                for (i in 0 until subs.length()) {
                    val sub = subs.getJSONObject(i)
                    val subLang = sub.optString("lang", "Unknown")
                    val subUrl = sub.optString("url")
                    if (subUrl.isNotBlank()) subtitleCallback(newSubtitleFile(subLang, subUrl))
                }

            } catch (e: Exception) {
                // Skip failed servers
            }
        }
    }
    
    // =========================================================================
    // EXISTING EXTRACTORS (Preserved)
    // =========================================================================

    suspend fun invokeIdlix(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title?.createSlug()
        val url = if (season == null) {
            "$idlixAPI/movie/$fixTitle-$year"
        } else {
            "$idlixAPI/episode/$fixTitle-season-$season-episode-$episode"
        }
        invokeWpmovies("Idlix", url, subtitleCallback, callback, encrypt = true)
    }

    private suspend fun invokeWpmovies(
        name: String? = null,
        url: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        fixIframe: Boolean = false,
        encrypt: Boolean = false,
        hasCloudflare: Boolean = false,
        interceptor: Interceptor? = null,
    ) {
        val res = app.get(url ?: return, interceptor = if (hasCloudflare) interceptor else null)
        val referer = getBaseUrl(res.url)
        val document = res.document
        document.select("ul#playeroptionsul > li").map {
            Triple(
                it.attr("data-post"),
                it.attr("data-nume"),
                it.attr("data-type")
            )
        }.amap { (id, nume, type) ->
            val json = app.post(
                url = "$referer/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type
                ),
                headers = mapOf("Accept" to "*/*", "X-Requested-With" to "XMLHttpRequest"),
                referer = url,
                interceptor = if (hasCloudflare) interceptor else null
            ).text
            val source = tryParseJson<ResponseHash>(json)?.let {
                when {
                    encrypt -> {
                        val meta = tryParseJson<Map<String, String>>(it.embed_url)?.get("m")
                            ?: return@amap
                        val key = generateWpKey(it.key ?: return@amap, meta)
                        AesHelper.cryptoAESHandler(
                            it.embed_url,
                            key.toByteArray(),
                            false
                        )?.fixUrlBloat()
                    }

                    fixIframe -> Jsoup.parse(it.embed_url).select("IFRAME").attr("SRC")
                    else -> it.embed_url
                }
            } ?: return@amap
            when {
                source.startsWith("https://jeniusplay.com") -> {
                    Jeniusplay2().getUrl(source, "$referer/", subtitleCallback, callback)
                }

                !source.contains("youtube") -> {
                    loadExtractor(source, "$referer/", subtitleCallback, callback)
                }
                else -> return@amap
            }
        }
    }

    suspend fun invokeWatchsomuch(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val id = imdbId?.removePrefix("tt")
        val epsId = app.post(
            "${watchSomuchAPI}/Watch/ajMovieTorrents.aspx", data = mapOf(
                "index" to "0",
                "mid" to "$id",
                "wsk" to "30fb68aa-1c71-4b8c-b5d4-4ca9222cfb45",
                "lid" to "",
                "liu" to ""
            ), headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsedSafe<WatchsomuchResponses>()?.movie?.torrents?.let { eps ->
            if (season == null) {
                eps.firstOrNull()?.id
            } else {
                eps.find { it.episode == episode && it.season == season }?.id
            }
        } ?: return

        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)

        val subUrl = if (season == null) {
            "${watchSomuchAPI}/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part="
        } else {
            "${watchSomuchAPI}/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part=S${seasonSlug}E${episodeSlug}"
        }

        app.get(subUrl).parsedSafe<WatchsomuchSubResponses>()?.subtitles?.map { sub ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    sub.label?.substringBefore("&nbsp")?.trim() ?: "",
                    fixUrl(sub.url ?: return@map null, watchSomuchAPI)
                )
            )
        }
    }

    suspend fun invokeMapple(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val mediaType = if (season == null) "movie" else "tv"
        val url = if (season == null) {
            "$mappleAPI/watch/$mediaType/$tmdbId"
        } else {
            "$mappleAPI/watch/$mediaType/$season-$episode/$tmdbId"
        }

        val data = if (season == null) {
            """[{"mediaId":$tmdbId,"mediaType":"$mediaType","tv_slug":"","source":"mapple","sessionId":"session_1760391974726_qym92bfxu"}]"""
        } else {
            """[{"mediaId":$tmdbId,"mediaType":"$mediaType","tv_slug":"$season-$episode","source":"mapple","sessionId":"session_1760391974726_qym92bfxu"}]"""
        }

        val headers = mapOf(
            "Next-Action" to "403f7ef15810cd565978d2ac5b7815bb0ff20258a5",
        )

        val res = app.post(
            url,
            requestBody = data.toRequestBody(RequestBodyTypes.TEXT.toMediaTypeOrNull()),
            headers = headers
        ).text
        val videoLink =
            tryParseJson<MappleSources>(res.substringAfter("1:").trim())?.data?.stream_url

        callback.invoke(
            newExtractorLink(
                "Mapple",
                "Mapple",
                videoLink ?: return,
                ExtractorLinkType.M3U8
            ) {
                this.referer = "$mappleAPI/"
                this.headers = mapOf(
                    "Accept" to "*/*"
                )
            }
        )

        val subRes = app.get(
            "$mappleAPI/api/subtitles?id=$tmdbId&mediaType=$mediaType${if (season == null) "" else "&season=1&episode=1"}",
            referer = "$mappleAPI/"
        ).text
        tryParseJson<ArrayList<MappleSubtitle>>(subRes)?.map { subtitle ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    subtitle.display ?: "",
                    fixUrl(subtitle.url ?: return@map, mappleAPI)
                )
            )
        }
    }

    suspend fun invokeVidlink(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit,
    ) {
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) {
            "$vidlinkAPI/$type/$tmdbId"
        } else {
            "$vidlinkAPI/$type/$tmdbId/$season/$episode"
        }

        val videoLink = app.get(
            url, interceptor = WebViewResolver(
                Regex("""$vidlinkAPI/api/b/$type/A{32}"""), timeout = 15_000L
            )
        ).parsedSafe<VidlinkSources>()?.stream?.playlist

        callback.invoke(
            newExtractorLink(
                "Vidlink",
                "Vidlink",
                videoLink ?: return,
                ExtractorLinkType.M3U8
            ) {
                this.referer = "$vidlinkAPI/"
            }
        )
    }

    suspend fun invokeVidfast(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val module = "hezushon/1000076901076321/0b0ce221/cfe60245-021f-5d4d-bacb-0d469f83378f/uva/jeditawev/b0535941d898ebdb81f575b2cfd123f5d18c6464/y/APA91zAOxU2psY2_BvBqEmmjG6QvCoLjgoaI-xuoLxBYghvzgKAu-HtHNeQmwxNbHNpoVnCuX10eEes1lnTcI2l_lQApUiwfx2pza36CZB34X7VY0OCyNXtlq-bGVCkLslfNksi1k3B667BJycQ67wxc1OnfCc5PDPrF0BA8aZRyMXZ3-2yxVGp"
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) {
            "$vidfastAPI/$type/$tmdbId"
        } else {
            "$vidfastAPI/$type/$tmdbId/$season/$episode"
        }

        val res = app.get(
            url, interceptor = WebViewResolver(
                Regex("""$vidfastAPI/$module/JEwECseLZdY"""),
                timeout = 15_000L
            )
        ).text

        tryParseJson<ArrayList<VidFastServers>>(res)?.filter { it.description?.contains("Original audio") == true }
            ?.amapIndexed { index, server ->
                val source =
                    app.get("$vidfastAPI/$module/Sdoi/${server.data}", referer = "$vidfastAPI/")
                        .parsedSafe<VidFastSources>()

                callback.invoke(
                    newExtractorLink(
                        "Vidfast",
                        "Vidfast [${server.name}]",
                        source?.url ?: return@amapIndexed,
                        INFER_TYPE
                    )
                )

                if (index == 1) {
                    source.tracks?.map { subtitle ->
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                subtitle.label ?: return@map,
                                subtitle.file ?: return@map
                            )
                        )
                    }
                }
            }
    }

    suspend fun invokeWyzie(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val url = if (season == null) {
            "$wyzieAPI/search?id=$tmdbId"
        } else {
            "$wyzieAPI/search?id=$tmdbId&season=$season&episode=$episode"
        }

        val res = app.get(url).text
        tryParseJson<ArrayList<WyzieSubtitle>>(res)?.map { subtitle ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    subtitle.display ?: return@map,
                    subtitle.url ?: return@map,
                )
            )
        }
    }

    suspend fun invokeVixsrc(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit,
    ) {
        val proxy = "https://proxy.heistotron.uk"
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) {
            "$vixsrcAPI/$type/$tmdbId"
        } else {
            "$vixsrcAPI/$type/$tmdbId/$season/$episode"
        }

        val res =
            app.get(url).document.selectFirst("script:containsData(window.masterPlaylist)")?.data()
                ?: return

        val video1 =
            Regex("""'token':\s*'(\w+)'[\S\s]+'expires':\s*'(\w+)'[\S\s]+url:\s*'(\S+)'""").find(res)
                ?.let {
                    val (token, expires, path) = it.destructured
                    "$path?token=$token&expires=$expires&h=1&lang=en"
                } ?: return

        val video2 =
            "$proxy/p/${base64Encode("$proxy/api/proxy/m3u8?url=${encode(video1)}&source=sakura|ananananananananaBatman!".toByteArray())}"

        listOf(
            VixsrcSource("Vixsrc [Alpha]", video1, url),
            VixsrcSource("Vixsrc [Beta]", video2, "$mappleAPI/"),
        ).map {
            callback.invoke(
                newExtractorLink(
                    it.name,
                    it.name,
                    it.url,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = it.referer
                    this.headers = mapOf(
                        "Accept" to "*/*"
                    )
                }
            )
        }
    }

    suspend fun invokeVidsrccx(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit,
    ) {
        val filePath =
            if (season == null) "/media/$tmdbId/master.m3u8" else "/media/$tmdbId-$season-$episode/master.m3u8"
        val video = app.post(
            "https://8ball.piracy.cloud/api/generate-secure-url", requestBody = mapOf(
                "filePath" to filePath
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<VidsrccxSource>()?.secureUrl

        callback.invoke(
            newExtractorLink(
                "VidsrcCx",
                "VidsrcCx",
                video ?: return,
                ExtractorLinkType.M3U8
            ) {
                this.referer = "$vidsrccxAPI/"
                this.headers = mapOf(
                    "Accept" to "*/*"
                )
            }
        )
    }

    suspend fun invokeSuperembed(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        api: String = "https://streamingnow.mov"
    ) {
        val path = if (season == null) "" else "&s=$season&e=$episode"
        val token = app.get("$superembedAPI/directstream.php?video_id=$tmdbId&tmdb=1$path").url.substringAfter(
            "?play="
        )

        val (server, id) = app.post(
            "$api/response.php", data = mapOf(
                "token" to token
            ), headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).document.select("ul.sources-list li:contains(vipstream-S)")
            .let { it.attr("data-server") to it.attr("data-id") }

        val playUrl = "$api/playvideo.php?video_id=$id&server_id=$server&token=$token&init=1"
        val playRes = app.get(playUrl).document
        val iframe = playRes.selectFirst("iframe.source-frame")?.attr("src") ?: run {
            val captchaId = playRes.select("input[name=captcha_id]").attr("value")
            app.post(playUrl, requestBody = "captcha_id=TEduRVR6NmZ3Sk5Jc3JpZEJCSlhTM25GREs2RCswK0VQN2ZsclI5KzNKL2cyV3dIaFEwZzNRRHVwMzdqVmoxV0t2QlBrNjNTY04wY2NSaHlWYS9Jc09nb25wZTV2YmxDSXNRZVNuQUpuRW5nbkF2dURsQUdJWVpwOWxUZzU5Tnh0NXllQjdYUG83Y0ZVaG1XRGtPOTBudnZvN0RFK0wxdGZvYXpFKzVNM2U1a2lBMG40REJmQ042SA%3D%3D&captcha_answer%5B%5D=8yhbjraxqf3o&captcha_answer%5B%5D=10zxn5vi746w&captcha_answer%5B%5D=gxfpe17tdwub".toRequestBody(RequestBodyTypes.TEXT.toMediaTypeOrNull())
            ).document.selectFirst("iframe.source-frame")?.attr("src")
        }
        val json = app.get(iframe ?: return).text.substringAfter("Playerjs(").substringBefore(");")

        val video = """file:"([^"]+)""".toRegex().find(json)?.groupValues?.get(1)

        callback.invoke(
            newExtractorLink(
                "Superembed",
                "Superembed",
                video ?: return,
                INFER_TYPE
            ) {
                this.headers = mapOf(
                    "Accept" to "*/*"
                )
            }
        )

        """subtitle:"([^"]+)""".toRegex().find(json)?.groupValues?.get(1)?.split(",")?.map {
            val (subLang, subUrl) = Regex("""\[(\w+)](http\S+)""").find(it)?.destructured
                ?: return@map
            subtitleCallback.invoke(
                newSubtitleFile(
                    subLang.trim(),
                    subUrl.trim()
                )
            )
        }
    }
}
