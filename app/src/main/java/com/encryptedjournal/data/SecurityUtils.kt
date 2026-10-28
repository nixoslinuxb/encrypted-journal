package com.encryptedjournal.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.SecureRandom
import android.util.Base64
import java.io.File
import androidx.security.crypto.EncryptedFile

object SecurityUtils {
    private const val PREFS_NAME = "secure_prefs"
    private const val DB_PASSPHRASE_KEY = "db_passphrase"

    fun getDatabasePassphrase(context: Context): ByteArray {
        val sharedPrefs = getEncryptedPrefs(context)
        var passphraseBase64 = sharedPrefs.getString(DB_PASSPHRASE_KEY, null)

        if (passphraseBase64 == null) {
            val random = SecureRandom()
            val passphrase = ByteArray(32)
            random.nextBytes(passphrase)
            passphraseBase64 = Base64.encodeToString(passphrase, Base64.NO_WRAP)
            sharedPrefs.edit().putString(DB_PASSPHRASE_KEY, passphraseBase64).apply()
        }

        return Base64.decode(passphraseBase64, Base64.NO_WRAP)
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getEncryptedFile(context: Context, file: File): EncryptedFile {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedFile.Builder(
            file,
            context,
            masterKeyAlias,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }
}
