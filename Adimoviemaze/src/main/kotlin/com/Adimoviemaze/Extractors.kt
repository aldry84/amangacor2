package com.Adimoviemaze

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.Qualities

/**
 * Extractor khusus untuk StreamWish/HGLink.
 */
class StreamWishCustom : StreamWishExtractor() {
    override val mainUrl = "https://hglink.to" 
    override val name = "HGLink"
}

/**
 * Extractor khusus untuk StreamSB.
 * DIPERBAIKI: Menggunakan 'override var' karena properti di StreamSB adalah 'var'.
 */
class StreamSBCustom : StreamSB() {
    override var mainUrl = "https://sblongvu.com" 
    override var name = "StreamSB"
}

/**
 * Extractor generik sebagai fallback atau untuk player kustom.
 */
class MazePlayerExtractor : ExtractorApi() {
    override val name = "MazePlayer"
    override val mainUrl = "https://moviemaze.cc" 
    override val requiresReferer = true
    
    // Default quality jika tidak dapat ditentukan
    private val quality = Qualities.Unknown.value

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Biarkan kosong, delegasikan ke loadExtractor.
        // Anda dapat menambahkan logika khusus di sini jika ada tautan yang unik dan tidak teratasi.
    }
}
