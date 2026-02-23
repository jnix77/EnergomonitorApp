package com.energomonitor.app.data.local

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    // A 256-bit hardcoded key (32 bytes)
    private const val ENCRYPTION_KEY = "EnergomonitorAppSecretKey123456!"
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    // Fixed IV for simplicity when obfuscating local credentials
    private const val FIXED_IV = "1234567890123456"

    fun encrypt(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return try {
            val keySpec = SecretKeySpec(ENCRYPTION_KEY.toByteArray(Charsets.UTF_8), ALGORITHM)
            val ivSpec = IvParameterSpec(FIXED_IV.toByteArray(Charsets.UTF_8))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedBytes = cipher.doFinal(input.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT).trim()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun decrypt(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return try {
            val keySpec = SecretKeySpec(ENCRYPTION_KEY.toByteArray(Charsets.UTF_8), ALGORITHM)
            val ivSpec = IvParameterSpec(FIXED_IV.toByteArray(Charsets.UTF_8))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decodedBytes = Base64.decode(input, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
