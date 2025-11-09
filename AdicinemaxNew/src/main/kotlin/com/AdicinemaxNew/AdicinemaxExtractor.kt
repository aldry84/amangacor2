package com.AdicinemaxNew

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.AdicinemaxNew.Utils.generateVrfAES
import com.AdicinemaxNew.Utils.extractIframeUrl
import com.AdicinemaxNew.Utils.extractProrcpUrl
import com.AdicinemaxNew.Utils.extractAndDecryptSource
import com.AdicinemaxNew.Utils.getBaseUrl
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.M3u8Helper
import org.json.JSONObject
import com.lagradost.cloudstream3.base64Decode
import org.jsoup.Jsoup
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

object AdicinemaxExtractor {

    // Kunci API/URL yang diperlukan (Asumsi ada di BuildConfig atau di hardcode jika tidak sensitif)
    const val vidsrctoAPI = "https://vidsrc.cc" // dari StreamPlay
    const val Vidsrcxyz = "https://vidsrc-embed.su" // dari StreamPlay
    const val Xprime = "https://xprime.tv" // dari StreamPlay
    const val SubtitlesAPI = "https://opensubtitles-v3.strem.io" // dari StreamPlay
    const val TRACKER_LIST_URL="https://newtrackon.com/api/stable" // dari StreamPlayTorrent
    
    // Hardcode URL HubCloud/GDFlix yang stabil (dari analisis StreamPlay)
    const val GDFlixAPI = "https://new6.gdflix.dad"
    const val HubCloudAPI = "https://hubcloud.ink"

    // --- 1. Vidsrccc (Vidsrc.cc) ---
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun invokeVidsrccc(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        // Diambil dari StreamPlayExtractor.invokeVidsrccc
        val url = if (season == null) {
            "$vidsrctoAPI/v2/embed/movie/$id?autoPlay=false"
        } else {
            "$vidsrctoAPI/v2/embed/tv/$id/$season/$episode?autoPlay=false"
        }
        // ... (Logika parsing, generateVrfAES, dan fetching iframe)
        val doc = app.get(url).document.toString()
        // ... (asumsi logika ekstraksi hash dan API call berhasil)
        
        // Simulasikan hasil ekstraksi (Anda perlu mengimplementasikan ulang logic di StreamPlayExtractor.kt)
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
        // Diambil dari StreamPlayExtractor.invokeVidSrcXyz
        val url = if (season == null) {
            "$Vidsrcxyz/embed/movie?imdb=$id"
        } else {
            "$Vidsrcxyz/embed/tv?imdb=$id&season=$season&episode=$episode"
        }
        
        // ... (Logika extractIframeUrl, extractProrcpUrl, dan extractAndDecryptSource)
        val iframeUrl = extractIframeUrl(url) ?: return
        val prorcpUrl = extractProrcpUrl(iframeUrl) ?: return
        val decryptedSource = extractAndDecryptSource(prorcpUrl) ?: return
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
        // Diambil dari StreamPlayExtractor.invokeXPrimeAPI
        val backendAPI = "https://api-xprime.phisher.repl.co" // ASUMSI API proxy/backend
        
        // ... (Logika generateXTrSignature, request API, dan dekripsi JSON)
        
        // Simulasikan hasil
        val url = "https://contoh.com/xprime.m3u8"
        val serverLabel = "Xprime PrimeBox"
        
        callback.invoke(
            newExtractorLink(
                "Adicinemax",
                serverLabel,
                url = url,
                type = ExtractorLinkType.M3U8
            ) {
                this.headers = mapOf("Origin" to Xprime)
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
        // Asumsi ini dipanggil setelah scraper seperti MoviesDrive menemukan link ke GDFlix/HubCloud
        
        // Simulasi logika scraper (Anda perlu mengimplementasikan scrapernya)
        val dummyLink = if (season == null) {
            "https://hubcloud.ink/movie/$imdbId"
        } else {
            "${GDFlixAPI}/tv/$imdbId/s${season}e${episode}"
        }
        
        if (dummyLink.contains("hubcloud")) {
            HubCloud().getUrl(dummyLink, "Adicinemax", subtitleCallback, callback)
        } else if (dummyLink.contains("gdflix")) {
             // GDFlix membutuhkan referer, tapi kita hanya memanggil ekstraktornya
             GDFlix().getUrl(dummyLink, "Adicinemax", subtitleCallback, callback) 
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
            // Simulasi parsing title dan kualitas
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
        // ... (Logika request dan parsing subtilte)
        
        // Simulasikan hasil (Anda perlu mengimplementasikan ulang logic di StreamPlayExtractor.kt)
        val dummySub = listOf(newSubtitleFile("English", "https://contoh.com/sub.vtt"))
        dummySub.forEach(subtitleCallback)
    }
}

// ASUMSI: Extractor GDFlix, HubCloud, dan kelas data TorrentioResponse sudah ada di Utils.kt atau terdefinisi di sini.
