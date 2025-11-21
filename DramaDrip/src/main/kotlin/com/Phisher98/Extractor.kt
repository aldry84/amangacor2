package com.Phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup

// --- Extractor Logic Object ---
object DramaDripExtractor {
    // API Constants
    const val gomoviesAPI = "https://gomovies-online.cam"
    const val idlixAPI = "https://tv6.idlixku.com"
    const val vidsrcccAPI = "https://vidsrc.cc"
    const val vidSrcAPI = "https://vidsrc.net"
    const val xprimeAPI = "https://backend.xprime.tv"
    const val watchSomuchAPI = "https://watchsomuch.tv"
    const val mappleAPI = "https://mapple.uk"
    const val vidlinkAPI = "https://vidlink.pro"
    const val vidfastAPI = "https://vidfast.pro"
    const val wyzieAPI = "https://sub.wyzie.ru"
    const val vixsrcAPI = "https://vixsrc.to"
    const val vidsrccxAPI = "https://vidsrc.cx"
    const val superembedAPI = "https://multiembed.mov"
    const val vidrockAPI = "https://vidrock.net"
    const val vidrockSubAPI = "https://sub.vdrk.site"

    var gomoviesCookies: Map<String, String>? = null

    suspend fun invokeGomovies(title: String?, year: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        invokeGpress(title, year, season, episode, callback, gomoviesAPI, "Gomovies", base64Decode("X3NtUWFtQlFzRVRi"), base64Decode("X3NCV2NxYlRCTWFU"))
    }

    private suspend fun invokeGpress(title: String?, year: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit, api: String, name: String, mediaSelector: String, episodeSelector: String) {
        fun String.decrypt(key: String): List<GpressSources>? = tryParseJson<List<GpressSources>>(base64Decode(this).xorDecrypt(key))

        val slug = getEpisodeSlug(season, episode)
        val query = if (season == null) title else "$title Season $season"
        var cookies = mapOf("_identitygomovies7" to "5a436499900c81529e3740fd01c275b29d7e2fdbded7d760806877edb1f473e0a%3A2%3A%7Bi%3A0%3Bs%3A18%3A%22_identitygomovies7%22%3Bi%3A1%3Bs%3A52%3A%22%5B2800906%2C%22L2aGGTL9aqxksKR0pLvL66TunKNe1xXb%22%2C2592000%5D%22%3B%7D")
        
        var res = app.get("$api/search/$query", cookies = cookies)
        cookies = gomoviesCookies ?: res.cookies.filter { it.key == "advanced-frontendgomovies7" }.also { gomoviesCookies = it }
        val doc = res.document
        val media = doc.select("div.$mediaSelector").map { Triple(it.attr("data-filmName"), it.attr("data-year"), it.select("a").attr("href")) }
            .find { if (season == null) (it.first.equals(title, true) || it.first.equals("$title ($year)", true)) && it.second == "$year" else it.first.equals("$title - Season $season", true) } 
            ?: doc.select("div.$mediaSelector").map { Triple(it.attr("data-filmName"), it.attr("data-year"), it.select("a").attr("href")) }.find { it.first.contains("$title", true) && it.second == "$year" } 
            ?: return

        val iframe = if (season == null) media.third else app.get(fixUrl(media.third, api)).document.selectFirst("div#$episodeSelector a:contains(Episode ${slug.second})")?.attr("href") ?: return
        
        res = app.get(fixUrl(iframe, api), cookies = cookies)
        val url = res.document.select("meta[property=og:url]").attr("content")
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        val qualities = intArrayOf(2160, 1440, 1080, 720, 480, 360)
        val (serverId, episodeId) = if (season == null) url.substringAfterLast("/") to "0" else url.substringBeforeLast("/").substringAfterLast("/") to url.substringAfterLast("/").substringBefore("-")
        
        val serverRes = app.get("$api/user/servers/$serverId?ep=$episodeId", cookies = cookies, headers = headers)
        val key = """key\s*="\s*(\d+)"""".toRegex().find(getAndUnpack(serverRes.text))?.groupValues?.get(1) ?: return
        
        serverRes.document.select("ul li").amap { el ->
            val server = el.attr("data-value")
            val encryptedData = app.get("$url?server=$server&_=${APIHolder.unixTimeMS}", cookies = cookies, referer = url, headers = headers).text
            encryptedData.decrypt(key)?.forEach { video ->
                qualities.filter { it <= video.max.toInt() }.forEach {
                    callback.invoke(newExtractorLink(name, name, video.src.split("360").joinToString(it.toString()), ExtractorLinkType.VIDEO) { this.referer = "$api/"; this.quality = it })
                }
            }
        }
    }

