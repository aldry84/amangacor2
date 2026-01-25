package com.phisher98

import com.lagradost.cloudstream3.base64DecodeArray
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

[span_2](start_span)private const val KEY = "AmSmZVcH93UQUezi"[span_2](end_span)
[span_3](start_span)private const val KEY2 = "8056483646328763"[span_3](end_span)
[span_4](start_span)private const val KEY3 = "sWODXX04QRTkHdlZ"[span_4](end_span)

[span_5](start_span)private val IV = intArrayOf(1382367819, 1465333859, 1902406224, 1164854838)[span_5](end_span)
[span_6](start_span)private val IV2 = intArrayOf(909653298, 909193779, 925905208, 892483379)[span_6](end_span)
[span_7](start_span)private val IV3 = intArrayOf(946894696, 1634749029, 1127508082, 1396271183)[span_7](end_span)

fun decrypt(encryptedB64: String): String {
    val keyIvPairs = listOf(
        Pair(KEY.toByteArray(Charsets.UTF_8), IV.toByteArray()),
        Pair(KEY2.toByteArray(Charsets.UTF_8), IV2.toByteArray()),
        Pair(KEY3.toByteArray(Charsets.UTF_8), IV3.toByteArray())
    [span_8](start_span))

    val encryptedBytes = base64DecodeArray(encryptedB64)[span_8](end_span)

    [span_9](start_span)for ((keyBytes, ivBytes) in keyIvPairs) {[span_9](end_span)
        try {
            return decryptWithKeyIv(keyBytes, ivBytes, encryptedBytes)
        } catch (ex: Exception) {
            // Lanjut ke pasangan kunci berikutnya jika gagal
        }
    }
    return "Decryption failed"
}

private fun decryptWithKeyIv(keyBytes: ByteArray, ivBytes: ByteArray, encryptedBytes: ByteArray): String {
    [span_10](start_span)val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")[span_10](end_span)
    [span_11](start_span)cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))[span_11](end_span)
    [span_12](start_span)return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)[span_12](end_span)
}

private fun IntArray.toByteArray(): ByteArray {
    return ByteArray(size * 4).also { bytes ->
        forEachIndexed { index, value ->
            [span_13](start_span)bytes[index * 4] = (value shr 24).toByte()[span_13](end_span)
            [span_14](start_span)bytes[index * 4 + 1] = (value shr 16).toByte()[span_14](end_span)
            [span_15](start_span)bytes[index * 4 + 2] = (value shr 8).toByte()[span_15](end_span)
            [span_16](start_span)bytes[index * 4 + 3] = value.toByte()[span_16](end_span)
        }
    }
}
