package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

// ==========================================
// UPDATE: P2P / CLOUD HOWNETWORK (API2.PHP)
// ==========================================
class Cloudhownetwork : ExtractorApi() {
    override val name = "CloudHownetwork"
    override val mainUrl = "https://cloud.hownetwork.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // url input example: https://cloud.hownetwork.xyz/video.php?id=KA07...
        val id = url.substringAfter("id=").substringBefore("&")
        
        // Headers sesuai data Curl Anda
        val headers = mapOf(
            "Origin" to mainUrl,
            "Referer" to url, // Referer saat request API adalah halaman video.php
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "X-Requested-With" to "XMLHttpRequest" // Biasanya diperlukan
        )

        // Data Body sesuai Curl
        // 'r' dalam data curl Anda adalah "https://playeriframe.sbs/"
        // Kita gunakan referer yang dipassing jika ada, atau default ke playeriframe
        val rParam = if (referer != null && referer.contains("http")) referer else "https://playeriframe.sbs/"
        
        val postData = mapOf(
            "r" to rParam,
            "d" to "cloud.hownetwork.xyz"
        )

        try {
            // Perhatikan: Menggunakan api2.php sesuai temuan baru
            val response = app.post(
                "$mainUrl/api2.php?id=$id",
                headers = headers,
                data = postData
            ).text

            val json = JSONObject(response)
            val file = json.optString("file")
            
            // Log untuk debug
            Log.d("CloudHownetwork", "Response: $response")

            if (file.isNotBlank() && file != "null") {
                
                // M3U8 Helper untuk generate quality list (360, 480, 720, 1080)
                val playlist = M3u8Helper.generateM3u8(
                    source = this.name,
                    streamUrl = file,
                    referer = url, // Referer file m3u8 biasanya sama dengan referer API
                    headers = headers
                )

                if (playlist.isNotEmpty()) {
                    playlist.forEach(callback)
                } else {
                    // Fallback jika generateM3u8 gagal, kirim raw link
                    callback(
                        newExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = file,
                            type = INFER_TYPE
                        ).apply {
                            this.headers = headers
                            this.referer = url
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            } else {
                 Log.e("CloudHownetwork", "File not found in API response")
            }
        } catch (e: Exception) {
            Log.e("CloudHownetwork", "Error: ${e.message}")
        }
    }
}

// ==========================================
// EXTRACTOR LAMA (Hownetwork Biasa)
// Biarkan jika masih ada link lama yang pakai api.php
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
        val response = app.post(
                "$mainUrl/api.php?id=$id", // Masih pakai api.php lama
                data = mapOf(
                        "r" to (referer ?: ""),
                        "d" to mainUrl,
                ),
                referer = url,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).text

        try {
            val json = JSONObject(response)
            val file = json.optString("file")
            if (file.isNotBlank() && file != "null") {
                M3u8Helper.generateM3u8(
                    source = this.name,
                    streamUrl = file,
                    referer = url
                ).forEach(callback)
            }
        } catch (e: Exception) {
            Log.e("Hownetwork", "Error: ${e.message}")
        }
    }
}

// Extractor Simple Lainnya
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

class Turbovidhls : Filesim() {
    override val name = "Turbovidhls"
    override var mainUrl = "https://turbovidhls.com"
}
