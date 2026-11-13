package com.example.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CryptoManagerTest {

    @Test
    fun encryptAndDecrypt_roundTripsTextAndBytesWithKeyCache() {
        CryptoManager.clearKeyCache()
        val passkey = "PodAllyPasskey2026!"
        val plainText = "TIT:Costco Grocery List||CON:- [ ] Milk\n- [ ] Avocados"

        val cipher1 = CryptoManager.encrypt(plainText, passkey)
        val cipher2 = CryptoManager.encrypt(plainText, passkey)
        // Random IV ensures distinct ciphertexts even for identical plaintext
        assertNotEquals(cipher1, cipher2)

        assertEquals(plainText, CryptoManager.decrypt(cipher1, passkey))
        assertEquals(plainText, CryptoManager.decrypt(cipher2, passkey))

        // Binary photo/receipt byte round-trip
        val rawBytes = byteArrayOf(1, 2, 3, 42, -99, 127, 0, 55)
        val encryptedBytes = CryptoManager.encryptBytes(rawBytes, passkey)
        val decryptedBytes = CryptoManager.decryptBytes(encryptedBytes, passkey)
        assertArrayEquals(rawBytes, decryptedBytes)

        // Wrong passkey fails cleanly
        assertNull(CryptoManager.decryptBytes(encryptedBytes, "WrongKey"))
        assertEquals("[Encrypted - Passkey mismatch]", CryptoManager.decrypt(cipher1, "WrongKey"))
    }
}
