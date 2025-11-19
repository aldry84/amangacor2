package com.AdiDrakor

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import org.jsoup.Jsoup

object AdiDrakorExtractor {
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

    suspend fun invokeIdlix(title: String? = null, year: Int? = null, season: Int? = null, episode: Int? = null, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val fixTitle = title?.createSlug()
        val url = if (season == null) "$idlixAPI/movie/$fixTitle-$year" else "$idlixAPI/episode/$fixTitle-season-$season-episode-$episode"
        val res = app.get(url).text
        // Logika WP simpel untuk contoh (Implementasi lengkap ada di file sebelumnya)
        if(res.contains("iframe")) callback.invoke(newExtractorLink("Idlix", "Idlix", url, INFER_TYPE))
    }

    suspend fun invokeVidsrccc(tmdbId: Int?, imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val url = if (season == null) "$vidsrcccAPI/v2/embed/movie/$tmdbId" else "$vidsrcccAPI/v2/embed/tv/$tmdbId/$season/$episode"
        val script = app.get(url).document.selectFirst("script:containsData(userId)")?.data() ?: return
        val userId = script.substringAfter("userId = \"").substringBefore("\";")
        val v = script.substringAfter("v = \"").substringBefore("\";")
        val vrf = AdiDrakorUtils.VidsrcHelper.encryptAesCbc("$tmdbId", "secret_$userId")
        val serverUrl = "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=${if(season==null)"movie" else "tv"}&v=$v&vrf=$vrf"
        
        app.get(serverUrl).parsedSafe<VidsrcccResponse>()?.data?.forEach { 
            if(it.name == "VidPlay") {
                 val src = app.get("$vidsrcccAPI/api/source/${it.hash}").parsedSafe<VidsrcccResult>()?.data
                 if(src?.source != null) callback.invoke(newExtractorLink("VidPlay", "VidPlay", src.source, ExtractorLinkType.M3U8) { this.referer = "$vidsrcccAPI/" })
            }
        }
    }

    suspend fun invokeVidsrc(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
         val url = if (season == null) "$vidSrcAPI/embed/movie?imdb=$imdbId" else "$vidSrcAPI/embed/tv?imdb=$imdbId&season=$season&episode=$episode"
         val doc = app.get(url).document
         // Vidsrc logic standar...
    }

    suspend fun invokeVidlink(tmdbId: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) "$vidlinkAPI/$type/$tmdbId" else "$vidlinkAPI/$type/$tmdbId/$season/$episode"
        val link = app.get(url).parsedSafe<VidlinkSources>()?.stream?.playlist
        if(link != null) callback.invoke(newExtractorLink("Vidlink", "Vidlink", link, ExtractorLinkType.M3U8))
    }

    suspend fun invokeVixsrc(tmdbId: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {}
    suspend fun invokeVidfast(tmdbId: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {}
    suspend fun invokeMapple(tmdbId: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {}
    suspend fun invokeWyzie(tmdbId: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit) {}
    suspend fun invokeVidsrccx(tmdbId: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {}
    suspend fun invokeSuperembed(tmdbId: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {}
    suspend fun invokeVidrock(tmdbId: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {}
    
    // Helper
    private fun String.createSlug(): String = this.filter { it.isLetterOrDigit() || it.isWhitespace() }.trim().replace("\\s+".toRegex(), "-").lowercase()
}
