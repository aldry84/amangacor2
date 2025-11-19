package com.AdiDrakor

import java.net.URI
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.lagradost.cloudstream3.utils.Qualities

object AdiDrakorUtils {
    object VidsrcHelper {
        fun encryptAesCbc(plainText: String, keyText: String): String {
            val sha256 = MessageDigest.getInstance("SHA-256")
            val keyBytes = sha256.digest(keyText.toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val iv = ByteArray(16) { 0 }
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            return base64UrlEncode(cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)))
        }
    }

    object VidrockHelper {
        private const val Ww = "x7k9mPqT2rWvY8zA5bC3nF6hJ2lK4mN9"
        fun encrypt(r: Int?, e: String, t: Int?, n: Int?): String {
            val s = if (e == "tv") "${r}_${t}_${n}" else r.toString()
            val keyBytes = Ww.toByteArray(Charsets.UTF_8)
            val ivBytes = Ww.substring(0, 16).toByteArray(Charsets.UTF_8)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(ivBytes)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            return base64UrlEncode(cipher.doFinal(s.toByteArray(Charsets.UTF_8)))
        }
    }
    
    fun generateWpKey(r: String, m: String): String {
        val rList = r.split("\\x").toTypedArray()
        var n = ""
        val decodedM = base64Decode(m.reversed() + "=".repeat((4 - m.length % 4) % 4))
        for (s in decodedM.split("|")) { n += "\\x" + rList[Integer.parseInt(s) + 1] }
        return n
    }

    fun getBaseUrl(url: String): String = URI(url).let { "${it.scheme}://${it.host}" }
}

fun base64Decode(input: String): String = String(android.util.Base64.decode(input, android.util.Base64.DEFAULT))
fun base64UrlEncode(input: ByteArray): String = android.util.Base64.encodeToString(input, android.util.Base64.DEFAULT).trim().replace("+", "-").replace("/", "_").replace("=", "")
fun String.createSlug(): String = this.filter { it.isLetterOrDigit() || it.isWhitespace() }.trim().replace("\\s+".toRegex(), "-").lowercase()
