package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup

object SoraExtractor : SoraStream() {

    // ... [fungsi lainnya tetap sama] ...

    suspend fun invokeVidsrccc(
        tmdbId: Int?,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {

        val url = if (season == null) {
            "$vidsrcccAPI/v2/embed/movie/$tmdbId"
        } else {
            "$vidsrcccAPI/v2/embed/tv/$tmdbId/$season/$episode"
        }

        val script =
            app.get(url).document.selectFirst("script:containsData(userId)")?.data() ?: return

        val userId = script.substringAfter("userId = \"").substringBefore("\";")
        val v = script.substringAfter("v = \"").substringBefore("\";")

        val vrf = VidsrcHelper.encryptAesCbc("$tmdbId", "secret_$userId")

        val serverUrl = if (season == null) {
            "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=movie&v=$v&vrf=$vrf&imdbId=$imdbId"
        } else {
            "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=tv&v=$v&vrf=$vrf&imdbId=$imdbId&season=$season&episode=$episode"
        }

        app.get(serverUrl).parsedSafe<VidsrcccResponse>()?.data?.amap {
            val sources =
                app.get("$vidsrcccAPI/api/source/${it.hash}").parsedSafe<VidsrcccResult>()?.data
                    ?: return@amap

            when {
                it.name.equals("VidPlay") -> {

                    callback.invoke(
                        newExtractorLink(
                            "VidPlay",
                            "VidPlay",
                            sources.source ?: return@amap,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$vidsrcccAPI/"
                        }
                    )

                    sources.subtitles?.map {
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                it.label ?: return@map,
                                it.file ?: return@map
                            )
                        )
                    }
                }

                it.name.equals("UpCloud") -> {
                    val scriptData = app.get(
                        sources.source ?: return@amap,
                        referer = "$vidsrcccAPI/"
                    ).document.selectFirst("script:containsData(source =)")?.data()
                    val iframe = Regex("source\\s*=\\s*\"([^\"]+)").find(
                        scriptData ?: return@amap
                    )?.groupValues?.get(1)?.fixUrlBloat()

                    val iframeRes =
                        app.get(iframe ?: return@amap, referer = "https://lucky.vidbox.site/").text

                    val id = iframe.substringAfterLast("/").substringBefore("?")
                    val key = Regex("\\w{48}").find(iframeRes)?.groupValues?.get(0) ?: return@amap

                    app.get(
                        "${iframe.substringBeforeLast("/")}/getSources?id=$id&_k=$key",
                        headers = mapOf(
                            "X-Requested-With" to "XMLHttpRequest",
                        ),
                        referer = iframe
                    ).parsedSafe<UpcloudResult>()?.sources?.amap file@{ source ->
                        callback.invoke(
                            newExtractorLink(
                                "UpCloud",
                                "UpCloud",
                                source.file ?: return@file,
                                ExtractorLinkType.M3U8
                            ) {
                                this.referer = "$vidsrcccAPI/"
                            }
                        )
                    }

                }

                else -> {
                    return@amap
                }
            }
        }
    }

    suspend fun invokeXprime(
        tmdbId: Int?,
        title: String? = null,
        year: Int? = null,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val servers = listOf("rage", "primebox")
        val serverName = servers.map { it.capitalize() }
        val referer = "https://xprime.tv/"
        runAllAsync(
            {
                val url = if (season == null) {
                    "$xprimeAPI/${servers.first()}?id=$tmdbId"
                } else {
                    "$xprimeAPI/${servers.first()}?id=$tmdbId&season=$season&episode=$episode"
                }

                val source = app.get(url).parsedSafe<RageSources>()?.url

                callback.invoke(
                    newExtractorLink(
                        serverName.first(),
                        serverName.first(),
                        source ?: return@runAllAsync,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = referer
                    }
                )
            },
            {
                val url = if (season == null) {
                    "$xprimeAPI/${servers.last()}?name=$title&fallback_year=$year"
                } else {
                    "$xprimeAPI/${servers.last()}?name=$title&fallback_year=$year&season=$season&episode=$episode"
                }

                val sources = app.get(url).parsedSafe<PrimeboxSources>()

                sources?.streams?.map { source ->
                    callback.invoke(
                        newExtractorLink(
                            serverName.last(),
                            serverName.last(),
                            source.value,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = referer
                            this.quality = getQualityFromName(source.key)
                        }
                    )
                }

                sources?.subtitles?.map { subtitle ->
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            subtitle.label ?: "",
                            subtitle.file ?: return@map
                        )
                    )
                }
            }
        )
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
                    fixUrl(sub.url ?: return@map, watchSomuchAPI)
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
                    subtitle.url ?: return@map
                )
            )
        }
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
            app.post(playUrl, requestBody = "captcha_id=TEduRVR6NmZ3Sk5Jc3JpZEJCSlhTM25GREs2RCswK0VQN2ZsclI5KzNKL2cyV3dIaFEwZzNRRHvVwMzdqVmoxV0t2QlBrNjNTY04wY2NSaHlWYS9Jc09nb25wZTV2YmxDSXNRZVNuQUpuRW5nbkF2dURsQUdJWVpwOWxUZzU5Tnh0NXllQjdYUG83Y0ZVaG1XRGtPOTBudnZvN0RFK0wxdGZvYXpFKzVNM2U1a2lBMG40REJmQ042SA%3D%3D&captcha_answer%5B%5D=8yhbjraxqf3o&captcha_answer%5B%5D=10zxn5vi746w&captcha_answer%5B%5D=gxfpe17tdwub".toRequestBody(RequestBodyTypes.TEXT.toMediaTypeOrNull())
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

    suspend fun invokeVidrock(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        subAPI: String = "https://sub.vdrk.site"
    ) {

        val type = if (season == null) "movie" else "tv"
        val url = "$vidrockAPI/$type/$tmdbId${if(type == "movie") "" else "/$season/$episode"}"
        val encryptData = VidrockHelper.encrypt(tmdbId, type, season, episode)

        app.get("$vidrockAPI/api/$type/$encryptData", referer = url).parsedSafe<LinkedHashMap<String,HashMap<String,String>>>()
            ?.map { source ->
                if(source.key == "source2") {
                    val json = app.get(source.value["url"] ?: return@map, referer = "${vidrockAPI}/").text
                    tryParseJson<ArrayList<VidrockSource>>(json)?.reversed()?.map mirror@{
                        callback.invoke(
                            newExtractorLink(
                                "Vidrock",
                                "Vidrock [Source2]",
                                it.url ?: return@mirror,
                                INFER_TYPE
                            ) {
                                this.quality = it.resolution ?: Qualities.Unknown.value
                                this.headers = mapOf(
                                    "Range" to "bytes=0-",
                                    "Referer" to "${vidrockAPI}/"
                                )
                            }
                        )
                    }
                } else {
                    callback.invoke(
                        newExtractorLink(
                            "Vidrock",
                            "Vidrock [${source.key.capitalize()}]",
                            source.value["url"] ?: return@map,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = "${vidrockAPI}/"
                            this.headers = mapOf(
                                "Origin" to vidrockAPI
                            )
                        }
                    )
                }
            }

        val subUrl = "$subAPI/$type/$tmdbId${if(type == "movie") "" else "/$season/$episode"}"
        val res = app.get(subUrl).text
        tryParseJson<ArrayList<VidrockSubtitle>>(res)?.map { subtitle ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    subtitle.label?.replace(Regex("\\d"), "")?.replace(Regex("\\s+Hi"), "")?.trim() ?: return@map,
                    subtitle.file ?: return@map
                )
            )
        }
    }
}
