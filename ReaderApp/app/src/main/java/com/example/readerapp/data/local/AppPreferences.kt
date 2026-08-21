package com.example.readerapp.data.local

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("server_url", "http://192.168.1.100:8080/") ?: "http://192.168.1.100:8080/"
        set(value) = prefs.edit().putString("server_url", formatUrl(value)).apply()

    var useServerMode: Boolean
        get() = prefs.getBoolean("use_server_mode", false)
        set(value) = prefs.edit().putBoolean("use_server_mode", value).apply()

    private fun formatUrl(input: String): String {
        var clean = input.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        if (!clean.endsWith("/")) {
            clean = "$clean/"
        }
        return clean
    }
}