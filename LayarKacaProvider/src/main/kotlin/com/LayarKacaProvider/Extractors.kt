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
import java.net.URI

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
        // 1. Ambil Host Dinamis (bisa stream.hownetwork atau cloud.hownetwork)
        val host = try { URI(url).host } catch (e: Exception) { URI(mainUrl).host }
        val apiHost = "https://$host"
        
        // 2. Ambil ID
        val id = url.substringAfter("id=").substringBefore("&")
        
        // 3. Request ke API2.php (Sesuai Log Curl terbaru)
        val postData = mapOf(
            "r" to "https://playeriframe.sbs/", // Value dari log curl
            "d" to host
        )

        val response = app.post(
                "$apiHost/api2.php?id=$id", 
                data = postData,
                referer = url, // Referer request API adalah URL video page
                headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Origin" to apiHost
                )
        ).text

        try {
            val json = JSONObject(response)
            val file = json.optString("file")
            
            if (file.isNotBlank() && file != "null") {
                Log.d("LayarKaca-Extractor", "File Found: $file")
                
                // Headers wajib untuk M3U8 (Referer harus url halaman video)
                val m3u8Headers = mapOf(
                    "Origin" to apiHost,
                    "Referer" to url, // PENTING: Referer harus https://cloud.hownetwork.xyz/video.php...
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
                )

                // Generate M3U8
                M3u8Helper.generateM3u8(
                    source = this.name,
                    streamUrl = file,
                    referer = url,
                    headers = m3u8Headers
                ).forEach(callback)

            } else {
                 Log.d("LayarKaca-Extractor", "File empty for $name")
            }
        } catch (e: Exception) {
            Log.e("LayarKaca-Extractor", "Error parsing JSON $name: ${e.message}")
        }
    }
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
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
