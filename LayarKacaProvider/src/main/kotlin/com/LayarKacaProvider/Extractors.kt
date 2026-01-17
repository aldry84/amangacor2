package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.M3u8Helper
import org.json.JSONObject
import java.util.Base64 // <-- GANTI: Pakai Java util, bukan Android util
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Turbovidhls : ExtractorApi() {
    override val name = "Turbovid"
    override val mainUrl = "https://turbovidhls.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Host" to "turbovidhls.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.9",
            "Origin" to "https://turbovidhls.com",
            "Referer" to "https://turbovidhls.com/",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache"
        )

        M3u8Helper.generateM3u8(
            source = this.name,
            streamUrl = url,
            referer = "https://turbovidhls.com/",
            headers = headers
        ).forEach(callback)
    }
}

// === EXTRACTOR CANGGIH UNTUK CAST/F16PX ===
class F16Px : ExtractorApi() {
    override val name = "F16Px"
    override val mainUrl = "https://f16px.com" 
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // 1. Ambil ID Code dari URL
        val code = url.substringAfterLast("/").substringBefore("?")
        
        // 2. Siapkan API URL
        val apiUrl = "https://f16px.com/api/videos/$code/embed/playback"

        // 3. Headers Wajib
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Origin" to "https://f16px.com",
            "Referer" to "https://f16px.com/e/$code",
            "x-embed-origin" to "playeriframe.sbs",
            "x-embed-parent" to "https://f16px.com/e/$code",
            "x-embed-referer" to "https://playeriframe.sbs/"
        )

        try {
            // 4. Request ke API
            val response = app.get(apiUrl, headers = headers).text
            val json = JSONObject(response)

            // 5. Ambil Data Enkripsi
            if (json.has("playback")) {
                val playback = json.getJSONObject("playback")
                val encryptedPayload = playback.getString("payload")
                val ivString = playback.getString("iv")
                
                val keys = json.optJSONObject("decrypt_keys")
                val keyString = keys?.optString("legacy_fallback") ?: ""

                if (encryptedPayload.isNotEmpty() && keyString.isNotEmpty()) {
                    // 6. Lakukan Dekripsi
                    val decryptedUrl = decryptAesGcm(encryptedPayload, keyString, ivString)
                    
                    if (decryptedUrl.startsWith("http")) {
                        // 7. Putar Video Hasil Dekripsi
                        M3u8Helper.generateM3u8(
                            source = "CAST",
                            streamUrl = decryptedUrl,
                            referer = "https://f16px.com/",
                            headers = mapOf(
                                "User-Agent" to headers["User-Agent"]!!,
                                "Origin" to "https://f16px.com"
                            )
                        ).forEach(callback)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fungsi Dekripsi AES-256-GCM (Versi Java Standard - Support Cross Platform)
    private fun decryptAesGcm(encryptedBase64: String, keyString: String, ivBase64: String): String {
        return try {
            val decodedKey = keyString.toByteArray(Charsets.UTF_8)
            
            // PERUBAHAN DI SINI: Menggunakan Java util Base64
            val decoder = Base64.getDecoder()
            val decodedIv = decoder.decode(ivBase64)
            val decodedPayload = decoder.decode(encryptedBase64)

            val spec = GCMParameterSpec(128, decodedIv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(decodedKey, "AES"), spec)

            val decryptedBytes = cipher.doFinal(decodedPayload)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace() // Print error ke log jika gagal
            "" 
        }
    }
}

// Class CastBox redirect ke F16
