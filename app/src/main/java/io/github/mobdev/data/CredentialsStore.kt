package io.github.mobdev.data

import android.content.Context
import androidx.core.content.edit

class CredentialsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    var token: String? = prefs.getString(KEY_TOKEN, null)
        set(value) {
            field = value
            prefs.edit { if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value) }
        }

    val username: String? get() = prefs.getString(KEY_USERNAME, null)
    val password: String? get() = prefs.getString(KEY_PASSWORD, null)

    val hasCredentials: Boolean get() = username != null && password != null

    fun saveCredentials(username: String, password: String) {
        prefs.edit {
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
        }
    }

    fun clear() {
        token = null
        prefs.edit {
            remove(KEY_USERNAME)
            remove(KEY_PASSWORD)
            remove(KEY_TOKEN)
        }
    }

    private companion object {
        const val PREFS_NAME = "chat_prefs"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_TOKEN = "token"
    }
}
