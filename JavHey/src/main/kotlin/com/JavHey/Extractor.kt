package com.JavHey

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app

// --- SERVER 1: LELEBAKAR ---
class LeleBakarExtractor : ExtractorApi() {
    override val name = "LeleBakar"
    override val mainUrl = "https://lelebakar.xyz"
    override val requiresReferer = true 

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            // Header Super Spesifik (Wajib untuk LeleBakar)
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
                "Referer" to url, // Referer wajib ke halaman embed
                "Origin" to "https://lelebakar.xyz",
                "Accept" to "*/*",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin"
            )

            // 1. Ambil Source Code Halaman Embed
            val response = app.get(url, headers = headers).text
            
            // 2. Cari file .m3u8 (master atau index)
            // Regex mencari pola: "https://....m3u8"
            val m3u8Regex = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
            val match = m3u8Regex.find(response)
            
            match?.groupValues?.get(1)?.let { m3u8Url ->
                // 3. Kirim Link ke Player
                callback.invoke(
                    ExtractorLink(
                        name,
                        name,
                        m3u8Url,
                        url, // Referer dikirim ke player
                        Qualities.Unknown.value,
                        true, // isM3u8 = true
                        headers = headers // Header penting agar tidak 403
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// --- SERVER 2: BYSEBUHO (Placeholder untuk nanti) ---
// Nanti kita isi setelah dapat CURL datanya
class BySebuhoExtractor : ExtractorApi() {
    override val name = "BySebuho"
    override val mainUrl = "https://bysebuho.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Logic Server 2 akan ditaruh di sini
    }
}
