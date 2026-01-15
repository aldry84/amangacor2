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

// Extractor Generik
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
        // Default behavior untuk Hownetwork lama (api.php)
        val apiEndpoint = "$mainUrl/api.php?id=$id"
        invokeApi(url, apiEndpoint, id, referer, callback)
    }

    // Fungsi bantu agar bisa dipanggil oleh child class
    protected suspend fun invokeApi(
        originalUrl: String,
        apiEndpoint: String,
        id: String, 
        referer: String?, 
        callback: (ExtractorLink) -> Unit
    ) {
        val reqReferer = "$mainUrl/video.php?id=$id"
        
        // Header sesuai CURL
        val headers = mapOf(
            "Origin" to mainUrl,
            "Referer" to reqReferer,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "X-Requested-With" to "XMLHttpRequest"
        )

        // Body sesuai CURL
        val bodyData = mapOf(
            "r" to "https://playeriframe.sbs/", // Hardcoded based on logs/CURL
            "d" to mainUrl.substringAfter("://"), // cloud.hownetwork.xyz
        )

        try {
            val response = app.post(
                apiEndpoint,
                headers = headers,
                data = bodyData,
                referer = reqReferer
            ).text

            val json = JSONObject(response)
            val file = json.optString("file")
            
            if (file.isNotBlank() && file != "null") {
                Log.d("Phisher-Success", "File Found: $file")
                
                // M3U8 Helper dengan header yang benar
                val playlist = M3u8Helper.generateM3u8(
                    source = this.name,
                    streamUrl = file,
                    referer = reqReferer, // Penting! M3U8 butuh referer ini
                    headers = headers
                )

                if (playlist.isNotEmpty()) {
                    playlist.forEach(callback)
                } else {
                    // Fallback jika generateM3u8 gagal parsing
                    callback(
                        newExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = file,
                            type = INFER_TYPE
                        ).apply {
                            this.headers = headers
                            this.referer = reqReferer
                        }
                    )
                }
            } else {
                 Log.d("Phisher-Error", "File is empty in JSON response")
            }
        } catch (e: Exception) {
            Log.e("Phisher-Error", "Error fetching Hownetwork: ${e.message}")
        }
    }
}

class Cloudhownetwork : Hownetwork() {
    override val name = "CloudHownetwork"
    override val mainUrl = "https://cloud.hownetwork.xyz"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfter("id=").substringBefore("&")
        // UPDATE TERBARU: Menggunakan api2.php sesuai CURL
        val apiEndpoint = "$mainUrl/api2.php?id=$id"
        invokeApi(url, apiEndpoint, id, referer, callback)
    }
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
