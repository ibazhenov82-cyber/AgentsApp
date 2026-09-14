package com.example.agentsapp.data.remote

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val PREFS_NAME = "server_connection"
private const val KEY_BASE_URL = "base_url"

/** Адрес сервера AgentsCore — единственная настройка уровня самого
 * приложения (всё остальное живёт на сервере). По умолчанию указывает на
 * алиас хост-машины для Android-эмулятора. */
class ServerConnectionSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _baseUrl = MutableStateFlow(loadBaseUrl())
    val baseUrl: StateFlow<String> = _baseUrl

    private fun loadBaseUrl(): String {
        val stored = prefs.getString(KEY_BASE_URL, null)
        return normalize(stored ?: DEFAULT_BASE_URL)
    }

    fun setBaseUrl(url: String) {
        val normalized = normalize(url)
        prefs.edit().putString(KEY_BASE_URL, normalized).apply()
        _baseUrl.value = normalized
    }

    fun currentBaseUrl(): String = _baseUrl.value

    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

        private fun normalize(url: String): String {
            val trimmed = url.trim()
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }
}
