package com.Adicinemax21

// Ini adalah file bridge untuk memanggil fungsi-fungsi dari StreamPlayExtractor
// yang masih berada di package com.phisher98
import com.phisher98.StreamPlayExtractor

// Karena StreamPlayExtractor adalah object, kita bisa langsung menggunakannya.
// Tidak perlu isi di sini, cukup mendeklarasikan import agar bisa diakses oleh Adicinemax21.kt

/**
 * Object bridge untuk mengakses StreamPlayExtractor dari package com.phisher98.
 * Digunakan untuk memanggil invokeSubtitleAPI dan invokeWyZIESUBAPI di Adicinemax21.kt.
 */
object StreamPlayExtractorBridge {
    val instance = StreamPlayExtractor
}
