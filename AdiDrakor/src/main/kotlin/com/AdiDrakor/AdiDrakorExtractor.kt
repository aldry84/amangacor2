package com.AdiDrakor

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.nicehttp.RequestBodyTypes
import org.jsoup.Jsoup
import com.AdiDrakor.AdiDrakor.LinkData

object AdiDrakorExtractor {

    // --- API CONSTANTS ---
    private const val gomoviesAPI = "https://gomovies-online.cam"
    private const val idlixAPI = "https://tv6.idlixku.com"
    private const val vidsrcccAPI = "https://vidsrc.cc"
    private const val vidSrcAPI = "https://vidsrc.net"
    private const val vidlinkAPI = "https://vidlink.pro"
    private const val vidfastAPI = "https://vidfast.pro"
    private const val wyzieAPI = "https://sub.wyzie.ru"
    private const val vixsrcAPI = "https://vixsrc.to"
    private const val vidsrccxAPI = "https://vidsrc.cx"
    private const val superembedAPI = "https://multiembed.mov"
    private const val vidrockAPI = "https://vidrock.net"
    private const val mappleAPI = "https://mapple.uk"
    private const val jeniusMainUrl = "https://jeniusplay.com"

    // --- FUNGSI UTAMA (PARALLEL & PRIORITIZED) ---
    suspend fun invokeAllExtractors(
        res: LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Fix error toJson: Gunakan extension function res.toJson()
        // Parsing dummy untuk memastikan data valid (opsional)
        try { AppUtils.parseJson<LinkData>(res.toJson()) } catch (e: Exception) { }

        // DAFTAR TUGAS EXTRACTOR
        // PENTING: invokeIdlix (sumber Jeniusplay) ditaruh PALING ATAS
        val tasks = listOf(
            // [PRIORITAS 1] IDLIX -> JENIUSPLAY
            suspend { invokeIdlix(res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            
            // [PRIORITAS 2] VIDSRCCC & VIDSRC (Stabil)
            suspend { invokeVidsrccc(res.id, res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            suspend { invokeVidsrc(res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            
            // [PRIORITAS 3] SUMBER LAINNYA
            suspend { invokeVidrock(res.id, res.season, res.episode, subtitleCallback, callback) },
            suspend { invokeVidlink(res.id, res.season, res.episode, callback) },
            suspend { invokeVixsrc(res.id, res.season, res.episode, callback) },
            suspend { invokeSuperembed(res.id, res.season, res.episode, subtitleCallback, callback) },
            suspend { invokeWyzie(res.id, res.season, res.episode, subtitleCallback) },
            suspend { invokeVidsrccx(res.id, res.season, res.episode, callback) },
            suspend { invokeWatchsomuch(res.imdbId, res.season, res.episode, subtitleCallback) },
            suspend { invokeMapple(res.id, res.season, res.episode, subtitleCallback, callback) },
            suspend { invokeVidfast(res.id, res.season, res.episode, subtitleCallback, callback) }
        )

        // JALANKAN SECARA PARALEL (CEPAT)
        // Menggunakan amap agar semua jalan berbarengan, tapi Idlix dimulai duluan
        tasks.amap { 
            try { it.invoke() } catch (e: Exception) { e.printStackTrace() } 
        }
    }

    // --- 1. JENIUSPLAY EXTRACTOR (Target Utama) ---
    //
    suspend fun invokeJeniusplay(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        try {
            val hash = url.split("/").last().substringAfter("data=")
            val m3uLink = app.post(
                url = "$jeniusMainUrl/player/index.php?data=$hash&do=getVideo",
                data = mapOf("hash" to hash, "r" to "$referer"),
                referer = url,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).parsed<ResponseSource>().videoSource

            // Kita beri nama "⭐️ Jeniusplay" agar terlihat spesial/diatas
            callback.invoke(newExtractorLink("Jeniusplay", "⭐️ Jeniusplay", m3uLink, ExtractorLinkType.M3U8) { 
                this.referer = url 
                this.quality = Qualities.P1080.value // Prioritaskan kualitas tinggi
            })

            // Subtitle Jeniusplay
            val scriptData = app.get(url, referer = "$jeniusMainUrl/").document.select("script").firstOrNull { 
                it.data().contains("eval(function(p,a,c,k,e,d)") 
            }?.data()

            if (scriptData != null) {
                val unpacked = getAndUnpack(scriptData)
                val subData = unpacked.substringAfter("\"tracks\":[").substringBefore("],")
                tryParseJson<List<JeniusTracks>>("[$subData]")?.forEach { subtitle ->
                    subtitleCallback.invoke(SubtitleFile(subtitle.label ?: "Indonesian", subtitle.file))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- 2. IDLIX (Parent dari Jeniusplay) ---
    //
    suspend fun invokeIdlix(title: String? = null, year: Int? = null, season: Int? = null, episode: Int? = null, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val fixTitle = title?.createSlug()
        val url = if (season == null) "$idlixAPI/movie/$fixTitle-$year" else "$idlixAPI/episode/$fixTitle-season-$season-episode-$episode"
        invokeWpmovies("Idlix", url, subtitleCallback, callback, encrypt = true)
    }

    private suspend fun invokeWpmovies(name: String? = null, url: String? = null, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit, fixIframe: Boolean = false, encrypt: Boolean = false) {
        val res = app.get(url ?: return)
        val referer = AdiDrakorUtils.getBaseUrl(res.url)
        
        res.document.select("ul#playeroptionsul > li").forEach { li ->
            val id = li.attr("data-post")
            val nume = li.attr("data-nume")
            val type = li.attr("data-type")
            
            val json = app.post(
                url = "$referer/wp-admin/admin-ajax.php",
                data = mapOf("action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type),
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                referer = url
            ).text
            
            val source = tryParseJson<ResponseHash>(json)?.let {
                if (encrypt) {
                    val meta = tryParseJson<Map<String, String>>(it.embed_url)?.get("m") ?: return@let null
                    val key = AdiDrakorUtils.generateWpKey(it.key ?: return@let null, meta)
                    AesHelper.cryptoAESHandler(it.embed_url, key.toByteArray(), false)?.replace("\"", "")?.replace("\\", "")
                } else it.embed_url
            } ?: return@forEach

            // LOGIKA PRIORITAS JENIUSPLAY
            if (source.startsWith("https://jeniusplay.com")) {
                invokeJeniusplay(source, "$referer/", subtitleCallback, callback)
            } else if (!source.contains("youtube")) {
                loadExtractor(source, "$referer/", subtitleCallback, callback)
            }
        }
    }

    // --- 3. VIDSRCCC ---
    suspend fun invokeVidsrccc(tmdbId: Int?, imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val url = if (season == null) "$vidsrcccAPI/v2/embed/movie/$tmdbId" else "$vidsrcccAPI/v2/embed/tv/$tmdbId/$season/$episode"
        val script = app.get(url).document.selectFirst("script:containsData(userId)")?.data() ?: return
        val userId = script.substringAfter("userId = \"").substringBefore("\";")
        val v = script.substringAfter("v = \"").substringBefore("\";")
        val vrf = AdiDrakorUtils.VidsrcHelper.encryptAesCbc("$tmdbId", "secret_$userId")
        
        val serverUrl = if(season==null) "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=movie&v=$v&vrf=$vrf" else "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=tv&v=$v&vrf=$vrf&season=$season&episode=$episode"
        
        app.get(serverUrl).parsedSafe<VidsrcccResponse>()?.data?.forEach { 
            if(it.name == "VidPlay") {
                 val src = app.get("$vidsrcccAPI/api/source/${it.hash}").parsedSafe<VidsrcccResult>()?.data
                 if(src?.source != null) callback.invoke(newExtractorLink("VidPlay", "VidPlay", src.source, ExtractorLinkType.M3U8) { this.referer = "$vidsrcccAPI/" })
                 src?.subtitles?.forEach { sub -> subtitleCallback.invoke(newSubtitleFile(sub.label ?: "", sub.file ?: "")) }
            }
        }
    }

    // --- 4. VIDSRC ---
    suspend fun invokeVidsrc(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val url = if (season == null) "$vidSrcAPI/embed/movie?imdb=$imdbId" else "$vidSrcAPI/embed/tv?imdb=$imdbId&season=$season&episode=$episode"
        app.get(url).document.select(".serversList .server").forEach { 
            if(it.text().contains("CloudStream Pro")) {
                val hash = app.get("https://cloudnestra.com/rcp/${it.attr("data-hash")}").text.substringAfter("/prorcp/").substringBefore("'")
                val m3u = Regex("https:.*\\.m3u8").find(app.get("https://cloudnestra.com/prorcp/$hash").text)?.value
                if(m3u!=null) callback.invoke(newExtractorLink("Vidsrc", "Vidsrc", m3u, ExtractorLinkType.M3U8))
            }
        }
    }

    // --- 5. VIDROCK ---
    suspend fun invokeVidrock(tmdbId: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val type = if (season == null) "movie" else "tv"
        val url = "$vidrockAPI/$type/$tmdbId${if (type == "movie") "" else "/$season/$episode"}"
        val enc = AdiDrakorUtils.VidrockHelper.encrypt(tmdbId, type, season, episode)
        
        app.get("$vidrockAPI/api/$type/$enc", referer = url).parsedSafe<Map<String, Map<String, String>>>()?.forEach { (key, value) ->
            if (key == "source2") {
                 val json = app.get(value["url"] ?: return@forEach, referer = "$vidrockAPI/").text
                 tryParseJson<List<VidrockSource>>(json)?.reversed()?.forEach { 
                     callback.invoke(newExtractorLink("Vidrock", "Vidrock [S2]", it.url ?: "", INFER_TYPE) { this.headers = mapOf("Range" to "bytes=0-", "Referer" to "$vidrockAPI/") })
                 }
            } else {
                callback.invoke(newExtractorLink("Vidrock", "Vidrock [$key]", value["url"] ?: "", ExtractorLinkType.M3U8) { this.referer = "$vidrockAPI/"; this.headers = mapOf("Origin" to vidrockAPI) })
            }
        }
    }
    
    // --- 6. OTHER EXTRACTORS ---
    suspend fun invokeVidlink(tmdbId: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) "$vidlinkAPI/$type/$tmdbId" else "$vidlinkAPI/$type/$tmdbId/$season/$episode"
        val link = app.get(url).parsedSafe<VidlinkSources>()?.stream?.playlist
        if(link != null) callback.invoke(newExtractorLink("Vidlink", "Vidlink", link, ExtractorLinkType.M3U8) { this.referer = "$vidlinkAPI/" })
    }

    suspend fun invokeVixsrc(tmdbId: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) "$vixsrcAPI/$type/$tmdbId" else "$vixsrcAPI/$type/$tmdbId/$season/$episode"
        val res = app.get(url).document.selectFirst("script:containsData(window.masterPlaylist)")?.data() ?: return
        val video = Regex("""'token':\s*'(\w+)'[\S\s]+'expires':\s*'(\w+)'[\S\s]+url:\s*'(\S+)'""").find(res)?.destructured?.let { (t, e, p) -> "$p?token=$t&expires=$e&h=1&lang=en" }
        if(video!=null) callback.invoke(newExtractorLink("Vixsrc", "Vixsrc", video, ExtractorLinkType.M3U8) { this.referer = url })
    }
    
    suspend fun invokeWatchsomuch(imdbId: String?, season: Int?, episode: Int?, cb: (SubtitleFile) -> Unit) {}
    suspend fun invokeVidfast(tmdbId: Int?, season: Int?, episode: Int?, cb: (SubtitleFile) -> Unit, call: (ExtractorLink) -> Unit) {}
    suspend fun invokeMapple(tmdbId: Int?, season: Int?, episode: Int?, cb: (SubtitleFile) -> Unit, call: (ExtractorLink) -> Unit) {}
    suspend fun invokeWyzie(tmdbId: Int?, season: Int?, episode: Int?, cb: (SubtitleFile) -> Unit) {
        val url = if(season==null) "$wyzieAPI/search?id=$tmdbId" else "$wyzieAPI/search?id=$tmdbId&season=$season&episode=$episode"
        app.get(url).parsedSafe<List<WyzieSubtitle>>()?.forEach { cb.invoke(newSubtitleFile(it.display?:"", it.url?:"")) }
    }
    suspend fun invokeVidsrccx(tmdbId: Int?, season: Int?, episode: Int?, call: (ExtractorLink) -> Unit) {}
    suspend fun invokeSuperembed(tmdbId: Int?, season: Int?, episode: Int?, cb: (SubtitleFile) -> Unit, call: (ExtractorLink) -> Unit) {}
}
