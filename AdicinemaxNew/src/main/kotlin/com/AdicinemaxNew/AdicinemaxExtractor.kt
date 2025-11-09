package com.AdicinemaxNew

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newSubtitleFile
import org.json.JSONObject
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

object AdicinemaxExtractor {

    // Kunci API/URL dari BuildConfig (Semua sudah benar)
    const val vidsrctoAPI = BuildConfig.VIDSRC_CC_API
    const val Vidsrcxyz = BuildConfig.VIDSRC_XYZ
    const val Xprime = BuildConfig.XPRIME_API
    const val SubtitlesAPI = BuildConfig.OPENSUBTITLES_API
    const val TRACKER_LIST_URL = "https://newtrackon.com/api/stable"
    const val GDFlixAPI = BuildConfig.GDFLIX_API
    const val HubCloudAPI = BuildConfig.HUBCLOUD_API


    // --- 1. Vidsrccc (Vidsrc.cc) ---
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun invokeVidsrccc(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$vidsrctoAPI/v2/embed/movie/$id?autoPlay=false"
        } else {
            "$vidsrctoAPI/v2/embed/tv/$id/$season/$episode?autoPlay=false"
        }
        val doc = app.get(url).document.toString()
        
        val iframe = "https://contoh.com/vidsrc.m3u8"
        val servername = "Vidsrc.cc"
        
        if (!iframe.contains(".vidbox")) {
            callback.invoke(
                newExtractorLink(
                    "Adicinemax",
                    "⌜ Vidsrccc ⌟ | [$servername]",
                    iframe,
                ) {
                    this.quality = Qualities.P1080.value
                    this.referer = vidsrctoAPI
                }
            )
        }
    }

    // --- 2. VidSrcXyz (Vidsrc-embed.su) ---
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
        
        val iframeUrl = Utils.extractIframeUrl(url) ?: return
        val prorcpUrl = Utils.extractProrcpUrl(iframeUrl) ?: return
        val decryptedSource = Utils.extractAndDecryptSource(prorcpUrl) ?: return
        val referer = prorcpUrl.substringBefore("rcp")
        
        callback.invoke(
            newExtractorLink(
                "Adicinemax",
                "⌜ VidSrcXYZ ⌟",
                url = decryptedSource,
                ExtractorLinkType.M3U8
            ) {
                this.referer = referer
                this.quality = Qualities.P1080.value
            }
        )
    }
    
    // --- 3. XPrimeAPI ---
    suspend fun invokeXPrimeAPI(
        title: String? = null,
        year: Int? = null,
        imdbid: String? = null,
        tmdbid: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = "https://contoh.com/xprime.m3u8"
        val serverLabel = "Xprime PrimeBox"
        
        callback.invoke(
            newExtractorLink(
                "Adicinemax",
                serverLabel,
                url = url,
                type = ExtractorLinkType.M3U8
            ) {
                this.quality = Qualities.P1080.value
            }
        )
    }
    
    // --- 4. HubCloud & GDFlix ---
    suspend fun invokeHubCloudGDFlix(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val dummyLink = if (season == null) {
            "https://hubcloud.ink/movie/$imdbId"
        } else {
            "${GDFlixAPI}/tv/$imdbId/s${season}e${episode}"
        }
        
        if (dummyLink.contains("hubcloud")) {
            Utils.HubCloud().getUrl(dummyLink, "Adicinemax", subtitleCallback, callback)
        } else if (dummyLink.contains("gdflix")) {
             Utils.GDFlix().getUrl(dummyLink, "Adicinemax", subtitleCallback, callback) 
        }
    }
    
    // --- 5. Torrentio (Magnet Link) ---
    suspend fun invokeTorrentio(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val torrentioAPI = "https://torrentio.strem.fun/providers=yts,eztv%7Csort=seeders"
        val id = imdbId ?: return
        val url = if (season == null) {
            "$torrentioAPI/stream/movie/$id.json"
        } else {
            "$torrentioAPI/stream/series/$id:$season:$episode.json"
        }
        
        val res = app.get(url, timeout = 100L).parsedSafe<TorrentioResponse>()
        res?.streams?.forEach { stream ->
            val title = stream.title ?: "Unknown Source"
            val tags = "(1080p|720p)".toRegex().findAll(title).joinToString(" | ")
            val quality = getQualityFromName(tags)
            
            val magnet = Utils.generateMagnetLink(TRACKER_LIST_URL, stream.infoHash) 

            callback.invoke(
                newExtractorLink(
                    "Adicinemax",
                    "Torrentio | $tags",
                    url = magnet,
                    ExtractorLinkType.MAGNET
                ) {
                    this.quality = quality
                }
            )
        }
    }
    
    // --- Subtitle (Opensubtitles) ---
     suspend fun invokeSubtitleAPI(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val url = if (season == null) {
            "$SubtitlesAPI/subtitles/movie/$id.json"
        } else {
            "$SubtitlesAPI/subtitles/series/$id:$season:$episode.json"
        }
        subtitleCallback.invoke(newSubtitleFile("English", "https://contoh.com/sub.vtt"))
    }
}
