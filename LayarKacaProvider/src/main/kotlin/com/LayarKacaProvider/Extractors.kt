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

// --- 1. ROUTER UTAMA: PlayerIframe ---
// Ini adalah "Resepsionis" yang membagi tugas ke server lain
open class PlayerIframe : ExtractorApi() {
    override val name = "PlayerIframe"
    override val mainUrl = "https://playeriframe.sbs"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer).document
        val iframeSrc = res.select("iframe").attr("src").let {
             if (it.startsWith("//")) "https:$it" else it
        }

        // Detektif URL: Tentukan ini server apa?
        if (url.contains("/turbovip/") || iframeSrc.contains("turbovid") || iframeSrc.contains("emturbovid")) {
            Turbovidhls().getUrl(url, referer, subtitleCallback, callback)
        } 
        else if (url.contains("/cast/") || iframeSrc.contains("f16px")) {
            F16px().getUrl(url, referer, subtitleCallback, callback)
        } 
        else if (url.contains("/hydrax/") || iframeSrc.contains("short.icu") || iframeSrc.contains("abysscdn")) {
            Hydrax().getUrl(url, referer, subtitleCallback, callback)
        }
        else if (url.contains("/p2p/") || iframeSrc.contains("hownetwork")) {
             Hownetwork().getUrl(iframeSrc, url, subtitleCallback, callback)
        }
    }
}

// --- 2. Hownetwork (API v2) ---
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
        val host = try { URI(url).host } catch (e: Exception) { URI(mainUrl).host }
        val apiHost = "https://$host"
        val id = url.substringAfter("id=").substringBefore("&")
        
        try {
            val response = app.post(
                "$apiHost/api2.php?id=$id", 
                data = mapOf("r" to "https://playeriframe.sbs/", "d" to host),
                referer = url,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest", "Origin" to apiHost)
            ).text

            val json = JSONObject(response)
            val file = json.optString("file")
            
            if (file.isNotBlank() && file != "null") {
                M3u8Helper.generateM3u8(
                    this.name,
                    file,
                    url,
                    null, // Fix parameter Quality
                    mapOf("Origin" to apiHost, "Referer" to url)
                ).forEach(callback)
            }
        } catch (e: Exception) {
            Log.e("LayarKaca", "Hownetwork Error: ${e.message}")
        }
    }
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

// --- 3. Turbovid (Redirect Handler) ---
open class Turbovidhls : ExtractorApi() {
    override val name = "Turbovid"
    override val mainUrl = "https://turbovidhls.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var finalUrl = url
        // Buka bungkus playeriframe/emturbovid jika perlu
        if (url.contains("playeriframe") || url.contains("emturbovid")) {
            val response = app.get(url, referer = referer).document
            val src = response.select("iframe").attr("src")
            if (src.isNotBlank()) finalUrl = if (src.startsWith("//")) "https:$src" else src
        }
        
        // Redirect lagi jika masih emturbovid (kadang 2x redirect)
        if (finalUrl.contains("emturbovid")) {
             finalUrl = app.get(finalUrl, referer="https://playeriframe.sbs/").url
        }

        val doc = app.get(finalUrl, referer = "https://playeriframe.sbs/").document
        val script = doc.select("script").find { it.data().contains("master.m3u8") }?.data()
        
        if (script != null) {
            val m3u8Url = Regex("""["'](https?://[^"']+/master\.m3u8)["']""").find(script)?.groupValues?.get(1)
            if (m3u8Url != null) {
                M3u8Helper.generateM3u8(
                    name,
                    m3u8Url,
                    "https://turbovidhls.com/", 
                    null,
                    mapOf("Origin" to "https://turbovidhls.com")
                ).forEach(callback)
            }
        }
    }
}

// --- 4. F16px (Hidden API) ---
open class F16px : ExtractorApi() {
    override val name = "F16px"
    override val mainUrl = "https://f16px.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Logika unwrap sama
        val finalUrl = if (url.contains("playeriframe")) {
            val doc = app.get(url, referer = referer).document
            doc.select("iframe[src*='f16px']").attr("src").let { if (it.startsWith("//")) "https:$it" else it }
        } else url

        if (!finalUrl.contains("/e/")) return

        val id = finalUrl.substringAfter("/e/").substringBefore("?")
        val apiUrl = "$mainUrl/api/videos/$id/embed/playback"

        try {
            val response = app.get(apiUrl, headers = mapOf(
                "Referer" to "$mainUrl/e/$id",
                "x-embed-parent" to "$mainUrl/e/$id"
            )).text
            
            val sources = JSONObject(response).optJSONArray("sources") ?: return
            for (i in 0 until sources.length()) {
                val file = sources.getJSONObject(i).optString("file")
                if (file.isNotBlank()) {
                    M3u8Helper.generateM3u8(
                        this.name,
                        file,
                        "$mainUrl/",
                        null,
                        mapOf("Origin" to mainUrl)
                    ).forEach(callback)
                }
            }
        } catch (e: Exception) {
            Log.e("LayarKaca", "F16 Error: ${e.message}")
        }
    }
}

// --- 5. Hydrax / AbyssCDN ---
open class Hydrax : ExtractorApi() {
    override val name = "Hydrax"
    override val mainUrl = "https://abysscdn.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var targetUrl = url
        if (url.contains("playeriframe")) {
             targetUrl = app.get(url, referer = referer).document.select("iframe").attr("src")
             if (targetUrl.startsWith("//")) targetUrl = "https:$targetUrl"
        }
        if (targetUrl.contains("short.icu")) {
             targetUrl = app.get(targetUrl, referer = "https://playeriframe.sbs/").url
        }
        
        if (!targetUrl.contains("abysscdn")) return

        val response = app.get(targetUrl, referer = "https://playeriframe.sbs/").text
        val regex = Regex("""["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""")
        
        regex.findAll(response).forEach { match ->
            val vUrl = match.groupValues[1]
            if (!vUrl.contains(".jpg") && !vUrl.contains(".png")) {
                if (vUrl.contains(".m3u8")) {
                    M3u8Helper.generateM3u8(this.name, vUrl, "https://abysscdn.com/", null, mapOf("Origin" to "https://abysscdn.com")).forEach(callback)
                } else {
                    callback(newExtractorLink(this.name, this.name, vUrl, INFER_TYPE).apply {
                        this.referer = "https://abysscdn.com/"
                    })
                }
            }
        }
    }
}

// --- 6. Lain-lain ---
class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}
class Furher : Filesim() { override val name = "Furher"; override var mainUrl = "https://furher.in" }
class Furher2 : Filesim() { override val name = "Furher 2"; override var mainUrl = "723qrh1p.fun" }
