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
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to "https://emturbovid.com/"
        )

        try {
            // 1. Download halaman embed
            val response = app.get(url, headers = headers).text
            
            // 2. REGEX SAPU JAGAT (BRUTE FORCE)
            // Mencari string apapun yang:
            // - Dimulai dengan http atau https
            // - Diakhiri dengan .m3u8
            // - Mengizinkan karakter backslash (\) untuk antisipasi format JSON escaped
            val regexBroad = """(https?://[a-zA-Z0-9/\\._-]+\.m3u8)""".toRegex()
            
            // Cari semua kemungkinan match
            val matches = regexBroad.findAll(response)
            
            // Ambil match pertama yang valid
            var m3u8Url = matches.map { it.value }.firstOrNull()

            if (m3u8Url != null) {
                // PENTING: Bersihkan URL dari backslash (jika formatnya https:\/\/...)
                m3u8Url = m3u8Url.replace("\\", "")

                // 3. Kirim ke Turbovidhls
                // Parameter ke-2 (referer) kita isi dengan URL Embed asli ($url)
                // Ini KUNCI agar tidak error 403 / 3001
                super.getUrl(m3u8Url, url, subtitleCallback, callback)
            } else {
                // Jika masih gagal, mungkin diproteksi JS Packed.
                // Saat ini kita skip dulu agar tidak crash/error.
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

// --- CLASS BASE TURBOVID (LOGIKA PLAYER & HEADER) ---
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
        // Tentukan domain base
        val baseDomain = if (url.contains("emturbovid")) "https://emturbovid.com" else "https://turbovidthis.com"
        
        // GUNAKAN REFERER YANG DIKIRIM DARI PARAMETER (Link Embed)
        // Jika null, fallback ke domain base
        val safeReferer = referer ?: "$baseDomain/"

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Origin" to baseDomain, // Origin biasanya cuma domain (tanpa path)
            "Referer" to safeReferer // Referer harus URL halaman embed
        )

        try {
            val response = app.get(url, headers = headers)
            val responseText = response.text

            // 1. Cek apakah ini Nested M3U8 (Master Playlist berisi resolusi lain)
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
                            referer = safeReferer,
                            isM3u8 = true, 
                            headers = headers
                        )
                    )
                    return
                }
            }

            // 2. Fallback: Link Langsung (Single Stream)
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

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- FUNGSI JEMBATAN (REFLECTION) ---
    // Menggunakan Reflection untuk bypass validasi Constructor library
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

        // Konversi Boolean ke Enum (Wajib untuk Cloudstream versi baru)
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
