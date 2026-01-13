package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getPacked
import org.json.JSONObject
import java.net.URI

// ==========================================
// 1. PLAYER IFRAME (ROUTER UTAMA)
// ==========================================
class PlayerIframe : ExtractorApi() {
    override val name = "PlayerIframe"
    override val mainUrl = "https://playeriframe.sbs"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to (referer ?: "https://tv7.lk21official.cc/"),
            "Sec-Fetch-Dest" to "iframe",
            "Upgrade-Insecure-Requests" to "1"
        )

        try {
            val response = app.get(url, headers = headers).text
            val regex = """<iframe[^>]+src=["']([^"']+)["']""".toRegex()
            val match = regex.find(response)?.groupValues?.get(1)
            
            if (match != null) {
                var innerUrl = if (match.startsWith("//")) "https:$match" else match
                
                // Fix Redirect: Short.icu -> Abyss
                if (innerUrl.contains("short.icu")) {
                    try {
                        innerUrl = app.get(innerUrl, allowRedirects = true).url
                    } catch (e: Exception) {}
                }
                
                // Fix Redirect: Turbovid -> Emturbovid (PENTING!)
                if (innerUrl.contains("turbovid")) {
                     try {
                        // Ikuti redirect Turbovid sampai ujung
                        val res = app.get(innerUrl, headers = headers, allowRedirects = true)
                        innerUrl = res.url
                        Log.d("LayarKaca", "Turbovid Redirected to: $innerUrl")
                    } catch (e: Exception) {}
                }

                Log.d("LayarKaca", "Final URL to process: $innerUrl")

                if (innerUrl.contains("hownetwork")) {
                    Hownetwork().getUrl(innerUrl, url, subtitleCallback, callback)
                } else {
                    // Oper ke UniversalVIP
                    UniversalVIP().getUrl(innerUrl, url, subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerIframe", "Error: ${e.message}")
        }
    }
}

// ==========================================
// 2. UNIVERSAL VIP (CAST, TURBO, HYDRAX)
// ==========================================
class UniversalVIP : ExtractorApi() {
    override val name = "UniversalVIP"
    override val mainUrl = "https://f16px.com" 
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val domain = try { URI(url).host } catch (e: Exception) { "" }
        
        // Header SCRAPING (Anti-Sandbox)
        val scrapeHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to "https://playeriframe.sbs/", 
            "Sec-Fetch-Dest" to "iframe",
            "Upgrade-Insecure-Requests" to "1"
        )

        // Header PLAYBACK (Anti-403)
        // Khusus Turbo/Emturbo, Referer harus spesifik
        val playbackHeaders = mutableMapOf(
            "Origin" to "https://$domain",
            "Referer" to "https://$domain/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )

        // Jika domainnya emturbovid, paksa referer ke turbovidhls juga (kadang perlu)
        if (domain.contains("emturbovid")) {
             playbackHeaders["Referer"] = "https://turbovidhls.com/"
             playbackHeaders["Origin"] = "https://turbovidhls.com"
        }

        // A. API Method (CAST)
        if (url.contains("f16px") || url.contains("vidhide")) {
            try {
                val id = url.substringAfter("/e/").substringBefore("?")
                val apiUrl = "https://$domain/api/videos/$id/embed/playback"
                val jsonResponse = app.get(apiUrl, headers = playbackHeaders).text
                val masterUrl = JSONObject(jsonResponse).optString("url")
                if (masterUrl.isNotEmpty()) {
                    M3u8Helper.generateM3u8(name, masterUrl, url, headers = playbackHeaders).forEach(callback)
                    return
                }
            } catch (e: Exception) { }
        }

        // B. Scraping Method (TURBO, HYDRAX)
        try {
            val pageHtml = app.get(url, headers = scrapeHeaders).text
            var sourceUrl = ""

            // 1. Cari Packed JS
            val packedRegex = """eval\(function\(p,a,c,k,e,d.*""".toRegex()
            val packedMatch = packedRegex.find(pageHtml)?.value
            if (packedMatch != null) {
                val unpacked = getPacked(packedMatch) ?: ""
                sourceUrl = """file:"([^"]+\.m3u8[^"]*)"""".toRegex().find(unpacked)?.groupValues?.get(1) ?: ""
            }

            // 2. Cari Regex Standar
            if (sourceUrl.isEmpty()) {
                sourceUrl = """file:\s*["']([^"']+\.m3u8[^"']*)["']""".toRegex().find(pageHtml)?.groupValues?.get(1) ?: ""
            }

            if (sourceUrl.isNotEmpty()) {
                M3u8Helper.generateM3u8(name, sourceUrl, url, headers = playbackHeaders).forEach(callback)
            } else {
                VidHidePro6().getUrl(url, referer, subtitleCallback, callback)
            }
        } catch (e: Exception) {
            Log.e("UniversalVIP", "Error: ${e.message}")
        }
    }
}

// ... (SISA FILE SAMA SEPERTI SEBELUMNYA: HOWNETWORK & REDIRECTOR CLASSES) ...
// Copy bagian bawah dari jawaban sebelumnya
open class Hownetwork : ExtractorApi() {
    override val name = "Hownetwork"
    override val mainUrl = "https://stream.hownetwork.xyz"
    override val requiresReferer = true
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val id = url.substringAfter("id=").substringBefore("&")
        val endpoints = listOf("api.php", "api2.php")
        for (endpoint in endpoints) {
            try {
                val response = app.post("$mainUrl/$endpoint?id=$id", data = mapOf("r" to (referer ?: ""), "d" to mainUrl), referer = url, headers = mapOf("X-Requested-With" to "XMLHttpRequest")).text
                val json = JSONObject(response)
                val file = json.optString("file")
                if (file.isNotBlank() && file != "null") {
                    val headers = mapOf("Origin" to mainUrl, "Referer" to url, "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    M3u8Helper.generateM3u8(name, file, url, headers = headers).forEach(callback)
                    return 
                }
            } catch (e: Exception) { Log.e("Hownetwork", e.message ?: "") }
        }
    }
}
class Cloudhownetwork : Hownetwork() { override var mainUrl = "https://cloud.hownetwork.xyz" }
class Turbovidhls : ExtractorApi() { override val name = "Turbovid"; override val mainUrl = "https://turbovidhls.com"; override val requiresReferer = false; override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) { UniversalVIP().getUrl(url, referer, subtitleCallback, callback) } }
class F16px : ExtractorApi() { override val name = "VidHide (F16)"; override val mainUrl = "https://f16px.com"; override val requiresReferer = false; override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) { UniversalVIP().getUrl(url, referer, subtitleCallback, callback) } }
class EmturbovidCustom : ExtractorApi() { override val name = "Emturbovid"; override val mainUrl = "https://emturbovid.com"; override val requiresReferer = false; override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) { val response = app.get(url).text; val regex = """["'](https?://[^"']*turbovidhls[^"']*)["']""".toRegex(); val realUrl = regex.find(response)?.groupValues?.get(1); if (realUrl != null) UniversalVIP().getUrl(realUrl, url, subtitleCallback, callback) } }
class Co4nxtrl : Filesim() { override val mainUrl = "https://co4nxtrl.com"; override val name = "Co4nxtrl" }
class Furher : Filesim() { override val name = "Furher"; override var mainUrl = "https://furher.in" }
class Furher2 : Filesim() { override val name = "Furher 2"; override var mainUrl = "723qrh1p.fun" }
