package dev.kris.clyde.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * App preferences + the loopback shared secret (X-Clyde-Key). Init in ClydeApp.onCreate.
 *
 * The secret is stored ENCRYPTED at rest: AES-256/GCM under a key held in the Android Keystore
 * (TEE/StrongBox-backed and non-exportable), so it is not readable as cleartext from the prefs file
 * even on a rooted device. A legacy plaintext key is migrated transparently on first run.
 */
object Prefs {
    private const val FILE = "clyde_prefs"
    private const val KEY_CLYDE_ENC = "clyde_key_enc"  // base64( 12-byte IV | GCM ciphertext )
    private const val KEY_CLYDE_LEGACY = "clyde_key"   // old plaintext — migrated then removed
    private const val KEY_SIGNED_IN = "signed_in"
    private const val KS_ALIAS = "clyde_prefs_aeskey"
    private const val ANDROID_KS = "AndroidKeyStore"

    private lateinit var sp: SharedPreferences
    @Volatile private var cached: String? = null

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        // Migrate a legacy plaintext key into encrypted storage, then drop the cleartext.
        val legacy = sp.getString(KEY_CLYDE_LEGACY, null)
        if (!legacy.isNullOrEmpty()) {
            store(legacy)
            sp.edit().remove(KEY_CLYDE_LEGACY).apply()
        }
        // Ensure a usable encrypted key exists — regenerate if missing or no longer decryptable
        // (e.g. the Keystore key was invalidated by a device credential reset). Fail-closed.
        if (sp.getString(KEY_CLYDE_ENC, null).isNullOrEmpty() || load().isEmpty()) {
            store(generateKey())
        }
    }

    /** 32 hex chars, shared with brain CLYDE_KEY. "" only if storage is unreadable (servers fail closed). */
    val clydeKey: String get() = cached ?: load().also { cached = it }

    var signedIn: Boolean
        get() = sp.getBoolean(KEY_SIGNED_IN, false)
        set(value) = sp.edit().putBoolean(KEY_SIGNED_IN, value).apply()

    private fun store(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey())
        val iv = cipher.iv // Keystore generates a fresh 12-byte GCM IV
        val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val blob = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, blob, 0, iv.size)
        System.arraycopy(ct, 0, blob, iv.size, ct.size)
        sp.edit().putString(KEY_CLYDE_ENC, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
        cached = value
    }

    private fun load(): String = runCatching {
        val blob = Base64.decode(sp.getString(KEY_CLYDE_ENC, "") ?: "", Base64.NO_WRAP)
        if (blob.size <= 12) return@runCatching ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), GCMParameterSpec(128, blob, 0, 12))
        String(cipher.doFinal(blob, 12, blob.size - 12), Charsets.UTF_8)
    }.getOrDefault("")

    private fun aesKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
        (ks.getEntry(KS_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KS)
        kg.init(
            KeyGenParameterSpec.Builder(KS_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return kg.generateKey()
    }

    private fun generateKey(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
