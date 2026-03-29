package com.example.gudgum_prod_flow.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts/decrypts cached credential hashes using Android Keystore (AES/GCM).
 * Hardware-backed on devices with a secure element (API 23+);
 * software-backed fallback on API 24 min SDK.
 */
@Singleton
class SecureCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun storeCredentials(userId: String, pinHash: String) {
        val key = getOrCreateKey(keyAlias(userId))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val encrypted = cipher.doFinal(pinHash.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        // Store IV length (1 byte) + IV + ciphertext
        val combined = byteArrayOf(iv.size.toByte()) + iv + encrypted

        prefs.edit()
            .putString(credKey(userId), Base64.encodeToString(combined, Base64.NO_WRAP))
            .putLong(timestampKey(userId), System.currentTimeMillis())
            .apply()
    }

    fun retrieveCredentials(userId: String): String? {
        val encoded = prefs.getString(credKey(userId), null) ?: return null
        val combined = Base64.decode(encoded, Base64.NO_WRAP)

        val ivLength = combined[0].toInt()
        val iv = combined.sliceArray(1..ivLength)
        val ciphertext = combined.sliceArray((ivLength + 1) until combined.size)

        val key = keyStore.getKey(keyAlias(userId), null) as? SecretKey ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun getCacheTimestamp(userId: String): Long {
        return prefs.getLong(timestampKey(userId), 0L)
    }

    fun isCacheValid(userId: String): Boolean {
        val timestamp = getCacheTimestamp(userId)
        if (timestamp == 0L) return false
        val ageMillis = System.currentTimeMillis() - timestamp
        return ageMillis <= CACHE_VALIDITY_MILLIS
    }

    fun clearCredentials(userId: String) {
        prefs.edit()
            .remove(credKey(userId))
            .remove(timestampKey(userId))
            .apply()
        keyStore.deleteEntry(keyAlias(userId))
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        keyStore.getKey(alias, null)?.let { return it as SecretKey }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }

    private fun keyAlias(userId: String) = "utpad_auth_key_$userId"
    private fun credKey(userId: String) = "credentials_$userId"
    private fun timestampKey(userId: String) = "cred_timestamp_$userId"

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PREFS_NAME = "utpad_secure_auth"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        const val CACHE_VALIDITY_MILLIS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }
}
