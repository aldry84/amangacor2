package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType 
import com.lagradost.cloudstream3.extractors.Filesim

// --- CLASS UTAMA UNTUK EMTURBOVID (REGEX YANG LEBIH KUAT) ---
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
            
            // 2. STRATEGI REGEX BARU (LEBIH AGRESIF)
            // Mencari URL apapun yang berakhiran .m3u8 di dalam tanda kutip
            var m3u8Url: String? = null
            
            // Pola 1: Mencari di dalam variabel javascript sources: [{ file: "..." }]
            val regex1 = """file:\s*["']([^"']+\.m3u8[^"']*)["']""".toRegex()
            m3u8Url = regex1.find(response)?.groupValues?.get(1)

            // Pola 2: Jika Pola 1 gagal, cari link m3u8 apa saja yang ada di halaman
            if (m3u8Url == null) {
                val regex2 = """["'](https?://[^"']+\.m3u8[^"']*)["']""".toRegex()
                m3u8Url = regex2.find(response)?.groupValues?.get(1)
            }

            if (m3u8Url != null) {
                // 3. SUKSES! Serahkan ke Turbovidhls untuk diputar
                super.getUrl(m3u8Url, referer, subtitleCallback, callback)
            } else {
                // 4. GAGAL! Jangan kirim URL Embed ke player (ini penyebab error 3001/3002)
                // Kita diam saja (return) agar Cloudstream mencari source lain, 
                // atau log error jika perlu.
                System.out.println("EmturboCustom: Gagal menemukan m3u8 di $url")
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

// --- CLASS BASE TURBOVID ---
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
        // Logika Header yang diperbaiki
        val currentDomain = if (url.contains("emturbovid")) "https://emturbovid.com/" else "https://turbovidthis.com/"
        
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Origin" to currentDomain.trimEnd('/'), // Origin biasanya tanpa slash di akhir
            "Referer" to currentDomain // Referer biasanya butuh slash (tapi amannya domain base aja)
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
    private fun createSafeExtractorLink(
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
