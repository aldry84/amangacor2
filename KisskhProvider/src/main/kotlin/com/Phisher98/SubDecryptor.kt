package com.phisher98

import com.lagradost.cloudstream3.base64DecodeArray
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object SubDecryptor {
    [span_4](start_span)[span_5](start_span)// Kunci dan IV dari sumber asli[span_4](end_span)[span_5](end_span)
    private val KEYS = listOf("AmSmZVcH93UQUezi", "8056483646328763", "sWODXX04QRTkHdlZ")
    private val IVS = listOf(
        intArrayOf(1382367819, 1465333859, 1902406224, 1164854838),
        intArrayOf(909653298, 909193779, 925905208, 892483379),
        intArrayOf(946894696, 1634749029, 1127508082, 1396271183)
    )

    fun decrypt(encryptedB64: String): String? {
        val encryptedBytes = try { 
            base64DecodeArray(encryptedB64) 
        } catch (e: Exception) { return null }

        for (i in KEYS.indices) {
            try {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                val keySpec = SecretKeySpec(KEYS[i].toByteArray(), "AES")
                val ivSpec = IvParameterSpec(IVS[i].toByteArr())
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
            } catch (e: Exception) { continue }
        }
        return null
    }

    private fun IntArray.toByteArr(): ByteArray {
        val bytes = ByteArray(size * 4)
        forEachIndexed { index, value ->
            bytes[index * 4] = (value shr 24).toByte()
            bytes[index * 4 + 1] = (value shr 16).toByte()
            bytes[index * 4 + 2] = (value shr 8).toByte()
            bytes[index * 4 + 3] = value.toByte()
        }
        return bytes
    }
}
