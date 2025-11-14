package com.AsianDrama

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile

class SoraStreamExtractor {
    
    suspend fun invokeAllExtractors(
        data: SoraStreamData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Gunakan extractor dari SoraStream
        runCatching {
            SoraExtractor.invokeIdlix(
                title = data.title,
                year = data.year,
                season = data.season,
                episode = data.episode,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        }
        
        runCatching {
            SoraExtractor.invokeVidsrccc(
                tmdbId = data.tmdbId,
                imdbId = data.imdbId,
                season = data.season,
                episode = data.episode,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        }
        
        runCatching {
            SoraExtractor.invokeVidsrc(
                imdbId = data.imdbId,
                season = data.season,
                episode = data.episode,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        }
        
        // Tambahkan extractor lain sesuai kebutuhan
        runCatching {
            SoraExtractor.invokeVixsrc(
                tmdbId = data.tmdbId,
                season = data.season,
                episode = data.episode,
                callback = callback
            )
        }
        
        runCatching {
            SoraExtractor.invokeMapple(
                tmdbId = data.tmdbId,
                season = data.season,
                episode = data.episode,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        }
    }
}
