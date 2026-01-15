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

// --- 1. Server Co4nxtrl (Standar) ---
class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

// --- 2. Server Hownetwork (API v2 Update) ---
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
        
        val postData = mapOf(
            "r" to "https://playeriframe.sbs/",
            "d" to host
        )

        try {
            val response = app.post(
                "$apiHost/api2.php?id=$id", 
                data = postData,
                referer = url,
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Origin" to apiHost
                )
            ).text

            val json = JSONObject(response)
            val file = json.optString("file")
            
            if (file.isNotBlank() && file != "null") {
                val m3u8Headers = mapOf(
                    "Origin" to apiHost,
                    "Referer" to url,
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
                )

                M3u8Helper.generateM3u8(
                    source = this.name,
                    streamUrl = file,
                    referer = url,
                    headers = m3u8Headers
                ).forEach(callback)
            }
        } catch (e: Exception) {
            Log.e("LayarKaca-Hownetwork", "Error: ${e.message}")
        }
    }
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

// --- 3. Server Furher (Standar) ---
class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Furher2 : Filesim() {
    override val name = "Furher 2"
    override var mainUrl = "723qrh1p.fun"
}

// --- 4. Server Turbovid (Redirect Handler) ---
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
        if (url.contains("playeriframe") || url.contains("emturbovid")) {
            val response = app.get(url, referer = referer).document
            val iframeSrc = response.select("iframe").attr("src")
            if (iframeSrc.isNotBlank()) {
                finalUrl = if (iframeSrc.startsWith("http")) iframeSrc else "https:$iframeSrc"
            }
        }

        val document = app.get(finalUrl, referer = "https://playeriframe.sbs/").document
        val script = document.select("script").find { it.data().contains("master.m3u8") }?.data()
        
        if (script != null) {
            val m3u8Url = Regex("""["'](https?://[^"']+/master\.m3u8)["']""").find(script)?.groupValues?.get(1)
            
            if (m3u8Url != null) {
                M3u8Helper.generateM3u8(
                    source = this.name,
                    streamUrl = m3u8Url,
                    referer = "https://turbovidhls.com/", 
                    headers = mapOf(
                        "Origin" to "https://turbovidhls.com",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                ).forEach(callback)
            }
        }
    }
}

// --- 5. Server F16px (Hidden API) ---
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
        val finalUrl = if (url.contains("playeriframe")) {
            val doc = app.get(url, referer = referer).document
            doc.select("iframe[src*='f16px']").attr("src").let { 
                if (it.startsWith("//")) "https:$it" else it 
            }
        } else {
            url
        }

        if (!finalUrl.contains("/e/")) return

        val id = finalUrl.substringAfter("/e/").substringBefore("?")
        val apiUrl = "$mainUrl/api/videos/$id/embed/playback"

        val apiHeaders = mapOf(
            "Referer" to "$mainUrl/e/$id",
            "X-Requested-With" to "XMLHttpRequest",
            "x-embed-origin" to "playeriframe.sbs",
            "x-embed-parent" to "$mainUrl/e/$id",
            "x-embed-referer" to "https://playeriframe.sbs/"
        )

        try {
            val response = app.get(apiUrl, headers = apiHeaders).text
            val json = JSONObject(response)
            val sources = json.optJSONArray("sources") ?: return

            for (i in 0 until sources.length()) {
                val source = sources.getJSONObject(i)
                val file = source.optString("file")
                
                if (file.isNotBlank()) {
                    val videoHeaders = mapOf(
                        "Origin" to mainUrl,
                        "Referer" to "$mainUrl/",
                    )

                    // PERBAIKAN: Menggunakan parameter 'source' yang benar
                    M3u8Helper.generateM3u8(
                        source = this.name,
                        streamUrl = file,
                        referer = "$mainUrl/",
                        headers = videoHeaders
                    ).forEach(callback)
                }
            }
        } catch (e: Exception) {
            Log.e("LayarKaca-F16", "Error: ${e.message}")
        }
    }
}

// --- 6. Server Hydrax / AbyssCDN (Regex HTML) ---
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
             val doc = app.get(url, referer = referer).document
             targetUrl = doc.select("iframe").attr("src").let {
                 if (it.startsWith("//")) "https:$it" else it
             }
        }

        if (targetUrl.contains("short.icu")) {
             targetUrl = app.get(targetUrl, referer = "https://playeriframe.sbs/").url
        }
        
        if (!targetUrl.contains("abysscdn")) return

        val response = app.get(targetUrl, referer = "https://playeriframe.sbs/").text
        
        val regex = Regex("""["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""")
        val matches = regex.findAll(response)
        
        matches.forEach { match ->
            val videoUrl = match.groupValues[1]
            if (!videoUrl.contains(".jpg") && !videoUrl.contains(".png")) {
                val isM3u8 = videoUrl.contains(".m3u8")
                
                if (isM3u8) {
                    // PERBAIKAN: Menggunakan parameter 'source'
                    M3u8Helper.generateM3u8(
                        source = this.name,
                        streamUrl = videoUrl,
                        referer = "https://abysscdn.com/",
                        headers = mapOf("Origin" to "https://abysscdn.com")
                    ).forEach(callback)
                } else {
                    // PERBAIKAN: Menggunakan newExtractorLink() alih-alih constructor
                    callback(
                        newExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = videoUrl,
                            referer = "https://abysscdn.com/",
                            quality = Qualities.Unknown.value,
                            type = INFER_TYPE
                        )
                    )
                }
            }
        }
    }
}
