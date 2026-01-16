package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.extractors.Filesim

// --- CLASS UTAMA UNTUK EMTURBOVID ---
class EmturboCustom : Turbovidhls() {
    override val name = "Emturbovid"
    override val mainUrl = "https://emturbovid.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Header untuk scraping halaman
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to "https://emturbovid.com/"
        )

        try {
            // 1. Download halaman embed
            val response = app.get(url, headers = headers).text
            
            // 2. REGEX SAPU JAGAT (BRUTE FORCE) v2
            // Mencari string yang diawali http/https dan diakhiri .m3u8
            // Kita ambil yang paling panjang/kompleks karena biasanya itu Master Playlist
            val regexBroad = """(https?://[a-zA-Z0-9/\\._-]+\.m3u8)""".toRegex()
            
            val matches = regexBroad.findAll(response)
            var m3u8Url = matches.map { it.value }.firstOrNull()

            if (m3u8Url != null) {
                // Bersihkan backslash jika link diambil dari JSON
                m3u8Url = m3u8Url.replace("\\", "")

                // 3. Setup Header yang Benar untuk Video
                // Referer WAJIB URL Embed asli ($url) agar tidak error 3001
                val videoHeaders = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Accept" to "*/*",
                    "Origin" to "https://emturbovid.com",
                    "Referer" to url 
                )

                // 4. KIRIM LANGSUNG KE PLAYER!
                // Kita TIDAK LAGI membuka isi M3U8 secara manual.
                // Biarkan Cloudstream yang membukanya, supaya menu KUALITAS muncul.
                callback(
                    createSafeExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = m3u8Url,
                        referer = url,
                        isM3u8 = true, // Wajib true agar dianggap playlist
                        headers = videoHeaders
                    )
                )
            } 

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

// --- CLASS BASE ---
open class Turbovidhls : ExtractorApi() {
    override val name = "Turbovid"
    override val mainUrl = "https://turbovidhls.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val baseDomain = if (url.contains("emturbovid")) "https://emturbovid.com" else "https://turbovidthis.com"
        val safeReferer = referer ?: "$baseDomain/"

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Origin" to baseDomain,
            "Referer" to safeReferer
        )

        // Langsung kirim URL ke player tanpa cek nested m3u8
        callback(
            createSafeExtractorLink(
                source = this.name,
                name = this.name,
                url = url,
                referer = safeReferer,
                isM3u8 = true,
                headers = headers
            )
        )
    }

    // --- FUNGSI JEMBATAN (REFLECTION) ---
    // Bypass error compiler
    protected fun createSafeExtractorLink(
        source: String,
        name: String,
        url: String,
        referer: String,
        isM3u8: Boolean,
        headers: Map<String, String>
    ): ExtractorLink {
        val clazz = ExtractorLink::class.java
        val constructor = clazz.constructors.find { it.parameterCount >= 6 } 
            ?: throw RuntimeException("Constructor ExtractorLink tidak ditemukan!")

        val type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO

        return constructor.newInstance(
            source,
            name,
            url,
            referer,
            Qualities.Unknown.value,
            type,
            headers,
            null
        ) as ExtractorLink
    }
}
