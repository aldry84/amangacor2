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

// ==========================================
// 1. ROUTER UTAMA (Resepsionis)
// ==========================================
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
        // Cari iframe src (bisa di atribut src atau data-src)
        var iframeSrc = res.select("iframe").attr("src")
        if (iframeSrc.isEmpty()) iframeSrc = res.select("iframe").attr("data-src")
        
        if (iframeSrc.startsWith("//")) iframeSrc = "https:$iframeSrc"

        Log.d("LayarKaca-Router", "Routing $url to $iframeSrc")

        // Deteksi Server berdasarkan keyword di URL iframe
        when {
            // Turbovid / Emturbovid
            iframeSrc.contains("turbovid") || iframeSrc.contains("emturbovid") || url.contains("/turbovip/") -> {
                Turbovidhls().getUrl(url, referer, subtitleCallback, callback)
            }
            // F16px / UniversalVIP
            iframeSrc.contains("f16px") || url.contains("/cast/") -> {
                F16px().getUrl(url, referer, subtitleCallback, callback)
            }
            // Hydrax / AbyssCDN / Short.icu
            iframeSrc.contains("short.icu") || iframeSrc.contains("abysscdn") || url.contains("/hydrax/") -> {
                Hydrax().getUrl(url, referer, subtitleCallback, callback)
            }
            // Hownetwork / P2P
            iframeSrc.contains("hownetwork") || url.contains("/p2p/") -> {
                // Hownetwork butuh URL iframe-nya langsung jika ada
                val targetUrl = if (iframeSrc.contains("hownetwork")) iframeSrc else url
                Hownetwork().getUrl(targetUrl, url, subtitleCallback, callback)
            }
            else -> {
                Log.d("LayarKaca-Router", "Unknown server or direct link: $iframeSrc")
            }
        }
    }
}

// ==========================================
// 2. SERVER: Hownetwork (API v2)
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
                    this.name,      // source
                    file,           // url
                    url,            // referer
                    null,           // quality (null agar tidak error tipe data)
                    m3u8Headers     // headers
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

// ==========================================
// 3. SERVER: Turbovid (Redirect Handler)
// ==========================================
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
        
        // 1. Unwrap PlayerIframe
        if (targetUrl.contains("playeriframe")) {
             val doc = app.get(targetUrl, referer = referer).document
             var src = doc.select("iframe").attr("src")
             if (src.isEmpty()) src = doc.select("iframe").attr("data-src")
             
             if (src.isNotBlank()) {
                 targetUrl = if (src.startsWith("//")) "https:$src" else src
             }
        }

        // 2. Unwrap Emturbovid (Redirect ke Turbovid)
        if (targetUrl.contains("emturbovid")) {
            val response = app.get(targetUrl, referer = "https://playeriframe.sbs/")
            targetUrl = response.url // Ambil URL akhir setelah redirect
        }

        // 3. Parsing Halaman Turbovid
        if (targetUrl.contains("turbovid")) {
            val doc = app.get(targetUrl, referer = "https://playeriframe.sbs/").document
            val script = doc.select("script").find { it.data().contains("master.m3u8") }?.data()
            
            if (script != null) {
                // Regex untuk menangkap link m3u8
                val m3u8Regex = Regex("""["'](https?://[^"']+/master\.m3u8[^"']*)["']""")
                val match = m3u8Regex.find(script)
                val m3u8Url = match?.groupValues?.get(1)
                
                if (m3u8Url != null) {
                    M3u8Helper.generateM3u8(
                        this.name,
                        m3u8Url,
                        targetUrl, // Referer penting: halaman turbovid itu sendiri
                        null,
                        mapOf(
                            "Origin" to "https://turbovidhls.com",
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        )
                    ).forEach(callback)
                }
            } else {
                // Fallback: Coba cari link file lain
                val fallbackRegex = Regex("""file:\s*["']([^"']+)["']""")
                val fallbackMatch = fallbackRegex.find(doc.html())?.groupValues?.get(1)
                
                if (fallbackMatch != null) {
                     M3u8Helper.generateM3u8(
                        this.name,
                        fallbackMatch,
                        targetUrl,
                        null,
                        mapOf("Origin" to "https://turbovidhls.com")
                    ).forEach(callback)
                }
            }
        }
    }
}

// ==========================================
// 4. SERVER: F16px (Hidden API)
// ==========================================
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

                    M3u8Helper.generateM3u8(
                        this.name,
                        file,
                        "$mainUrl/",
                        null,
                        videoHeaders
                    ).forEach(callback)
                }
            }
        } catch (e: Exception) {
            Log.e("LayarKaca-F16", "Error: ${e.message}")
        }
    }
}

// ==========================================
// 5. SERVER: Hydrax / AbyssCDN
// ==========================================
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
             var src = doc.select("iframe").attr("src")
             if (src.isNotBlank()) {
                 targetUrl = if (src.startsWith("//")) "https:$src" else src
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
                    M3u8Helper.generateM3u8(
                        this.name, 
                        videoUrl, 
                        "https://abysscdn.com/", 
                        null, 
                        mapOf("Origin" to "https://abysscdn.com")
                    ).forEach(callback)
                } else {
                    // Gunakan .apply untuk mengisi properti
                    callback(
                        newExtractorLink(
                            this.name, 
                            this.name, 
                            videoUrl, 
                            INFER_TYPE
                        ).apply {
                            this.referer = "https://abysscdn.com/"
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
        }
    }
}

// ==========================================
// 6. SERVER SIMPLE (Filesim)
// ==========================================
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
