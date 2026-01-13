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
// 1. HOWNETWORK (P2P) - DENGAN FITUR CADANGAN API2
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
        
        // Loop cek endpoint: coba api.php, kalau gagal coba api2.php
        val endpoints = listOf("api.php", "api2.php")

        for (endpoint in endpoints) {
            try {
                val response = app.post(
                        "$mainUrl/$endpoint?id=$id",
                        data = mapOf(
                                "r" to (referer ?: ""),
                                "d" to mainUrl,
                        ),
                        referer = url,
                        headers = mapOf(
                                "X-Requested-With" to "XMLHttpRequest"
                        )
                ).text

                val json = JSONObject(response)
                val file = json.optString("file")
                
                // Jika file ditemukan, proses dan stop looping
                if (file.isNotBlank() && file != "null") {
                    val headers = mapOf(
                        "Origin" to mainUrl,
                        "Referer" to url,
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                    )
                    
                    M3u8Helper.generateM3u8(name, file, url, headers).forEach(callback)
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
// 2. TURBOVIP (TURBOVIDHLS) - FIX BERDASARKAN CURL
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
            // 1. Ambil source halaman
            val response = app.get(url, referer = referer).text
            
            // 2. Cari file m3u8 via Regex
            val regex = """file:\s*["']([^"']+\.m3u8)["']""".toRegex()
            val m3u8Link = regex.find(response)?.groupValues?.get(1)

            if (!m3u8Link.isNullOrEmpty()) {
                // 3. Header Wajib (Sesuai Curl kamu)
                val headers = mapOf(
                    "Origin" to "https://turbovidhls.com",
                    "Referer" to "https://turbovidhls.com/",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )

                M3u8Helper.generateM3u8(
                    this.name,
                    m3u8Link,
                    "https://turbovidhls.com/",
                    headers
                ).forEach(callback)
            }
        } catch (e: Exception) {
            Log.e("Turbovid", "Error: ${e.message}")
        }
    }
}

// Redirector untuk Turbovid (Menangani link emturbovid.com)
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
        // Cari link asli turbovidhls di dalam iframe/script
        val regex = """["'](https?://[^"']*turbovidhls[^"']*)["']""".toRegex()
        val realUrl = regex.find(response)?.groupValues?.get(1)

        if (realUrl != null) {
            Turbovidhls().getUrl(realUrl, url, subtitleCallback, callback)
        }
    }
}

// ==========================================================
// 3. CAST (F16PX / VIDHIDE)
// ==========================================================
class F16px : VidHidePro6() {
    override val name = "VidHide (F16)"
    override val mainUrl = "https://f16px.com"
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
