package com.example.readerapp.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.readerapp.domain.model.ReaderFont
import com.example.readerapp.domain.model.ReaderSettings
import com.example.readerapp.domain.model.ReaderTheme

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    // --- Ajustes del Servidor (Existentes) ---
    var serverUrl: String
        get() = prefs.getString("server_url", "http://192.168.1.100:8080/") ?: "http://192.168.1.100:8080/"
        set(value) = prefs.edit().putString("server_url", formatUrl(value)).apply()

    var useServerMode: Boolean
        get() = prefs.getBoolean("use_server_mode", false)
        set(value) = prefs.edit().putBoolean("use_server_mode", value).apply()

    // --- Ajustes del Lector (Nuevos) ---

    /**
     * Recupera toda la configuración del lector.
     * Si no hay nada guardado, devuelve los valores por defecto de ReaderSettings.
     */
    fun getReaderSettings(): ReaderSettings {
        return ReaderSettings(
            theme = ReaderTheme.valueOf(
                prefs.getString("reader_theme", ReaderTheme.LIGHT.name) ?: ReaderTheme.LIGHT.name
            ),
            fontSize = prefs.getInt("reader_font_size", 18),
            font = ReaderFont.valueOf(
                prefs.getString("reader_font", ReaderFont.SANS.name) ?: ReaderFont.SANS.name
            ),
            autoNightMode = prefs.getBoolean("reader_auto_night", false),
            nightModeStartHour = prefs.getInt("reader_night_start", 22),
            nightModeEndHour = prefs.getInt("reader_night_end", 7)
        )
    }

    /**
     * Guarda la configuración completa del lector.
     */
    fun saveReaderSettings(settings: ReaderSettings) {
        prefs.edit().apply {
            putString("reader_theme", settings.theme.name)
            putInt("reader_font_size", settings.fontSize)
            putString("reader_font", settings.font.name)
            putBoolean("reader_auto_night", settings.autoNightMode)
            putInt("reader_night_start", settings.nightModeStartHour)
            putInt("reader_night_end", settings.nightModeEndHour)
            apply()
        }
    }

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