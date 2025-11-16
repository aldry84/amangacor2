package com.Adicinemax21

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.Provider
import com.phisher98.StreamPlayExtractor
import com.phisher98.StreamPlay.LinkData

// Menggunakan alias LinkData dari StreamPlay untuk kompatibilitas
typealias StreamPlayLinkData = LinkData

@RequiresApi(Build.VERSION_CODES.O)
fun buildAdicinemax21Providers(): List<Provider> {
    val allProviders = com.phisher98.buildProviders()
    
    // Filter Provider: Hapus sumber Anime, dan sumber yang hanya relevan untuk torrent (uhdmovies)
    val filteredProviders = allProviders.filter { provider ->
        // Exclude Anime
        !provider.id.equals("anime", ignoreCase = true) && 
        // Exclude sources often used as torrent index/large files that we want to avoid
        !provider.id.equals("uhdmovies", ignoreCase = true) &&
        !provider.id.equals("4khdhub", ignoreCase = true) && // Juga cenderung ke large file/torrent
        !provider.id.equals("hdhub4u", ignoreCase = true) && // Juga cenderung ke large file/torrent
        !provider.id.equals("rogmovies", ignoreCase = true) && // Juga cenderung ke large file/torrent
        !provider.id.equals("dotmovies", ignoreCase = true) // Juga cenderung ke large file/torrent
    }

    return filteredProviders.map { originalProvider ->
        // Memastikan fungsi invoke menggunakan tipe data LinkData dari StreamPlay
        Provider(
            id = originalProvider.id,
            name = originalProvider.name,
            invoke = { res: StreamPlayLinkData, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit, token: String, dahmerMoviesAPI: String ->
                originalProvider.invoke(res, subtitleCallback, callback, token, dahmerMoviesAPI)
            }
        )
    }
}
