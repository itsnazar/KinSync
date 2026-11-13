package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecurePasskeyStore {
    private const val PREFS_NAME = "podally_prefs"
    private const val KEY_WRAPPED_PASSKEY = "wrapped_family_passkey"
    private const val KEY_LEGACY_PASSKEY = "family_passkey"
    private const val KEY_ACTIVE_FAMILY_ID = "active_family_id"
    private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
    private const val KEYSTORE_ALIAS = "podally_master_key"
    private const val DEFAULT_PASSKEY = "PodAllyFamilyKey2026"

    private fun getOrCreateKeyStoreSecret(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(spec)
            generator.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    fun loadPasskey(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wrapped = prefs.getString(KEY_WRAPPED_PASSKEY, null)
        if (!wrapped.isNullOrBlank()) {
            val unwrapped = decryptWithKeystore(wrapped)
            if (!unwrapped.isNullOrBlank()) return unwrapped
        }
        val legacy = prefs.getString(KEY_LEGACY_PASSKEY, null)
        if (!legacy.isNullOrBlank()) {
            savePasskey(context, legacy)
            return legacy
        }
        return DEFAULT_PASSKEY
    }

    fun savePasskey(context: Context, passkey: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wrapped = encryptWithKeystore(passkey)
        if (wrapped != null) {
            prefs.edit()
                .putString(KEY_WRAPPED_PASSKEY, wrapped)
                .remove(KEY_LEGACY_PASSKEY)
                .commit()
        } else {
            prefs.edit().putString(KEY_LEGACY_PASSKEY, passkey).commit()
        }
    }

    fun saveActiveFamilyId(context: Context, familyId: String) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_FAMILY_ID, familyId)
            .apply()
    }

    fun loadActiveFamilyId(context: Context): String {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_FAMILY_ID, "") ?: ""
    }

    fun isBiometricLockEnabled(context: Context): Boolean {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BIOMETRIC_LOCK, false)
    }

    fun setBiometricLockEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BIOMETRIC_LOCK, enabled)
            .apply()
    }

    private fun encryptWithKeystore(plain: String): String? {
        return try {
            val key = getOrCreateKeyStoreSecret() ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val encrypted = Base64.encodeToString(
                cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8)),
                Base64.NO_WRAP
            )
            "$iv:$encrypted"
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptWithKeystore(wrapped: String): String? {
        return try {
            val parts = wrapped.split(":")
            if (parts.size != 2) return null
            val key = getOrCreateKeyStoreSecret() ?: return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
