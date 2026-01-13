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

// ==========================================================
// 1. HOWNETWORK (P2P) - SUDAH FIX & WORK
// ==========================================================
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
                    
                    M3u8Helper.generateM3u8(
                        source = name, 
                        streamUrl = file, 
                        referer = url, 
                        headers = headers 
                    ).forEach(callback)
                    return 
                }
            } catch (e: Exception) {
                Log.e("Hownetwork", "Gagal di $endpoint: ${e.message}")
            }
        }
    }
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

// ==========================================================
// 2. TURBOVIP (TURBOVIDHLS) - FIX BERDASARKAN CURL KAMU
// ==========================================================
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
            // 1. Buka halaman webnya
            val response = app.get(url, referer = referer).text
            
            // 2. Cari link .m3u8 di dalam script HTML
            val regex = """file:\s*["']([^"']+\.m3u8)["']""".toRegex()
            val m3u8Link = regex.find(response)?.groupValues?.get(1)

            if (!m3u8Link.isNullOrEmpty()) {
                // 3. Masukkan Header Sesuai cURL
                val headers = mapOf(
                    "Origin" to "https://turbovidhls.com",
                    "Referer" to "https://turbovidhls.com/",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )

                M3u8Helper.generateM3u8(
                    source = this.name,
                    streamUrl = m3u8Link,
                    referer = "https://turbovidhls.com/",
                    headers = headers
                ).forEach(callback)
            }
        } catch (e: Exception) {
            Log.e("Turbovid", "Error: ${e.message}")
        }
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

        if (realUrl != null) {
            Turbovidhls().getUrl(realUrl, url, subtitleCallback, callback)
        }
    }
}

// ==========================================================
// 3. CAST (F16PX)
// ==========================================================
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
        VidHidePro6().getUrl(url, referer, subtitleCallback, callback)
    }
}

// ==========================================================
// 4. CADANGAN LAINNYA
// ==========================================================
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
