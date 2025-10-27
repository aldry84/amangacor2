package com.Adimoviemaze

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.VidCloud
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.fixUrl

/**
 * Extractor khusus untuk StreamWish/HGLink.
 */
class StreamWishCustom : StreamWishExtractor() {
    override val mainUrl = "https://hglink.to" 
    override val name = "HGLink"
}

/**
 * Extractor khusus untuk StreamSB.
 */
class StreamSBCustom : StreamSB() {
    override val mainUrl = "https://sblongvu.com" 
    override val name = "StreamSB"
}

/**
 * Extractor khusus untuk VidCloud/VCDN.
 */
class VidCloudCustom : VidCloud() {
    override val mainUrl = "https://vcdn.io"
    override val name = "VCDN"
}

/**
 * Extractor generik sebagai fallback atau untuk player kustom.
 */
class MazePlayerExtractor : ExtractorApi() {
    override val name = "MazePlayer"
    override val mainUrl = "https://moviemaze.cc" 
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Biarkan kosong agar ia mendelegasikan ke loadExtractor.
        // Hanya tambahkan logika di sini jika Anda tahu Moviemaze menggunakan
        // domain player yang sangat unik yang tidak di-handle oleh library.
    }
}
