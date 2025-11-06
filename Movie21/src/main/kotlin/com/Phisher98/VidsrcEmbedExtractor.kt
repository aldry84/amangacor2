package com.Phisher98

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.app // KRITIS: Diperlukan untuk memanggil app.get()

class VidsrcEmbedExtractor : ExtractorApi() {
    override val name = "VidsrcEmbed"
    override val mainUrl = "https://vidsrc-embed.ru" 
    override val requiresReferer = true
    
    // PERBAIKAN: Fungsi getLinks yang di-override dengan benar
    override suspend fun getLinks(
        url: String, 
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Mendapatkan halaman embed
        val page = app.get(url, headers = mapOf("referer" to referer)) 

        // Mencari URL dari iframe player yang biasanya menunjuk ke host video
        val iframeSrc = page.document.select("iframe[src*=\"embed\"]").attr("src")

        if (iframeSrc.isNullOrBlank()) {
            return false
        }
        
        // Memanggil loadExtractor lagi dengan URL host internal yang ditemukan
        loadExtractor(iframeSrc, url, callback) 
        
        return true
    }
}
