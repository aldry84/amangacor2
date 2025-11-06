// File: com.Phisher98.VidsrcEmbedExtractor.kt
package com.Phisher98

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorApi // Pastikan ini di-import
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.app // PERBAIKAN KRITIS: Import 'app' untuk memanggil app.get()

class VidsrcEmbedExtractor : ExtractorApi() {
    override val name = "VidsrcEmbed"
    override val mainUrl = "https://vidsrc-embed.ru" 
    override val requiresReferer = true
    
    // PERBAIKAN: Mengganti nama fungsi agar sesuai dengan base class ExtractorApi
    override suspend fun getLinks(
        url: String, 
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // PERBAIKAN: app.get() kini teratasi berkat import com.lagradost.cloudstream3.app
        val page = app.get(url, headers = mapOf("referer" to referer)) 

        val iframeSrc = page.document.select("iframe[src*=\"embed\"]").attr("src")

        if (iframeSrc.isNullOrBlank()) {
            return false
        }
        
        // PERBAIKAN: Panggilan loadExtractor kini memiliki semua argumen yang benar
        loadExtractor(iframeSrc, url, callback) 
        
        return true
    }
}
