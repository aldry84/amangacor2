package com.phisher98

import com.lagradost.cloudstream3.base64DecodeArray
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.jetbrains.annotations.NotNull

object SubDecryptor {
    private const val AES_TRANSFORMATION = "AES/CBC/PKCS5Padding"
    
    private val KEYS = listOf(
        "AmSmZVcH93UQUezi",
        "8056483646328763",
        "sWODXX04QRTkHdlZ"
    )

    private val IVS = listOf(
        intArrayOf(1382367819, 1465333859, 1902406224, 1164854838),
        intArrayOf(909653298, 909193779, 925905208, 892483379),
        intArrayOf(946894696, 1634749029, 1127508082, 1396271183)
    )

    fun decrypt(encryptedB64: String): String? {
        val encryptedBytes = try {
            base64DecodeArray(encryptedB64)
        } catch (e: Exception) {
            return null
        }

        for (i in KEYS.indices) {
            try {
                val keySpec = SecretKeySpec(KEYS[i].toByteArray(Charsets.UTF_8), "AES")
                val ivSpec = IvParameterSpec(IVS[i].toByteArray())
                
                val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                
                return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
            } catch (ex: Exception) {
                // Lanjut ke pasangan key/IV berikutnya
                continue
            }
        }
        return null // Kembalikan null jika semua gagal agar bisa ditangani interceptor
    }

    private fun IntArray.toByteArray(): ByteArray {
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
