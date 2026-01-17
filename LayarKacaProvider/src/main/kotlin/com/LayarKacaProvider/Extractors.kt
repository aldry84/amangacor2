package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.M3u8Helper

class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Turbovidhls : ExtractorApi() {
    override val name = "Turbovid"
    override val mainUrl = "https://turbovidhls.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // --- HASIL ANALISA CURL ---
        // Kita meniru Header dari 'Curl dan Respon2.txt' yang sukses (HTTP 200).
        // Error 1020 terjadi jika User-Agent salah atau Origin tidak ada.
        
        val headers = mapOf(
            "Host" to "turbovidhls.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.9",
            "Origin" to "https://turbovidhls.com",
            "Referer" to "https://turbovidhls.com/",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache"
        )

        // Menggunakan M3u8Helper dengan headers yang sudah "kebal" Cloudflare
        M3u8Helper.generateM3u8(
            source = this.name,
            streamUrl = url,
            referer = "https://turbovidhls.com/",
            headers = headers
        ).forEach(callback)
    }
}
