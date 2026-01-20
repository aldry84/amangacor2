package com.JavHey

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile // Import Wajib!

class LeleBakarExtractor : ExtractorApi() {
    override val name = "LeleBakar"
    override val mainUrl = "https://lelebakar.xyz"
    override val requiresReferer = true 

    // Signature yang benar sesuai log error
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
                "Referer" to url, 
                "Origin" to "https://lelebakar.xyz",
                "Accept" to "*/*",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin"
            )

            val response = app.get(url, headers = headers).text
            
            // Regex mencari file m3u8
            val m3u8Regex = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
            val match = m3u8Regex.find(response)
            
            match?.groupValues?.get(1)?.let { m3u8Url ->
                callback.invoke(
                    ExtractorLink(
                        name,
                        name,
                        m3u8Url,
                        url, 
                        Qualities.Unknown.value,
                        true, // isM3u8
                        headers = headers 
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
