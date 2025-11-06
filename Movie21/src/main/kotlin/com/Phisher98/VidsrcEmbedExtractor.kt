// File: com.Phisher98.VidsrcEmbedExtractor.kt
package com.Phisher98 // PERBAIKAN: Menggunakan package yang benar sesuai error log

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorApi // PERBAIKAN: Menggunakan import ExtractorApi yang benar
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

class VidsrcEmbedExtractor : ExtractorApi() { // PERBAIKAN: class ExtractorApi() yang benar
    override val name = "VidsrcEmbed"
    override val mainUrl = "https://vidsrc-embed.ru" 
    override val requiresReferer = true // PERBAIKAN: menggunakan requiresReferer yang benar
    
    // PERBAIKAN: Menggunakan fungsi getLinks yang benar (bukan 'get' atau 'getLinks')
    override suspend fun getLinks(
        url: String, // URL embed penuh
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val page = app.get(url, headers = mapOf("referer" to referer))

        // Cari iframe SRC (contoh disesuaikan agar lebih generik)
        val iframeSrc = page.document.select("iframe[src*=\"embed\"]").attr("src")

        if (iframeSrc.isNullOrBlank()) {
            return false
        }
        
        // Panggil loadExtractor lagi untuk URL host video yang sebenarnya
        loadExtractor(iframeSrc, url, callback) 
        
        return true
    }
}
