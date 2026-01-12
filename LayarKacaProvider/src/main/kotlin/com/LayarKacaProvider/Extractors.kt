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

// --- EXTRACTOR CUSTOM ---

class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

// Extractor untuk Furher dan variasinya
open class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Furher2 : Furher() {
    override val name = "Furher 2"
    override var mainUrl = "https://furher.net"
}

class Furher3 : Furher() {
    override val name = "Furher 3"
    override var mainUrl = "https://fuh.xyz" 
}

class Turbovidhls : Filesim() {
    override val name = "Turbovidhls"
    override var mainUrl = "https://turbovidhls.com"
}

// --- HOWNETWORK LOGIC (UPDATE: SUPPORT API 1 & 2) ---

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
        
        // Daftar endpoint yang akan dicoba berurutan
        val endpoints = listOf("api.php", "api2.php")
        
        for (endpoint in endpoints) {
            try {
                // Request ke API (mencoba api.php dulu, lalu api2.php)
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
                
                // Jika berhasil mendapatkan file
                if (file.isNotBlank() && file != "null") {
                    val properReferer = url 
                    val headers = mapOf(
                        "Origin" to mainUrl,
                        "Referer" to properReferer,
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )

                    // Coba generate M3U8 (Playlist)
                    val playlist = M3u8Helper.generateM3u8(
                        source = this.name,
                        streamUrl = file,
                        referer = properReferer,
                        headers = headers
                    )

                    if (playlist.isNotEmpty()) {
                        playlist.forEach(callback)
                        return // Sukses, keluar dari fungsi
                    } else {
                        // Fallback: Jika M3u8Helper gagal, masukkan link secara manual
                        callback(
                            newExtractorLink(
                                source = this.name,
                                name = this.name,
                                url = file,
                                type = INFER_TYPE
                            ).apply {
                                this.headers = headers
                                this.referer = properReferer
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        return // Sukses, keluar dari fungsi
                    }
                }
            } catch (e: Exception) {
                // Jika error di api.php, log errornya dan lanjut coba api2.php
                Log.e("Hownetwork", "Error on $endpoint: ${e.message}")
            }
        }
        
        Log.e("Hownetwork", "All endpoints failed for ID: $id")
    }
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}
