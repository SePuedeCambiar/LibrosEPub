package com.example.readerapp.domain.model

/**
 * Representa la entidad de un libro dentro del dominio de la aplicación.
 * Es completamente independiente de la API o la base de datos (SOLID - SRP).
 */
data class Book(
    val id: String,
    val title: String,
    val authors: List<String>,
    val language: String,
    val coverUrl: String?,
    val downloadUrl: String,
    val extension: String,
    val provider: String,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null
)