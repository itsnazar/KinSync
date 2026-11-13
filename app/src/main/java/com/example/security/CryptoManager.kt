package com.example.security

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH_BITS = 256
    private val DEFAULT_SALT = "PodAllyFamilySalt2026".toByteArray(StandardCharsets.UTF_8)
    private val LEGACY_SALT = "KinSyncFamilySalt2026".toByteArray(StandardCharsets.UTF_8)

    // Cache derived AES-256 keys in memory so PBKDF2 isn't recomputed on every item/recomposition
    private val keyCache = ConcurrentHashMap<String, SecretKeySpec>()
    private val secureRandom = SecureRandom()

    /**
     * Derives (or retrieves cached) 256-bit AES secret key using PBKDF2WithHmacSHA256.
     */
    private fun deriveKey(passkey: String, salt: ByteArray = DEFAULT_SALT): SecretKeySpec {
        val cacheKey = "$passkey|${salt.contentHashCode()}"
        keyCache[cacheKey]?.let { return it }

        val spec = PBEKeySpec(passkey.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretBytes = factory.generateSecret(spec).encoded
        val keySpec = SecretKeySpec(secretBytes, "AES")

        if (keyCache.size >= 4) {
            keyCache.clear()
        }
        keyCache[cacheKey] = keySpec
        return keySpec
    }

    fun clearKeyCache() {
        keyCache.clear()
    }

    /**
     * Encrypts plaintext using AES-256-GCM and returns a combined Base64 payload (IV:Ciphertext).
     */
    fun encrypt(plainText: String, passkey: String): String {
        if (plainText.isEmpty() || passkey.isEmpty()) return plainText
        return encryptBytes(plainText.toByteArray(StandardCharsets.UTF_8), passkey)
    }

    /**
     * Encrypts arbitrary binary bytes (e.g., compressed WebP receipts/photos) with AES-256-GCM.
     */
    fun encryptBytes(plainBytes: ByteArray, passkey: String): String {
        if (plainBytes.isEmpty() || passkey.isEmpty()) return ""

        val keySpec = deriveKey(passkey)
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val cipherBytes = cipher.doFinal(plainBytes)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)

        return "$ivBase64:$cipherBase64"
    }

    /**
     * Decrypts a combined Base64 payload (IV:Ciphertext) using AES-256-GCM.
     */
    fun decrypt(payload: String, passkey: String): String {
        if (payload.isEmpty() || passkey.isEmpty()) return payload
        val parts = payload.split(":")
        if (parts.size != 2) {
            return payload
        }
        val bytes = decryptBytes(payload, passkey) ?: return "[Encrypted - Passkey mismatch]"
        return String(bytes, StandardCharsets.UTF_8)
    }

    /**
     * Decrypts a combined Base64 payload (IV:Ciphertext) back into raw bytes.
     */
    fun decryptBytes(payload: String, passkey: String): ByteArray? {
        if (payload.isEmpty() || passkey.isEmpty()) return null
        val parts = payload.split(":")
        if (parts.size != 2) return null

        val iv = try {
            Base64.decode(parts[0], Base64.NO_WRAP)
        } catch (_: Exception) {
            return null
        }
        val cipherBytes = try {
            Base64.decode(parts[1], Base64.NO_WRAP)
        } catch (_: Exception) {
            return null
        }

        return decryptWithSalt(iv, cipherBytes, passkey, DEFAULT_SALT)
            ?: decryptWithSalt(iv, cipherBytes, passkey, LEGACY_SALT)
    }

    private fun decryptWithSalt(
        iv: ByteArray,
        cipherBytes: ByteArray,
        passkey: String,
        salt: ByteArray
    ): ByteArray? {
        return try {
            val keySpec = deriveKey(passkey, salt)
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.doFinal(cipherBytes)
        } catch (_: Exception) {
            null
        }
    }
}
