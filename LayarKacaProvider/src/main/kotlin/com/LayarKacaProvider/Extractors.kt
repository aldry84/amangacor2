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

// ==========================================
// 1. PLAYER IFRAME (PEMBUKA PINTU)
// ==========================================
class PlayerIframe : ExtractorApi() {
    override val name = "PlayerIframe"
    override val mainUrl = "https://playeriframe.sbs"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val response = app.get(url, referer = referer).text
            val regex = """<iframe[^>]+src=["']([^"']+)["']""".toRegex()
            val match = regex.find(response)?.groupValues?.get(1)
            
            if (match != null) {
                val innerUrl = if (match.startsWith("//")) "https:$match" else match
                Log.d("LayarKaca", "PlayerIframe Unwrap: $innerUrl")

                if (innerUrl.contains("turbovid")) {
                    Turbovidhls().getUrl(innerUrl, url, subtitleCallback, callback)
                } else if (innerUrl.contains("f16px") || innerUrl.contains("vidhide") || innerUrl.contains("filemoon")) {
                    F16px().getUrl(innerUrl, url, subtitleCallback, callback)
                } else if (innerUrl.contains("hownetwork")) {
                    Hownetwork().getUrl(innerUrl, url, subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerIframe", "Error: ${e.message}")
        }
    }
}

// ==========================================
// 2. CAST (F16PX) - UPDATE AGAR TEMBUS SECURITY
// ==========================================
class F16px : ExtractorApi() {
    override val name = "VidHide (F16)"
    override val mainUrl = "https://f16px.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfter("/e/").substringBefore("?")
        val headers = mapOf(
            "Origin" to "https://f16px.com",
            "Referer" to "https://f16px.com/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )

        // METODE 1: Coba Tembak API (Cara Sopan)
        try {
            val apiUrl = "https://f16px.com/api/videos/$id/embed/playback"
            val jsonResponse = app.get(apiUrl, headers = headers).text
            val json = JSONObject(jsonResponse)
            val masterUrl = json.optString("url")

            if (masterUrl.isNotEmpty()) {
                Log.d("F16px", "API Success")
                M3u8Helper.generateM3u8(name, masterUrl, "https://f16px.com/", headers = headers).forEach(callback)
                return
            }
        } catch (e: Exception) {
            Log.e("F16px", "API method failed, trying scraping method...")
        }

        // METODE 2: Geledah HTML (Cara Kasar / Brute Force)
        try {
            val pageHtml = app.get(url, headers = headers).text
            
            // Cari kode packed (eval(function...))
            val packedRegex = """eval\(function\(p,a,c,k,e,d.*""".toRegex()
            val packedMatch = packedRegex.find(pageHtml)?.value
            
            var sourceUrl = ""

            if (packedMatch != null) {
                // Unpack JS-nya
                val unpacked = getPacked(packedMatch) ?: ""
                // Cari .m3u8 di dalam hasil unpack
                sourceUrl = """file:"([^"]+\.m3u8[^"]*)"""".toRegex().find(unpacked)?.groupValues?.get(1) ?: ""
            }

            // Jika masih kosong, cari regex standar di HTML mentah
            if (sourceUrl.isEmpty()) {
                sourceUrl = """file:\s*["']([^"']+\.m3u8[^"']*)["']""".toRegex().find(pageHtml)?.groupValues?.get(1) ?: ""
            }

            if (sourceUrl.isNotEmpty()) {
                Log.d("F16px", "Scraping Success: $sourceUrl")
                M3u8Helper.generateM3u8(name, sourceUrl, "https://f16px.com/", headers = headers).forEach(callback)
            } else {
                // Fallback Terakhir: Panggil Extractor Bawaan Cloudstream
                Log.d("F16px", "All methods failed, calling VidHidePro6")
                VidHidePro6().getUrl(url, referer, subtitleCallback, callback)
            }

        } catch (e: Exception) {
            Log.e("F16px", "Fatal Error: ${e.message}")
        }
    }
}

// ==========================================
// 3. TURBOVIP (TURBOVIDHLS) - SESUAI CURL
// ==========================================
class Turbovidhls : ExtractorApi() {
    override val name = "Turbovid"
    override val mainUrl = "https://turbovidhls.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val response = app.get(url, referer = referer).text
            val regex = """file:\s*["']([^"']+\.m3u8)["']""".toRegex()
            val m3u8Link = regex.find(response)?.groupValues?.get(1)

            if (!m3u8Link.isNullOrEmpty()) {
                val headers = mapOf(
                    "Origin" to "https://turbovidhls.com",
                    "Referer" to "https://turbovidhls.com/",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                )
                M3u8Helper.generateM3u8(name, m3u8Link, "https://turbovidhls.com/", headers = headers).forEach(callback)
            }
        } catch (e: Exception) { Log.e("Turbovid", e.message ?: "") }
    }
}

class EmturbovidCustom : ExtractorApi() {
    override val name = "Emturbovid"
    override val mainUrl = "https://emturbovid.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url).text
        val regex = """["'](https?://[^"']*turbovidhls[^"']*)["']""".toRegex()
        val realUrl = regex.find(response)?.groupValues?.get(1)
        if (realUrl != null) Turbovidhls().getUrl(realUrl, url, subtitleCallback, callback)
    }
}

// ==========================================
// 4. HOWNETWORK (P2P)
// ==========================================
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
        val endpoints = listOf("api.php", "api2.php")

        for (endpoint in endpoints) {
            try {
                val response = app.post(
                        "$mainUrl/$endpoint?id=$id",
                        data = mapOf("r" to (referer ?: ""), "d" to mainUrl),
                        referer = url,
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).text

                val json = JSONObject(response)
                val file = json.optString("file")
                
                if (file.isNotBlank() && file != "null") {
                    val headers = mapOf(
                        "Origin" to mainUrl,
                        "Referer" to url,
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                    )
                    M3u8Helper.generateM3u8(name, file, url, headers = headers).forEach(callback)
                    return 
                }
            } catch (e: Exception) { Log.e("Hownetwork", e.message ?: "") }
        }
    }
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

// ==========================================
// 5. CADANGAN
// ==========================================
class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Furher2 : Filesim() {
    override val name = "Furher 2"
    override var mainUrl = "723qrh1p.fun"
}
