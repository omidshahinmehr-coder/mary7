package ir.lbo.locationsms

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * All persisted app data (settings, location history, command history) goes
 * through one encrypted SharedPreferences file instead of a plain-text one —
 * this app stores location history and an email App Password, both worth
 * protecting if the device is ever rooted or backed up insecurely.
 *
 * Uses a NEW file name (rather than reusing the old plain-text file) so an
 * upgrade never tries to decrypt old plain-text data as if it were
 * encrypted. Existing installs will need to re-enter their settings once
 * after upgrading — a one-time cost worth the real encryption benefit.
 *
 * If key-store-backed encryption setup fails for any reason (some obscure
 * OEM keystore bug, for instance), this falls back to a plain preferences
 * file rather than crashing the app — working unencrypted beats not
 * working at all.
 */
object SecurePrefs {
    private const val PREFS_FILE_NAME = "location_sms_secure_prefs"

    @Volatile
    private var instance: SharedPreferences? = null

    fun get(context: Context): SharedPreferences {
        return instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }
    }

    private fun create(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }
}
