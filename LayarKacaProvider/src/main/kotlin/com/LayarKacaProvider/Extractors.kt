package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getPacked
import org.json.JSONObject
import java.net.URI

// ==========================================
// 1. KODE ASLI (HOWNETWORK & CADANGAN)
// ==========================================
class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

open class Hownetwork : ExtractorApi() {
    override val name = "Hownetwork"
    override val mainUrl = "https://stream.hownetwork.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfter("id=").substringBefore("&")
        val response = app.post(
                "$mainUrl/api.php?id=$id",
                data = mapOf("r" to (referer ?: ""), "d" to mainUrl),
                referer = url,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).text

        try {
            val json = JSONObject(response)
            val file = json.optString("file")
            
            if (file.isNotBlank() && file != "null") {
                val properReferer = url 
                val headers = mapOf(
                    "Origin" to mainUrl,
                    "Referer" to properReferer,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )

                val playlist = M3u8Helper.generateM3u8(this.name, file, properReferer, headers = headers)
                if (playlist.isNotEmpty()) {
                    playlist.forEach(callback)
                } else {
                    // --- FIX BUILD ERROR DI SINI ---
                    // Menggunakan Constructor langsung dengan named arguments agar tidak tertukar
                    callback(
                        ExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = file,
                            referer = properReferer,
                            quality = Qualities.Unknown.value,
                            type = INFER_TYPE,
                            headers = headers
                        )
                    )
                }
            }
        } catch (e: Exception) { Log.e("Hownetwork", "Error: ${e.message}") }
    }
}

class Cloudhownetwork : Hownetwork() { override var mainUrl = "https://cloud.hownetwork.xyz" }
class Furher : Filesim() { override val name = "Furher"; override var mainUrl = "https://furher.in" }
class Furher2 : Filesim() { override val name = "Furher 2"; override var mainUrl = "723qrh1p.fun" }


// ==========================================
// 2. KODE BARU (FIX PLAYERIFRAME & SANDBOX)
// ==========================================

class PlayerIframe : ExtractorApi() {
    override val name = "PlayerIframe"
    override val mainUrl = "https://playeriframe.sbs"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to (referer ?: "https://tv7.lk21official.cc/"),
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Upgrade-Insecure-Requests" to "1"
        )
        try {
            val response = app.get(url, headers = headers).text
            val regex = """<iframe[^>]+src=["']([^"']+)["']""".toRegex()
            val match = regex.find(response)?.groupValues?.get(1)
            
            if (match != null) {
                var innerUrl = if (match.startsWith("//")) "https:$match" else match
                // Fix Redirect Short.icu -> Hydrax
                if (innerUrl.contains("short.icu")) {
                    try { innerUrl = app.get(innerUrl, allowRedirects = true).url } catch (e: Exception) {}
                }
                // Fix Redirect Turbovid -> Emturbovid
                if (innerUrl.contains("turbovid")) {
                     try { innerUrl = app.get(innerUrl, headers = headers, allowRedirects = true).url } catch (e: Exception) {}
                }
                
                if (innerUrl.contains("hownetwork")) {
                    Hownetwork().getUrl(innerUrl, url, subtitleCallback, callback)
                } else {
                    UniversalVIP().getUrl(innerUrl, url, subtitleCallback, callback)
                }
            }
        } catch (e: Exception) { Log.e("PlayerIframe", "Error: ${e.message}") }
    }
}

class UniversalVIP : ExtractorApi() {
    override val name = "UniversalVIP"
    override val mainUrl = "https://f16px.com" 
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val domain = try { URI(url).host } catch (e: Exception) { "" }
        
        // Header SCRAPING (Lolos Sandbox)
        val scrapeHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to "https://playeriframe.sbs/", 
            "Sec-Fetch-Dest" to "iframe",
            "Upgrade-Insecure-Requests" to "1"
        )
        // Header PLAYBACK (Lolos 403 Forbidden)
        val playbackHeaders = mutableMapOf(
            "Origin" to "https://$domain",
            "Referer" to "https://$domain/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        if (domain.contains("emturbovid")) {
             playbackHeaders["Referer"] = "https://turbovidhls.com/"
             playbackHeaders["Origin"] = "https://turbovidhls.com"
        }

        // A. API (CAST/F16)
        if (url.contains("f16px") || url.contains("vidhide")) {
            try {
                val id = url.substringAfter("/e/").substringBefore("?")
                val apiUrl = "https://$domain/api/videos/$id/embed/playback"
                val apiHeaders = playbackHeaders.toMutableMap()
                apiHeaders["Referer"] = url 
                val jsonResponse = app.get(apiUrl, headers = apiHeaders).text
                val masterUrl = JSONObject(jsonResponse).optString("url")
                if (masterUrl.isNotEmpty()) {
                    M3u8Helper.generateM3u8(name, masterUrl, url, headers = apiHeaders).forEach(callback)
                    return
                }
            } catch (e: Exception) { }
        }

        // B. SCRAPING (TURBO/HYDRAX)
        try {
            val pageHtml = app.get(url, headers = scrapeHeaders).text
            var sourceUrl = ""
            val packedRegex = """eval\(function\(p,a,c,k,e,d.*""".toRegex()
            val packedMatch = packedRegex.find(pageHtml)?.value
            if (packedMatch != null) {
                val unpacked = getPacked(packedMatch) ?: ""
                sourceUrl = """file:"([^"]+\.m3u8[^"]*)"""".toRegex().find(unpacked)?.groupValues?.get(1) ?: ""
            }
            if (sourceUrl.isEmpty()) {
                sourceUrl = """file:\s*["']([^"']+\.m3u8[^"']*)["']""".toRegex().find(pageHtml)?.groupValues?.get(1) ?: ""
            }
            if (sourceUrl.isNotEmpty()) {
                M3u8Helper.generateM3u8(name, sourceUrl, url, headers = playbackHeaders).forEach(callback)
            } else {
                VidHidePro6().getUrl(url, referer, subtitleCallback, callback)
            }
        } catch (e: Exception) { Log.e("UniversalVIP", "Error: ${e.message}") }
    }
}

// Redirectors untuk Plugin
class Turbovidhls : ExtractorApi() {
    override val name = "Turbovid"
    override val mainUrl = "https://turbovidhls.com"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) { UniversalVIP().getUrl(url, referer, subtitleCallback, callback) }
}
class F16px : ExtractorApi() { override val name = "VidHide (F16)"; override val mainUrl = "https://f16px.com"; override val requiresReferer = false; override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) { UniversalVIP().getUrl(url, referer, subtitleCallback, callback) } }
class EmturbovidCustom : ExtractorApi() { override val name = "Emturbovid"; override val mainUrl = "https://emturbovid.com"; override val requiresReferer = false; override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) { val response = app.get(url).text; val regex = """["'](https?://[^"']*turbovidhls[^"']*)["']""".toRegex(); val realUrl = regex.find(response)?.groupValues?.get(1); if (realUrl != null) UniversalVIP().getUrl(realUrl, url, subtitleCallback, callback) } }
