package com.AsianDrama

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.net.URI

object AsianDramaExtractor {
    private const val vidsrcccAPI = "https://vidsrc.cc"
    private const val vidSrcAPI = "https://vidsrc.net"
    private const val superembedAPI = "https://multiembed.mov"
    private const val idlixAPI = "https://tv6.idlixku.com"

    suspend fun invokeAllExtractors(
        data: AsianDrama.StreamData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Run extractors sequentially for stability
        invokeVidsrccc(data, subtitleCallback, callback)
        invokeVidsrc(data, subtitleCallback, callback)
        invokeIdlix(data, subtitleCallback, callback)
        invokeSuperembed(data, subtitleCallback, callback)
    }

    private suspend fun invokeVidsrccc(
        data: AsianDrama.StreamData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val url = if (data.type == "movie") {
                "$vidsrcccAPI/v2/embed/movie/${data.tmdbId}"
            } else {
                "$vidsrcccAPI/v2/embed/tv/${data.tmdbId}/${data.season}/${data.episode}"
            }

            val script = app.get(url).document.selectFirst("script:containsData(userId)")?.data() ?: return
            val userId = script.substringAfter("userId = \"").substringBefore("\";")
            val v = script.substringAfter("v = \"").substringBefore("\";")

            // Simplified VRF for basic functionality
            val vrf = "simple_${data.tmdbId}_${userId}"

            val serverUrl = if (data.type == "movie") {
                "$vidsrcccAPI/api/${data.tmdbId}/servers?id=${data.tmdbId}&type=movie&v=$v&vrf=$vrf&imdbId=${data.imdbId}"
            } else {
                "$vidsrcccAPI/api/${data.tmdbId}/servers?id=${data.tmdbId}&type=tv&v=$v&vrf=$vrf&imdbId=${data.imdbId}&season=${data.season}&episode=${data.episode}"
            }

            app.get(serverUrl).parsedSafe<VidsrcccResponse>()?.data?.forEach { server ->
                when {
                    server.name.equals("VidPlay", ignoreCase = true) -> {
                        val sources = app.get("$vidsrcccAPI/api/source/${server.hash}")
                            .parsedSafe<VidsrcccResult>()?.data
                        
                        sources?.source?.let { m3u8Url ->
                            callback.invoke(
                                newExtractorLink(
                                    "VidPlay",
                                    "VidPlay",
                                    m3u8Url,
                                    ExtractorLinkType.M3U8
                                ) {
                                    this.referer = "$vidsrcccAPI/"
                                }
                            )
                        }

                        sources?.subtitles?.forEach { sub ->
                            subtitleCallback.invoke(
                                SubtitleFile(
                                    sub.label ?: "Unknown",
                                    sub.file ?: return@forEach
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Continue with other extractors
        }
    }

    private suspend fun invokeVidsrc(
        data: AsianDrama.StreamData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val url = if (data.type == "movie") {
                "$vidSrcAPI/embed/movie?imdb=${data.imdbId}"
            } else {
                "$vidSrcAPI/embed/tv?imdb=${data.imdbId}&season=${data.season}&episode=${data.episode}"
            }

            app.get(url).document.select(".server").forEach { server ->
                when {
                    server.text().contains("CloudStream", ignoreCase = true) -> {
                        val hash = server.attr("data-hash")
                        // Basic CloudStream extraction
                        extractCloudStreamSource(hash, callback)
                    }
                }
            }
        } catch (e: Exception) {
            // Continue with other extractors
        }
    }

    private suspend fun invokeIdlix(
        data: AsianDrama.StreamData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val fixTitle = data.title?.createSlug()
            val url = if (data.type == "movie") {
                "$idlixAPI/movie/$fixTitle-${data.year}"
            } else {
                "$idlixAPI/episode/$fixTitle-season-${data.season}-episode-${data.episode}"
            }

            invokeWpmovies("Idlix", url, subtitleCallback, callback)
        } catch (e: Exception) {
            // Continue with other extractors
        }
    }

    private suspend fun invokeSuperembed(
        data: AsianDrama.StreamData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val path = if (data.type == "movie") "" else "&s=${data.season}&e=${data.episode}"
            val response = app.get("$superembedAPI/directstream.php?video_id=${data.tmdbId}&tmdb=1$path")
            val token = response.url.toString().substringAfter("?play=")

            val api = "https://streamingnow.mov"
            val playRes = app.post(
                "$api/response.php", 
                data = mapOf("token" to token),
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).document

            val serverElement = playRes.select("ul.sources-list li").firstOrNull()
            val server = serverElement?.attr("data-server") ?: return
            val id = serverElement.attr("data-id") ?: return

            val playUrl = "$api/playvideo.php?video_id=$id&server_id=$server&token=$token&init=1"
            val iframe = app.get(playUrl).document.selectFirst("iframe.source-frame")?.attr("src")
            
            iframe?.let { frameUrl ->
                val frameContent = app.get(frameUrl).text
                val jsonPart = frameContent.substringAfter("Playerjs(").substringBefore(");")
                val video = """file:\s*"([^"]+)""".toRegex().find(jsonPart)?.groupValues?.get(1)

                video?.let { 
                    callback.invoke(
                        newExtractorLink(
                            "Superembed",
                            "Superembed", 
                            it,
                            INFER_TYPE
                        )
                    )
                }

                // Extract subtitles
                """subtitle:\s*"([^"]+)""".toRegex().find(jsonPart)?.groupValues?.get(1)?.split(",")?.forEach { sub ->
                    val match = Regex("""\[(\w+)](http\S+)""").find(sub)
                    match?.let { m ->
                        val (subLang, subUrl) = m.destructured
                        subtitleCallback.invoke(
                            SubtitleFile(
                                subLang.trim(),
                                subUrl.trim()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Continue with other extractors
        }
    }

    // Helper functions
    private suspend fun invokeWpmovies(
        name: String,
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val res = app.get(url)
            val referer = getBaseUrl(res.url)
            val document = res.document

            document.select("ul#playeroptionsul > li").forEach { server ->
                val id = server.attr("data-post")
                val nume = server.attr("data-nume") 
                val type = server.attr("data-type")

                val json = app.post(
                    "$referer/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "doo_player_ajax",
                        "post" to id,
                        "nume" to nume, 
                        "type" to type
                    ),
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    referer = url
                ).text

                val source = tryParseJson<ResponseHash>(json)?.embed_url ?: return@forEach
                
                when {
                    source.contains("jeniusplay", ignoreCase = true) -> {
                        extractJeniusplaySource(source, referer, subtitleCallback, callback)
                    }
                    !source.contains("youtube") -> {
                        loadExtractor(source, "$referer/", subtitleCallback, callback)
                    }
                }
            }
        } catch (e: Exception) {
            // Continue
        }
    }

    private suspend fun extractCloudStreamSource(hash: String, callback: (ExtractorLink) -> Unit) {
        try {
            val api = "https://cloudnestra.com"
            val res = app.get("$api/rcp/$hash").text
            val m3u8Link = Regex("https:.*\\.m3u8").find(res)?.value
            
            m3u8Link?.let {
                callback.invoke(
                    newExtractorLink(
                        "CloudStream",
                        "CloudStream",
                        it,
                        ExtractorLinkType.M3U8
                    )
                )
            }
        } catch (e: Exception) {
            // Continue
        }
    }

    private suspend fun extractJeniusplaySource(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val document = app.get(url, referer = referer).document
            val hash = url.split("/").last().substringAfter("data=")

            val m3u8Link = app.post(
                "$url?data=$hash&do=getVideo",
                data = mapOf("hash" to hash, "r" to referer),
                referer = url,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).parsedSafe<JeniusplayResponse>()?.videoSource

            m3u8Link?.let {
                callback.invoke(
                    newExtractorLink(
                        "Jeniusplay",
                        "Jeniusplay", 
                        it,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                    }
                )
            }

            // Extract subtitles
            document.select("script").forEach { script ->
                if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                    try {
                        val unpacked = getAndUnpack(script.data())
                        val subData = unpacked.substringAfter("\"tracks\":[").substringBefore("],")
                        tryParseJson<List<Tracks>>("[$subData]")?.forEach { subtitle ->
                            subtitleCallback.invoke(
                                SubtitleFile(
                                    getLanguage(subtitle.label ?: ""),
                                    subtitle.file
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // Skip subtitle extraction if failed
                    }
                }
            }
        } catch (e: Exception) {
            // Continue
        }
    }

    private fun getLanguage(str: String): String {
        return when {
            str.contains("indonesia", true) || str.contains("bahasa", true) -> "Indonesian"
            else -> str
        }
    }

    private fun String.createSlug(): String {
        return this.filter { it.isWhitespace() || it.isLetterOrDigit() }
            .trim()
            .replace("\\s+".toRegex(), "-")
            .lowercase()
    }

    private fun getBaseUrl(url: String): String {
        return try {
            val uri = URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            "https://dramadrip.com"
        }
    }
}

// Data classes
data class VidsrcccServer(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("hash") val hash: String? = null,
)

data class VidsrcccResponse(
    @JsonProperty("data") val data: List<VidsrcccServer>? = null,
)

data class VidsrcccResult(
    @JsonProperty("data") val data: VidsrcccSources? = null,
)

data class VidsrcccSources(
    @JsonProperty("subtitles") val subtitles: List<VidsrcccSubtitles>? = null,
    @JsonProperty("source") val source: String? = null,
)

data class VidsrcccSubtitles(
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("file") val file: String? = null,
)

data class ResponseHash(
    @JsonProperty("embed_url") val embed_url: String = "",
    @JsonProperty("key") val key: String? = null,
)

data class JeniusplayResponse(
    @JsonProperty("videoSource") val videoSource: String = "",
)

data class Tracks(
    @JsonProperty("kind") val kind: String? = null,
    @JsonProperty("file") val file: String = "",
    @JsonProperty("label") val label: String? = null,
)
