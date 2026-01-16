package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
// PENTING: Import ini wajib ada untuk mencegah crash "IllegalArgumentException"
import com.lagradost.cloudstream3.utils.ExtractorLinkType 
import com.lagradost.cloudstream3.extractors.Filesim

// --- CLASS UTAMA UNTUK EMTURBOVID ---
// Menggunakan logika scraping untuk mencari file m3u8 asli di dalam HTML
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
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to "https://emturbovid.com/"
        )

        try {
            // 1. Download source code halaman embed
            val response = app.get(url, headers = headers).text
            
            // 2. Cari link .m3u8 menggunakan Regex
            // Pola 1: file: "https://..."
            var m3u8Url = """file:\s*["']([^"']+\.m3u8[^"']*)["']""".toRegex()
                .find(response)?.groupValues?.get(1)

            // Pola 2 (Fallback): Cari string apapun yang berakhiran .m3u8
            if (m3u8Url == null) {
                 m3u8Url = """["'](https?://[^"']+\.m3u8[^"']*)["']""".toRegex()
                    .find(response)?.groupValues?.get(1)
            }

            if (m3u8Url != null) {
                // 3. Jika link asli ditemukan, serahkan ke Turbovidhls untuk diputar
                // super.getUrl akan menangani header Origin dan Reflection
                super.getUrl(m3u8Url, referer, subtitleCallback, callback)
            } else {
                // Jika gagal scraping (mungkin diproteksi packed JS), coba metode lama
                // tapi kemungkinan besar akan error 3002 jika server mengirim HTML
                super.getUrl(url, referer, subtitleCallback, callback)
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

// --- CLASS BASE TURBOVID & LOGIKA UTAMA ---
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
        // Setup Header yang Valid
        val currentDomain = if (url.contains("emturbovid")) "https://emturbovid.com/" else "https://turbovidthis.com/"
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Origin" to currentDomain.trimEnd('/'),
            "Referer" to currentDomain
        )

        try {
            // Cek konten M3U8 apakah nested (playlist di dalam playlist)
            val response = app.get(url, headers = headers)
            val responseText = response.text

            if (responseText.contains("#EXT-X-STREAM-INF") && responseText.contains("http")) {
                val nextUrl = responseText.split('\n').firstOrNull { 
                    it.trim().startsWith("http") && it.contains(".m3u8") 
                }?.trim()

                if (!nextUrl.isNullOrEmpty()) {
                    callback(
                        createSafeExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = nextUrl,
                            referer = currentDomain,
                            isM3u8 = true, 
                            headers = headers
                        )
                    )
                    return
                }
            }

            // Fallback ke URL input jika bukan nested
            callback(
                createSafeExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = url,
                    referer = currentDomain,
                    isM3u8 = true,
                    headers = headers
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- FUNGSI JEMBATAN (REFLECTION) ---
    // Memaksa pembuatan objek ExtractorLink meskipun Constructor di-block compiler
    private fun createSafeExtractorLink(
        source: String,
        name: String,
        url: String,
        referer: String,
        isM3u8: Boolean,
        headers: Map<String, String>
    ): ExtractorLink {
        val clazz = ExtractorLink::class.java
        
        // Cari constructor utama (yang parameternya paling banyak)
        val constructor = clazz.constructors.find { it.parameterCount >= 6 } 
            ?: throw RuntimeException("Constructor ExtractorLink tidak ditemukan!")

        // PERBAIKAN PENTING:
        // Konversi Boolean (isM3u8) menjadi ExtractorLinkType (Enum)
        // Ini mencegah error "IllegalArgumentException" saat runtime
        val type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO

        return constructor.newInstance(
            source,
            name,
            url,
            referer,
            Qualities.Unknown.value, // quality
            type,                    // type (Enum) - Bukan Boolean!
            headers,                 // headers
            null                     // extractorData
        ) as ExtractorLink
    }
}
