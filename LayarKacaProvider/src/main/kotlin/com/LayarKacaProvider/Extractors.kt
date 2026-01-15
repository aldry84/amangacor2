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
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.net.URI

// --- Extractor Helper Classes ---

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
        val id = url.substringAfter("id=").substringBefore("&")
        val apiEndpoint = "$mainUrl/api.php?id=$id"
        invokeApi(url, apiEndpoint, id, referer, callback)
    }

    protected suspend fun invokeApi(
        originalUrl: String,
        apiEndpoint: String,
        id: String, 
        referer: String?, 
        callback: (ExtractorLink) -> Unit
    ) {
        val reqReferer = "$mainUrl/video.php?id=$id"
        
        val headers = mapOf(
            "Origin" to mainUrl,
            "Referer" to reqReferer,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "X-Requested-With" to "XMLHttpRequest"
        )

        val bodyData = mapOf(
            "r" to "https://playeriframe.sbs/",
            "d" to mainUrl.substringAfter("://"),
        )

        try {
            val response = app.post(
                apiEndpoint,
                headers = headers,
                data = bodyData,
                referer = reqReferer
            ).text

            val json = JSONObject(response)
            val file = json.optString("file")
            
            if (file.isNotBlank() && file != "null") {
                M3u8Helper.generateM3u8(
                    source = this.name,
                    streamUrl = file,
                    referer = reqReferer,
                    headers = headers
                ).forEach(callback)
            }
        } catch (e: Exception) {
            Log.e("Hownetwork", "Error: ${e.message}")
        }
    }
}

class Cloudhownetwork : Hownetwork() {
    override val name = "CloudHownetwork"
    override val mainUrl = "https://cloud.hownetwork.xyz"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfter("id=").substringBefore("&")
        val apiEndpoint = "$mainUrl/api2.php?id=$id"
        invokeApi(url, apiEndpoint, id, referer, callback)
    }
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Furher2 : Filesim() {
    override val name = "Furher 2"
    override var mainUrl = "723qrh1p.fun"
}

class Turbovidhls : ExtractorApi() {
    override val name = "Turbovidhls"
    override val mainUrl = "https://turbovidhls.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Origin" to "https://turbovidhls.com",
            "Sec-Ch-Ua-Mobile" to "?0",
            "Sec-Ch-Ua-Platform" to "\"Linux\""
        )

        if (url.contains(".m3u8")) {
            M3u8Helper.generateM3u8(
                name,
                url,
                referer ?: "https://turbovidhls.com/",
                headers = headers
            ).forEach(callback)
        } else {
            try {
                val res = app.get(url, headers = headers).text
                val m3u8Link = Regex("""["']([^"']+\.m3u8[^"']*)["']""").find(res)?.groupValues?.get(1)
                
                if (!m3u8Link.isNullOrEmpty()) {
                    M3u8Helper.generateM3u8(
                        name,
                        m3u8Link,
                        referer ?: url,
                        headers = headers
                    ).forEach(callback)
                }
            } catch (e: Exception) {
                Log.e("Turbovidhls", "Error parsing embed: ${e.message}")
            }
        }
    }
}

class F16px : ExtractorApi() {
    override val name = "F16px"
    override val mainUrl = "https://f16px.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val response = app.get(url, referer = referer).text
            val packed = getPacked(response)
            
            if (packed != null) {
                val m3u8Regex = Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""")
                val mp4Regex = Regex("""file:\s*["']([^"']+\.mp4[^"']*)["']""")
                
                val m3u8Match = m3u8Regex.find(packed)?.groupValues?.get(1)
                val mp4Match = mp4Regex.find(packed)?.groupValues?.get(1)

                if (m3u8Match != null) {
                    M3u8Helper.generateM3u8(
                        name,
                        m3u8Match,
                        referer = url,
                        headers = mapOf("Referer" to url)
                    ).forEach(callback)
                } else if (mp4Match != null) {
                    // FIX: Gunakan .apply {} karena parameter referer/quality tidak ada di constructor
                    callback(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = mp4Match,
                            type = INFER_TYPE
                        ).apply {
                            this.referer = url
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            } else {
                val rawLink = Regex("""["']([^"']+\.m3u8[^"']*)["']""").find(response)?.groupValues?.get(1)
                if (rawLink != null) {
                    M3u8Helper.generateM3u8(name, rawLink, url).forEach(callback)
                }
            }
        } catch (e: Exception) {
            Log.e("F16px", "Error: ${e.message}")
        }
    }
}

class AbyssCdn : ExtractorApi() {
    override val name = "Hydrax"
    override val mainUrl = "https://abysscdn.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val res = app.get(url, referer = referer).text
            
            val packed = getPacked(res)
            val contentToSearch = packed ?: res
            
            val linkRegex = Regex("""(?:file|source|src)\s*:\s*["']([^"']+)["']""")
            val match = linkRegex.find(contentToSearch)?.groupValues?.get(1)

            if (!match.isNullOrEmpty() && match.startsWith("http")) {
                val cleanUrl = match.replace("\\/", "/")
                
                if (cleanUrl.contains(".m3u8")) {
                     M3u8Helper.generateM3u8(
                        name,
                        cleanUrl,
                        referer = url,
                        headers = mapOf("Origin" to getBaseDomain(url))
                    ).forEach(callback)
                } else {
                    // FIX: Gunakan .apply {} karena parameter referer/quality tidak ada di constructor
                    callback(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = cleanUrl,
                            type = INFER_TYPE
                        ).apply {
                            this.referer = url
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            } else {
                Log.d("AbyssCdn", "No link found in unpacked content")
            }

        } catch (e: Exception) {
            Log.e("AbyssCdn", "Error: ${e.message}")
        }
    }

    private fun getBaseDomain(url: String): String {
        return try {
            val uri = URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            mainUrl
        }
    }
}
