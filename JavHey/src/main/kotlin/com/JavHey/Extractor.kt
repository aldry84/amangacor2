package com.JavHey

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile

class LeleBakarExtractor : ExtractorApi() {
    override val name = "LeleBakar"
    override val mainUrl = "https://lelebakar.xyz"
    override val requiresReferer = true 

    // PERBAIKAN FINAL: Menambahkan @Suppress("DEPRECATION") di level fungsi
    // Ini akan memaksa compiler mengabaikan warning constructor ExtractorLink
    @Suppress("DEPRECATION")
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
                // Menggunakan Constructor Lama (Klasik)
                // Karena ada @Suppress di atas, error "Deprecated" tidak akan muncul lagi
                callback.invoke(
                    ExtractorLink(
                        name,           // source
                        name,           // name
                        m3u8Url,        // url
                        url,            // referer
                        Qualities.Unknown.value, // quality
                        true,           // isM3u8
                        headers,        // headers map
                        null            // extractorData
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