    suspend fun invokeIdlix(title: String?, year: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val fixTitle = title?.createSlug()
        val url = if (season == null) "$idlixAPI/movie/$fixTitle-$year" else "$idlixAPI/episode/$fixTitle-season-$season-episode-$episode"
        invokeWpmovies("Idlix", url, subtitleCallback, callback, encrypt = true)
    }

    private suspend fun invokeWpmovies(name: String, url: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit, encrypt: Boolean = false) {
        val res = app.get(url)
        val referer = getBaseUrl(res.url)
        res.document.select("ul#playeroptionsul > li").amap { li ->
            val json = app.post("$referer/wp-admin/admin-ajax.php", data = mapOf("action" to "doo_player_ajax", "post" to li.attr("data-post"), "nume" to li.attr("data-nume"), "type" to li.attr("data-type")), headers = mapOf("X-Requested-With" to "XMLHttpRequest"), referer = url).text
            val source = tryParseJson<ResponseHash>(json)?.let {
                if (encrypt) {
                    val meta = tryParseJson<Map<String, String>>(it.embed_url)?.get("m") ?: return@let null
                    AesHelper.cryptoAESHandler(it.embed_url, generateWpKey(it.key ?: return@let null, meta).toByteArray(), false)?.fixUrlBloat()
                } else it.embed_url
            } ?: return@amap

            if (source.startsWith("https://jeniusplay.com")) Jeniusplay2().getUrl(source, "$referer/", subtitleCallback, callback)
            else if (!source.contains("youtube")) loadExtractor(source, "$referer/", subtitleCallback, callback)
        }
    }

    suspend fun invokeVidsrccc(tmdbId: String?, imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val url = if (season == null) "$vidsrcccAPI/v2/embed/movie/$tmdbId" else "$vidsrcccAPI/v2/embed/tv/$tmdbId/$season/$episode"
        val script = app.get(url).document.selectFirst("script:containsData(userId)")?.data() ?: return
        val userId = script.substringAfter("userId = \"").substringBefore("\";")
        val v = script.substringAfter("v = \"").substringBefore("\";")
        val vrf = VidsrcHelper.encryptAesCbc("$tmdbId", "secret_$userId")
        val serverUrl = if (season == null) "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=movie&v=$v&vrf=$vrf&imdbId=$imdbId" else "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=tv&v=$v&vrf=$vrf&imdbId=$imdbId&season=$season&episode=$episode"

        app.get(serverUrl).parsedSafe<VidsrcccResponse>()?.data?.amap {
            val sources = app.get("$vidsrcccAPI/api/source/${it.hash}").parsedSafe<VidsrcccResult>()?.data ?: return@amap
            if (it.name == "VidPlay") {
                callback.invoke(newExtractorLink("VidPlay", "VidPlay", sources.source ?: return@amap, ExtractorLinkType.M3U8) { this.referer = "$vidsrcccAPI/" })
                sources.subtitles?.map { sub -> subtitleCallback.invoke(newSubtitleFile(sub.label ?: "", sub.file ?: "")) }
            }
        }
    }
    
