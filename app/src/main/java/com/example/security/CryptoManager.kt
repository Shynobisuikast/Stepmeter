package com.example.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end local encryption manager.
 * - Uses hardware-backed Android Keystore (AES-256-GCM) for on-device encrypted storage and automatic backups.
 * - Uses PBKDF2 + AES-256-GCM with random salt & IV for portable password-protected CSV backups.
 */
object CryptoManager {
  private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
  private const val KEY_ALIAS = "stepmeter_local_aes256_master"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val GCM_TAG_BITS = 128
  private const val IV_SIZE = 12
  private const val SALT_SIZE = 16
  private const val PBKDF2_ITERATIONS = 65_536

  // Binary container format headers
  private val MAGIC_KEYSTORE = "ADIM_KS1".toByteArray(Charsets.UTF_8)
  private val MAGIC_PW_SHA256 = "ADIM_PW2".toByteArray(Charsets.UTF_8)
  private val MAGIC_PW_SHA1 = "ADIM_PW1".toByteArray(Charsets.UTF_8)

  private val secureRandom = SecureRandom()

  @Volatile private var cachedKeystoreKey: SecretKey? = null

  // Fallback ephemeral key ONLY when AndroidKeyStore is absent (e.g., Robolectric JVM unit tests)
  @Volatile private var testFallbackKey: SecretKey? = null

  private fun getOrCreateKeystoreKey(): SecretKey {
    cachedKeystoreKey?.let { return it }
    return synchronized(this) {
      cachedKeystoreKey?.let { return it }
      try {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        val resolved =
          if (existing != null) {
            existing
          } else {
            val keyGenerator =
              KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec =
              KeyGenParameterSpec.Builder(
                  KEY_ALIAS,
                  KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
          }
        cachedKeystoreKey = resolved
        resolved
      } catch (_: Exception) {
        testFallbackKey
          ?: KeyGenerator.getInstance("AES")
            .apply { init(256) }
            .generateKey()
            .also {
              testFallbackKey = it
              cachedKeystoreKey = it
            }
      }
    }
  }

  fun encryptWithKeystore(plainBytes: ByteArray): ByteArray {
    val key = getOrCreateKeystoreKey()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val iv = cipher.iv
    val encrypted = cipher.doFinal(plainBytes)

    return ByteBuffer.allocate(MAGIC_KEYSTORE.size + 1 + iv.size + encrypted.size)
      .put(MAGIC_KEYSTORE)
      .put(iv.size.toByte())
      .put(iv)
      .put(encrypted)
      .array()
  }

  fun decryptWithKeystore(payload: ByteArray): ByteArray {
    val buffer = ByteBuffer.wrap(payload)
    val magic = ByteArray(MAGIC_KEYSTORE.size)
    require(payload.size > MAGIC_KEYSTORE.size + 2) { "Geçersiz şifreli veri formatı." }
    buffer.get(magic)
    require(magic.contentEquals(MAGIC_KEYSTORE)) { "Anahtar deposu başlığı doğrulanamadı." }
    val ivLen = buffer.get().toInt() and 0xFF
    val iv = ByteArray(ivLen)
    buffer.get(iv)
    val cipherBytes = ByteArray(buffer.remaining())
    buffer.get(cipherBytes)

    val key = getOrCreateKeystoreKey()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
    return cipher.doFinal(cipherBytes)
  }

  fun encryptStringKeystore(plainText: String): String {
    val encrypted = encryptWithKeystore(plainText.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(encrypted, Base64.NO_WRAP)
  }

  fun decryptStringKeystore(encoded: String): String {
    val raw = Base64.decode(encoded, Base64.NO_WRAP)
    return String(decryptWithKeystore(raw), Charsets.UTF_8)
  }

  fun encryptWithPassword(plainBytes: ByteArray, password: String): ByteArray {
    require(password.length >= 4) { "Parola en az 4 karakter olmalıdır." }
    val salt = ByteArray(SALT_SIZE).also { secureRandom.nextBytes(it) }
    val iv = ByteArray(IV_SIZE).also { secureRandom.nextBytes(it) }
    val useSha256 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    val magic = if (useSha256) MAGIC_PW_SHA256 else MAGIC_PW_SHA1
    val key = derivePasswordKey(password, salt, useSha256)

    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
    val encrypted = cipher.doFinal(plainBytes)

    return ByteBuffer.allocate(magic.size + SALT_SIZE + IV_SIZE + encrypted.size)
      .put(magic)
      .put(salt)
      .put(iv)
      .put(encrypted)
      .array()
  }

  fun decryptWithPassword(payload: ByteArray, password: String): ByteArray {
    require(payload.size > MAGIC_PW_SHA256.size + SALT_SIZE + IV_SIZE) {
      "Şifreli yedek dosyası bozuk veya eksik."
    }
    val buffer = ByteBuffer.wrap(payload)
    val magic = ByteArray(MAGIC_PW_SHA256.size)
    buffer.get(magic)
    val useSha256 =
      when {
        magic.contentEquals(MAGIC_PW_SHA256) -> true
        magic.contentEquals(MAGIC_PW_SHA1) -> false
        else -> throw IllegalArgumentException("Bu dosya parola ile şifrelenmiş Stepmeter yedeği değil.")
      }
    val salt = ByteArray(SALT_SIZE).also { buffer.get(it) }
    val iv = ByteArray(IV_SIZE).also { buffer.get(it) }
    val cipherBytes = ByteArray(buffer.remaining()).also { buffer.get(it) }

    val key = derivePasswordKey(password, salt, useSha256)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
    return cipher.doFinal(cipherBytes)
  }

  fun isPasswordProtectedPayload(payload: ByteArray): Boolean {
    if (payload.size < MAGIC_PW_SHA256.size) return false
    val magic = payload.copyOfRange(0, MAGIC_PW_SHA256.size)
    return magic.contentEquals(MAGIC_PW_SHA256) || magic.contentEquals(MAGIC_PW_SHA1)
  }

  fun isKeystorePayload(payload: ByteArray): Boolean {
    if (payload.size < MAGIC_KEYSTORE.size) return false
    val magic = payload.copyOfRange(0, MAGIC_KEYSTORE.size)
    return magic.contentEquals(MAGIC_KEYSTORE)
  }

  private fun derivePasswordKey(password: String, salt: ByteArray, sha256: Boolean): SecretKey {
    val algo = if (sha256) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
    val factory = SecretKeyFactory.getInstance(algo)
    val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
    val keyBytes = factory.generateSecret(spec).encoded
    return SecretKeySpec(keyBytes, "AES")
  }
}
