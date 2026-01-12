package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import org.json.JSONObject

// Extractor Generik untuk Turbovid, dll
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
        // Logika untuk membersihkan ID
        val id = url.substringAfter("id=")
                    .substringBefore("&") // Jaga-jaga ada parameter lain di belakang

        // Request ke API
        val response = app.post(
                "$mainUrl/api.php?id=$id",
                data = mapOf(
                        "r" to (referer ?: ""),
                        "d" to mainUrl,
                ),
                referer = url,
                headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest"
                )
        ).text

        try {
            val json = JSONObject(response)
            val file = json.optString("file")
            
            if (file.isNotBlank() && file != "null") {
                Log.d("Phisher-Success", "File Found: $file")
                
                // PERBAIKAN UTAMA DI SINI:
                // Menambahkan Referer Header ke dalam helper M3u8
                // Agar server tidak menolak (HTTP 403 Forbidden)
                M3u8Helper.generateM3u8(
                    source = this.name,
                    streamUrl = file,
                    referer = "$mainUrl/", // Header penting!
                    headers = mapOf("Origin" to mainUrl) // Tambahan header
                ).forEach(callback)
            } else {
                 Log.d("Phisher-Error", "File is empty in JSON")
            }
        } catch (e: Exception) {
            Log.e("Phisher-Error", "Json parse error: ${e.message}")
        }
    }
}

// Extractor Turunan
class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

// Extractor Standar
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