    suspend fun invokeVidsrc(imdbId: String?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val url = if (season == null) "$vidSrcAPI/embed/movie?imdb=$imdbId" else "$vidSrcAPI/embed/tv?imdb=$imdbId&season=$season&episode=$episode"
        app.get(url).document.select(".serversList .server").amap { server ->
            if (server.text().equals("CloudStream Pro", true)) {
                val hash = app.get("https://cloudnestra.com/rcp/${server.attr("data-hash")}").text.substringAfter("/prorcp/").substringBefore("'")
                val m3u8 = Regex("https:.*\\.m3u8").find(app.get("https://cloudnestra.com/prorcp/$hash").text)?.value
                callback.invoke(newExtractorLink("Vidsrc", "Vidsrc", m3u8 ?: return@amap, ExtractorLinkType.M3U8))
            }
        }
    }

    suspend fun invokeWyzie(tmdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit) {
        val url = if (season == null) "$wyzieAPI/search?id=$tmdbId" else "$wyzieAPI/search?id=$tmdbId&season=$season&episode=$episode"
        tryParseJson<ArrayList<WyzieSubtitle>>(app.get(url).text)?.map { 
             subtitleCallback.invoke(newSubtitleFile(getLanguageNameFromCode(it.display) ?: it.display ?: "", it.url ?: return@map)) 
        }
    }

    suspend fun invokeVidlink(tmdbId: String?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) "$vidlinkAPI/$type/$tmdbId" else "$vidlinkAPI/$type/$tmdbId/$season/$episode"
        val link = app.get(url, interceptor = WebViewResolver(Regex("""$vidlinkAPI/api/b/$type/A{32}"""))).parsedSafe<VidlinkSources>()?.stream?.playlist
        callback.invoke(newExtractorLink("Vidlink", "Vidlink", link ?: return, ExtractorLinkType.M3U8) { this.referer = "$vidlinkAPI/" })
    }

    suspend fun invokeVidfast(tmdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val module = "hezushon/1000076901076321/0b0ce221/cfe60245-021f-5d4d-bacb-0d469f83378f/uva/jeditawev/b0535941d898ebdb81f575b2cfd123f5d18c6464/y/APA91zAOxU2psY2_BvBqEmmjG6QvCoLjgoaI-xuoLxBYghvzgKAu-HtHNeQmwxNbHNpoVnCuX10eEes1lnTcI2l_lQApUiwfx2pza36CZB34X7VY0OCyNXtlq-bGVCkLslfNksi1k3B667BJycQ67wxc1OnfCc5PDPrF0BA8aZRyMXZ3-2yxVGp"
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) "$vidfastAPI/$type/$tmdbId" else "$vidfastAPI/$type/$tmdbId/$season/$episode"
        val res = app.get(url, interceptor = WebViewResolver(Regex("""$vidfastAPI/$module/JEwECseLZdY"""))).text
        tryParseJson<ArrayList<VidFastServers>>(res)?.filter { it.description?.contains("Original") == true }?.amapIndexed { idx, server ->
            val src = app.get("$vidfastAPI/$module/Sdoi/${server.data}", referer = "$vidfastAPI/").parsedSafe<VidFastSources>()
            callback.invoke(newExtractorLink("Vidfast", "Vidfast [${server.name}]", src?.url ?: return@amapIndexed, INFER_TYPE))
            if (idx == 1) src.tracks?.map { subtitleCallback.invoke(newSubtitleFile(it.label ?: "", it.file ?: "")) }
        }
    }

    suspend fun invokeVidrock(tmdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val type = if (season == null) "movie" else "tv"
        val url = "$vidrockAPI/$type/$tmdbId${if (type == "movie") "" else "/$season/$episode"}"
        val encryptData = VidrockHelper.encrypt(tmdbId?.toIntOrNull(), type, season, episode)
        
        app.get("$vidrockAPI/api/$type/$encryptData", referer = url).parsedSafe<LinkedHashMap<String, HashMap<String, String>>>()?.map { source ->
            if (source.key == "source2") {
                tryParseJson<ArrayList<VidrockSource>>(app.get(source.value["url"] ?: return@map, referer = "$vidrockAPI/").text)?.reversed()?.map {
                     callback.invoke(newExtractorLink("Vidrock", "Vidrock [Source2]", it.url ?: "", INFER_TYPE) { this.quality = it.resolution ?: Qualities.Unknown.value; this.headers = mapOf("Referer" to "$vidrockAPI/") })
                }
            } else callback.invoke(newExtractorLink("Vidrock", "Vidrock [${source.key}]", source.value["url"] ?: "", ExtractorLinkType.M3U8) { this.referer = "$vidrockAPI/"; this.headers = mapOf("Origin" to vidrockAPI) })
        }
        val subRes = app.get("$vidrockSubAPI/$type/$tmdbId${if (type == "movie") "" else "/$season/$episode"}").text
        tryParseJson<ArrayList<VidrockSubtitle>>(subRes)?.map { 
             subtitleCallback.invoke(newSubtitleFile(getLanguageNameFromCode(it.label?.replace(Regex("\\d|Hi"), "")?.trim()) ?: "", it.file ?: "")) 
        }
    }
}

