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

// --- 1. ROUTER UTAMA (Penghubung) ---
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
        var iframeSrc = res.select("iframe").attr("src")
        if (iframeSrc.isEmpty()) iframeSrc = res.select("iframe").attr("data-src")
        if (iframeSrc.startsWith("//")) iframeSrc = "https:$iframeSrc"

        Log.d("LayarKaca-Router", "Routing $url to $iframeSrc")

        when {
            iframeSrc.contains("turbovid") || iframeSrc.contains("emturbovid") || url.contains("/turbovip/") -> {
                Turbovidhls().getUrl(url, referer, subtitleCallback, callback)
            }
            iframeSrc.contains("f16px") || url.contains("/cast/") -> {
                F16px().getUrl(url, referer, subtitleCallback, callback)
            }
            iframeSrc.contains("short.icu") || iframeSrc.contains("abysscdn") || url.contains("/hydrax/") -> {
                Hydrax().getUrl(url, referer, subtitleCallback, callback)
            }
            iframeSrc.contains("hownetwork") || url.contains("/p2p/") -> {
                val targetUrl = if (iframeSrc.contains("hownetwork")) iframeSrc else url
                Hownetwork().getUrl(targetUrl, url, subtitleCallback, callback)
            }
        }
    }
}

// --- 2. Hownetwork (P2P) ---
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
                    this.name, file, url, null,
                    mapOf("Origin" to apiHost, "Referer" to url)
                ).forEach(callback)
            }
        } catch (e: Exception) { Log.e("LayarKaca", "Hownetwork Error") }
    }
}

class Cloudhownetwork : Hownetwork() { override var mainUrl = "https://cloud.hownetwork.xyz" }

// --- 3. Turbovid (Emturbovid) ---
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
        var targetUrl = url
        if (targetUrl.contains("playeriframe") || targetUrl.contains("emturbovid")) {
            val response = app.get(targetUrl, referer = referer)
            targetUrl = response.url
            val src = response.document.select("iframe").attr("src")
            if (src.isNotBlank()) targetUrl = if (src.startsWith("//")) "https:$src" else src
        }

        val doc = app.get(targetUrl, referer = "https://playeriframe.sbs/").document
        val script = doc.select("script").find { it.data().contains("master.m3u8") }?.data() ?: doc.html()
        
        val m3u8Regex = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
        val m3u8Url = m3u8Regex.find(script)?.groupValues?.get(1)
        
        if (m3u8Url != null) {
            M3u8Helper.generateM3u8(
                this.name, m3u8Url, targetUrl, null,
                mapOf("Origin" to "https://turbovidhls.com", "Referer" to "https://turbovidhls.com/")
            ).forEach(callback)
        }
    }
}

// --- 4. F16px ---
open class F16px : ExtractorApi() {
    override val name = "F16px"
    override val mainUrl = "https://f16px.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val finalUrl = if (url.contains("playeriframe")) {
            app.get(url, referer = referer).document.select("iframe[src*='f16px']").attr("src").let { if (it.startsWith("//")) "https:$it" else it }
        } else url

        val id = finalUrl.substringAfter("/e/").substringBefore("?")
        val apiUrl = "$mainUrl/api/videos/$id/embed/playback"

        try {
            val response = app.get(apiUrl, headers = mapOf("Referer" to "$mainUrl/e/$id", "x-embed-parent" to "$mainUrl/e/$id")).text
            val sources = JSONObject(response).optJSONArray("sources") ?: return
            for (i in 0 until sources.length()) {
                val file = sources.getJSONObject(i).optString("file")
                if (file.isNotBlank()) {
                    M3u8Helper.generateM3u8(this.name, file, "$mainUrl/", null, mapOf("Origin" to mainUrl)).forEach(callback)
                }
            }
        } catch (e: Exception) { }
    }
}

// --- 5. Hydrax ---
open class Hydrax : ExtractorApi() {
    override val name = "Hydrax"
    override val mainUrl = "https://abysscdn.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        var targetUrl = url
        if (url.contains("playeriframe")) {
             targetUrl = app.get(url, referer = referer).document.select("iframe").attr("src")
             if (targetUrl.startsWith("//")) targetUrl = "https:$targetUrl"
        }
        if (targetUrl.contains("short.icu")) targetUrl = app.get(targetUrl).url
        
        if (!targetUrl.contains("abysscdn")) return
        val response = app.get(targetUrl, referer = "https://playeriframe.sbs/").text
        val regex = Regex("""["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""")
        
        regex.findAll(response).forEach { match ->
            val vUrl = match.groupValues[1]
            if (vUrl.contains(".m3u8")) {
                M3u8Helper.generateM3u8(this.name, vUrl, "https://abysscdn.com/", null, mapOf("Origin" to "https://abysscdn.com")).forEach(callback)
            } else if (vUrl.contains(".mp4")) {
                callback(newExtractorLink(this.name, this.name, vUrl, INFER_TYPE).apply { this.referer = "https://abysscdn.com/" })
            }
        }
    }
}

class Co4nxtrl : Filesim() { override val mainUrl = "https://co4nxtrl.com"; override val name = "Co4nxtrl" }
class Furher : Filesim() { override val name = "Furher"; override var mainUrl = "https://furher.in" }
class Furher2 : Filesim() { override val name = "Furher 2"; override var mainUrl = "723qrh1p.fun" }
