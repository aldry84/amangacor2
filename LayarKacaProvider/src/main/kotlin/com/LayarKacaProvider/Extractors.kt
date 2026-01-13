package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

// --- HOWNETWORK & CLOUD (SUDAH WORK) ---
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
                data = mapOf("r" to (referer ?: ""), "d" to mainUrl),
                referer = url,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).text

        try {
            val json = JSONObject(response)
            val file = json.optString("file")
            if (file.isNotBlank() && file != "null") {
                val headers = mapOf(
                    "Origin" to mainUrl,
                    "Referer" to url,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                )
                
                // Prioritaskan M3U8 Generator
                M3u8Helper.generateM3u8(name, file, url, headers).forEach(callback)
                
                // Fallback direct link
                callback(newExtractorLink(name, name, file, INFER_TYPE, Qualities.Unknown.value).apply {
                    this.headers = headers
                    this.referer = url
                })
            }
        } catch (e: Exception) {
            Log.e("Hownetwork", "Error: ${e.message}")
        }
    }
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

// --- EXTRACTOR KHUSUS DOMAIN BARU (TURBO & CAST) ---

// Menangani CAST (f16px.com) -> Ini sebenarnya VidHide
class F16px : VidHidePro6() {
    override val name = "VidHide (F16)"
    override val mainUrl = "https://f16px.com"
}

// Menangani TURBOVIP (emturbovid.com)
class EmturbovidCustom : Filesim() {
    override val name = "Emturbovid"
    override var mainUrl = "https://emturbovid.com"
}

// Extractor cadangan lainnya
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
