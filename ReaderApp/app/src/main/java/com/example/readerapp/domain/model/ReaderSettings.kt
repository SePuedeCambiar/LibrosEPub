package com.example.readerapp.domain.model

/**
 * Temas visuales disponibles para el lector.
 */
enum class ReaderTheme {
    LIGHT,   // Fondo blanco, texto negro
    DARK,    // Fondo oscuro, texto gris claro
    SEPIA    // Fondo crema, texto marrón oscuro
}

/**
 * Familias de fuentes disponibles.
 */
enum class ReaderFont {
    SANS,    // Moderna, sin remates (estándar)
    SERIF,   // Clásica, con remates (ideal para libros)
    MONOSPACE // Estilo máquina de escribir
}

/**
 * Entidad de dominio que agrupa todos los ajustes visuales del lector.
 * Al ser una data class, es fácil de pasar entre el ViewModel y la UI.
 */
data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val fontSize: Int = 18, // Tamaño en sp
    val font: ReaderFont = ReaderFont.SANS,
    val autoNightMode: Boolean = false,
    val nightModeStartHour: Int = 22, // 10 PM
    val nightModeEndHour: Int = 7     // 7 AM
)