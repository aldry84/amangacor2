// AsianDrama/src/main/kotlin/com/AsianDrama/AsianDramaExtractor.kt
package com.AsianDrama

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup

object AsianDramaExtractor {

    // SoraStream API endpoints
    private const val vidsrcccAPI = "https://vidsrc.cc"
    private const val vidSrcAPI = "https://vidsrc.net"
    private const val superembedAPI = "https://multiembed.mov"
    private const idlixAPI = "https://tv6.idlixku.com"

    suspend fun invokeAllExtractors(
        data: AsianDrama.StreamData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Run all extractors in parallel
        runAllAsync(
            {
                invokeVidsrccc(data, subtitleCallback, callback)
            },
            {
                invokeVidsrc(data, subtitleCallback, callback)
            },
            {
                invokeIdlix(data, subtitleCallback, callback)
            },
            {
                invokeSuperembed(data, subtitleCallback, callback)
            }
        )
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

            // Simple VRF simulation (simplified from SoraStream)
            val vrf = "vidsrc_${data.tmdbId}_${userId}"

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
                    server.name.equals("UpCloud", ignoreCase = true) -> {
                        // Handle UpCloud source
                        handleUpCloudSource(server.hash, subtitleCallback, callback)
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

            app.get(url).document.select(".serversList .server").forEach { server ->
                when {
                    server.text().contains("CloudStream", ignoreCase = true) -> {
                        val hash = server.attr("data-hash")
                        // Simplified CloudStream extraction
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
            val token = app.get("$superembedAPI/directstream.php?video_id=${data.tmdbId}&tmdb=1$path")
                .url.substringAfter("?play=")

            val api = "https://streamingnow.mov"
            val playRes = app.post(
                "$api/response.php", 
                data = mapOf("token" to token),
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).document

            val server = playRes.select("ul.sources-list li:contains(vipstream-S)")
                .attr("data-server")
            val id = playRes.select("ul.sources-list li:contains(vipstream-S)")
                .attr("data-id")

            val playUrl = "$api/playvideo.php?video_id=$id&server_id=$server&token=$token&init=1"
            val iframe = app.get(playUrl).document.selectFirst("iframe.source-frame")?.attr("src")
            
            iframe?.let { frameUrl ->
                val json = app.get(frameUrl).text.substringAfter("Playerjs(").substringBefore(");")
                val video = """file:"([^"]+)""".toRegex().find(json)?.groupValues?.get(1)

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
                """subtitle:"([^"]+)""".toRegex().find(json)?.groupValues?.get(1)?.split(",")?.forEach {
                    val match = Regex("""\[(\w+)](http\S+)""").find(it)
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
                    // Handle Jeniusplay source
                    extractJeniusplaySource(source, referer, subtitleCallback, callback)
                }
                !source.contains("youtube") -> {
                    loadExtractor(source, "$referer/", subtitleCallback, callback)
                }
            }
        }
    }

    private suspend fun handleUpCloudSource(
        hash: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val scriptData = app.get("$vidsrcccAPI/api/source/$hash", referer = "$vidsrcccAPI/")
                .document.selectFirst("script:containsData(source =)")?.data()
            
            val iframe = Regex("source\\s*=\\s*\"([^\"]+)").find(scriptData ?: return)?.groupValues?.get(1)
            iframe?.let { 
                loadExtractor(it, "$vidsrcccAPI/", subtitleCallback, callback)
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
                    val subData = getAndUnpack(script.data())
                        .substringAfter("\"tracks\":[")
                        .substringBefore("],")
                    tryParseJson<List<Tracks>>("[$subData]")?.forEach { subtitle ->
                        subtitleCallback.invoke(
                            SubtitleFile(
                                getLanguage(subtitle.label ?: ""),
                                subtitle.file
                            )
                        )
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
        return java.net.URI(url).let { "${it.scheme}://${it.host}" }
    }
}

// Data classes for JSON parsing
data class VidsrcccServer(
    @JsonProperty("name") val name: String?,
    @JsonProperty("hash") val hash: String?,
)

data class VidsrcccResponse(
    @JsonProperty("data") val data: List<VidsrcccServer>?,
)

data class VidsrcccResult(
    @JsonProperty("data") val data: VidsrcccSources?,
)

data class VidsrcccSources(
    @JsonProperty("subtitles") val subtitles: List<VidsrcccSubtitles>?,
    @JsonProperty("source") val source: String?,
)

data class VidsrcccSubtitles(
    @JsonProperty("label") val label: String?,
    @JsonProperty("file") val file: String?,
)

data class ResponseHash(
    @JsonProperty("embed_url") val embed_url: String,
    @JsonProperty("key") val key: String?,
)

data class JeniusplayResponse(
    @JsonProperty("videoSource") val videoSource: String,
)

data class Tracks(
    @JsonProperty("kind") val kind: String?,
    @JsonProperty("file") val file: String,
    @JsonProperty("label") val label: String?,
)
