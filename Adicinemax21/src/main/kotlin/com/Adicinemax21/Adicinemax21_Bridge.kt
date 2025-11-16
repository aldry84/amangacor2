package com.Adicinemax21

// --- IMPORT DARI PACKAGE ASAL (com.phisher98) ---
import com.phisher98.StreamPlayExtractor

/**
 * Object bridge untuk mengakses StreamPlayExtractor dari package com.phisher98.
 * Digunakan untuk memanggil invokeSubtitleAPI dan invokeWyZIESUBAPI di Adicinemax21.kt.
 */
object StreamPlayExtractorBridge {
    val instance = StreamPlayExtractor
}