// --- Classes Jeniusplay & Data ---
class Jeniusplay2 : ExtractorApi() {
    override val name = "Jeniusplay"
    override val mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val hash = url.split("/").last().substringAfter("data=")
        val m3u = app.post("$mainUrl/player/index.php?data=$hash&do=getVideo", data = mapOf("hash" to hash, "r" to "$referer"), referer = url, headers = mapOf("X-Requested-With" to "XMLHttpRequest")).parsed<ResponseSource>().videoSource
        callback.invoke(newExtractorLink(name, name, m3u, ExtractorLinkType.M3U8) { this.referer = url })
        app.get(url).document.select("script").map { if(it.data().contains("eval")) tryParseJson<List<Tracks>>("[${getAndUnpack(it.data()).substringAfter("tracks\":[").substringBefore("],")}]")?.map { s -> subtitleCallback.invoke(SubtitleFile(s.label ?: "", s.file)) } }
    }
    data class ResponseSource(@JsonProperty("videoSource") val videoSource: String)
    data class Tracks(@JsonProperty("file") val file: String, @JsonProperty("label") val label: String?)
}

data class GpressSources(@JsonProperty("src") val src: String, @JsonProperty("max") val max: String)
data class ResponseHash(@JsonProperty("embed_url") val embed_url: String, @JsonProperty("key") val key: String?)
data class VidsrcccResponse(@JsonProperty("data") val data: ArrayList<VidsrcccServer>?)
data class VidsrcccServer(@JsonProperty("name") val name: String?, @JsonProperty("hash") val hash: String?)
data class VidsrcccResult(@JsonProperty("data") val data: VidsrcccSources?)
data class VidsrcccSources(@JsonProperty("subtitles") val subtitles: ArrayList<VidsrcccSubtitles>?, @JsonProperty("source") val source: String?)
data class VidsrcccSubtitles(@JsonProperty("label") val label: String?, @JsonProperty("file") val file: String?)
data class WyzieSubtitle(@JsonProperty("display") val display: String?, @JsonProperty("url") val url: String?)
data class VidlinkSources(@JsonProperty("stream") val stream: VidlinkStream?)
data class VidlinkStream(@JsonProperty("playlist") val playlist: String?)
data class VidFastSources(@JsonProperty("url") val url: String?, @JsonProperty("tracks") val tracks: ArrayList<VidFastTracks>?)
data class VidFastTracks(@JsonProperty("file") val file: String?, @JsonProperty("label") val label: String?)
data class VidFastServers(@JsonProperty("name") val name: String?, @JsonProperty("description") val description: String?, @JsonProperty("data") val data: String?)
data class VidrockSource(@JsonProperty("resolution") val resolution: Int?, @JsonProperty("url") val url: String?)
data class VidrockSubtitle(@JsonProperty("label") val label: String?, @JsonProperty("file") val file: String?)
