package com.AdicinemaxNew

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.AdicinemaxNew.AdicinemaxExtractor.invokeVidsrccc
import com.AdicinemaxNew.AdicinemaxExtractor.invokeVidSrcXyz
import com.AdicinemaxNew.AdicinemaxExtractor.invokeXPrimeAPI
import com.AdicinemaxNew.AdicinemaxExtractor.invokeHubCloudGDFlix
import com.AdicinemaxNew.AdicinemaxExtractor.invokeTorrentio

// Data class dan buildProviders sama dengan StreamPlay, tetapi disederhanakan
data class Provider(
    val id: String,
    val name: String,
    val invoke: suspend (
        res: AdicinemaxNew.LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) -> Unit
)

fun buildProviders(): List<Provider> {
    return listOf(
        Provider("vidsrccc", "Vidsrccc") { res, _, callback ->
            invokeVidsrccc(res.id, res.season, res.episode, callback)
        },
        Provider("vidsrcxyz", "VidSrcXyz") { res, _, callback ->
            invokeVidSrcXyz(res.imdbId, res.season, res.episode, callback)
        },
        Provider("xprimeapi", "XPrime API") { res, subtitleCallback, callback ->
            invokeXPrimeAPI(res.title, res.year, res.imdbId, res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("hubcloud_gdflix", "HubCloud & GDFlix (Cloud)") { res, subtitleCallback, callback ->
            invokeHubCloudGDFlix(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("torrentio", "Torrentio (Magnet)") { res, _, callback ->
            invokeTorrentio(res.imdbId, res.season, res.episode, callback)
        },
    )
}
