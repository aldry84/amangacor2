package com.LayarKacaProvider

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

open class EmturbovidExtractor : ExtractorApi() {
    override var name = "Emturbovid"
    override var mainUrl = "https://emturbovid.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        // Pastikan ada referer, kalau null pakai mainUrl
        val finalReferer = referer ?: "$mainUrl/"
        
        // Request ke halaman embed
        val response = app.get(url, referer = finalReferer)
        
        // Cari script yang berisi variabel 'urlPlay'
        val playerScript = response.document.selectXpath("//script[contains(text(),'var urlPlay')]").html()

        val sources = mutableListOf<ExtractorLink>()
        
        if (playerScript.isNotBlank()) {
            // Extract URL m3u8 dari script
            val m3u8Url = playerScript.substringAfter("var urlPlay = '").substringBefore("'")

            // --- UPGRADE: Gunakan M3u8Helper ---
            // Fungsi ini otomatis:
            // 1. Generate resolusi (360p, 720p, 1080p).
            // 2. Memastikan header Referer terbawa ke setiap chunk video agar tidak putus (Error 3001).
            M3u8Helper.generateM3u8(
                source = name,
                streamUrl = m3u8Url,
                referer = finalReferer,
                headers = mapOf("Referer" to finalReferer)
            ).forEach { link ->
                sources.add(link)
            }
        }
        return sources
    }
}
