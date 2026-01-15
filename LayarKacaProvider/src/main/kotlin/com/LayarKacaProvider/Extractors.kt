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
// 1. UPDATE: TURBOVIP / TURBOVIDHLS
// ==========================================
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
        // Headers ketat sesuai data Curl Anda
        // Origin dan Referer WAJIB https://turbovidhls.com/ (dengan trailing slash)
        val headers = mapOf(
            "Origin" to mainUrl,
            "Referer" to "$mainUrl/",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
        )

        try {
            // 1. Ambil Source HTML dari halaman embed
            val response = app.get(url, headers = headers).text

            // 2. Cari link .m3u8 di dalam script HTML
            // Pola umum: file: "https://..." atau sources: [{file: "..."}]
            val regex = """file\s*:\s*["']([^"']+\.m3u8[^"']*)["']""".toRegex()
            val masterUrl = regex.find(response)?.groupValues?.get(1)

            if (!masterUrl.isNullOrEmpty()) {
                Log.d("Turbovid", "Found Master M3U8: $masterUrl")

                // 3. Generate M3U8 dengan Header Khusus
                // Header ini akan diteruskan ke setiap request segmen (.ts/image)
                M3u8Helper.generateM3u8(
                    source = name,
                    streamUrl = masterUrl,
                    referer = "$mainUrl/", 
                    headers = headers // KUNCI: Header ini agar segmen 'gambar' ibyteimg bisa diputar
                ).forEach(callback)

            } else {
                Log.e("Turbovid", "M3U8 url not found in embed page")
                // Opsional: Tambahkan logika regex alternatif jika pola 'file:' tidak ketemu
            }

        } catch (e: Exception) {
            Log.e("Turbovid", "Error: ${e.message}")
        }
    }
}

// ==========================================
// 2. UPDATE: P2P / CLOUD HOWNETWORK (API2.PHP)
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
        val id = url.substringAfter("id=").substringBefore("&")
        
        val headers = mapOf(
            "Origin" to mainUrl,
            "Referer" to url,
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "X-Requested-With" to "XMLHttpRequest"
        )

        // Menggunakan referer dari parameter atau default
        val rParam = if (referer != null && referer.contains("http")) referer else "https://playeriframe.sbs/"
        
        val postData = mapOf(
            "r" to rParam,
            "d" to "cloud.hownetwork.xyz"
        )

        try {
            // Update: Menggunakan api2.php
            val response = app.post(
                "$mainUrl/api2.php?id=$id",
                headers = headers,
                data = postData
            ).text

            val json = JSONObject(response)
            val file = json.optString("file")
            
            if (file.isNotBlank() && file != "null") {
                M3u8Helper.generateM3u8(
                    source = this.name,
                    streamUrl = file,
                    referer = url,
                    headers = headers
                ).forEach(callback)
            }
        } catch (e: Exception) {
            Log.e("CloudHownetwork", "Error: ${e.message}")
        }
    }
}

// ==========================================
// EXTRACTOR LAINNYA (LAMA)
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
                "$mainUrl/api.php?id=$id",
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
