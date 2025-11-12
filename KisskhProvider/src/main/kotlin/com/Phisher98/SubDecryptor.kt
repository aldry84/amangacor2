package com.phisher98

import com.lagradost.cloudstream3.base64DecodeArray
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object SubDecryptor {
    private const val KEY = "AmSmZVcH93UQUezi"
    private const val KEY2 = "8056483646328763"

    private val IV = intArrayOf(1382367819, 1465333859, 1902406224, 1164854838)
    private val IV2 = intArrayOf(909653298, 909193779, 925905208, 892483379)

    fun decrypt(encryptedB64: String): String {
        val encryptedBytes = base64DecodeArray(encryptedB64)
        val pairs = listOf(KEY to IV, KEY2 to IV2)

        for ((keyStr, ivArr) in pairs) {
            try {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                val key = SecretKeySpec(keyStr.toByteArray(), "AES")
                val iv = IvParameterSpec(ivArr.toByteArray())
                cipher.init(Cipher.DECRYPT_MODE, key, iv)
                return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
            } catch (e: Exception) {
                continue
            }
        }
        return encryptedB64 // fallback: kembalikan aslinya
    }

    private fun IntArray.toByteArray(): ByteArray =
        ByteBuffer.allocate(size * 4).apply { asIntBuffer().put(this@toByteArray) }.array()
}
