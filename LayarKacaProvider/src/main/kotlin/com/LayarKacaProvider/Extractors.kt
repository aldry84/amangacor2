package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import org.json.JSONObject

// ==========================================
// 1. PLAYER IFRAME (UNWRAPPER)
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
                Log.d("LayarKaca", "Unwrapped: $innerUrl")

                if (innerUrl.contains("turbovid")) {
                    Turbovidhls().getUrl(innerUrl, url, subtitleCallback, callback)
                } else if (innerUrl.contains("f16px") || innerUrl.contains("vidhide")) {
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
// 2. CAST (F16PX) - NEW API LOGIC
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
        try {
            // Ambil ID dari URL (contoh: https://f16px.com/e/fgijrp498djx -> fgijrp498djx)
            val id = url.substringAfter("/e/").substringBefore("?")
            
            // 1. Tembak API Playback (Sesuai cURL kamu)
            val apiUrl = "https://f16px.com/api/videos/$id/embed/playback"
            val jsonResponse = app.get(
                apiUrl,
                headers = mapOf(
                    "Referer" to url,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            ).text

            // 2. Parse JSON untuk cari link .m3u8
            val json = JSONObject(jsonResponse)
            val masterUrl = json.optString("url")

            if (masterUrl.isNotEmpty()) {
                // 3. Header WAJIB untuk memutar video (Sesuai cURL)
                val headers = mapOf(
                    "Origin" to "https://f16px.com",
                    "Referer" to "https://f16px.com/",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )

                M3u8Helper.generateM3u8(
                    name,
                    masterUrl,
                    "https://f16px.com/",
                    headers = headers
                ).forEach(callback)
            } else {
                // Fallback: Jika API gagal, coba metode lama VidHidePro6
                VidHidePro6().getUrl(url, referer, subtitleCallback, callback)
            }

        } catch (e: Exception) {
            Log.e("F16px", "Error: ${e.message}")
            // Fallback terakhir
            VidHidePro6().getUrl(url, referer, subtitleCallback, callback)
        }
    }
}

// ==========================================
// 3. TURBOVIP (TURBOVIDHLS)
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
