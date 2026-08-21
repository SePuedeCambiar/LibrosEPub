package com.example.readerapp.data.remote.dto

import com.example.readerapp.domain.model.Book
import com.google.gson.annotations.SerializedName

/**
 * Mapea la respuesta JSON de un libro proveniente del servidor en Go.
 */
data class BookDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("authors") val authors: List<String>?,
    @SerializedName("language") val language: String,
    @SerializedName("cover_url") val coverUrl: String?,
    @SerializedName("download_url") val downloadUrl: String,
    @SerializedName("extension") val extension: String,
    @SerializedName("provider") val provider: String
) {
    fun toDomain(): Book {
        return Book(
            id = id,
            title = title,
            authors = authors ?: emptyList(),
            language = language,
            coverUrl = coverUrl,
            downloadUrl = downloadUrl,
            extension = extension,
            provider = provider
        )
    }
}

/**
 * Mapea la respuesta completa de búsqueda: GET /api/v1/books/search
 */
data class SearchResponseDto(
    @SerializedName("total") val total: Int = 0, // <-- Corregido: Int con mayúscula
    @SerializedName("books") val books: List<BookDto>?,
    @SerializedName("took_ms") val tookMs: Long
)

/**
 * Payload para solicitar descarga: POST /api/v1/books/download
 */
data class DownloadRequestDto(
    @SerializedName("book_id") val bookId: String,
    @SerializedName("title") val title: String,
    @SerializedName("download_url") val downloadUrl: String,
    @SerializedName("extension") val extension: String
)

/**
 * Respuesta tras completar la descarga en el servidor
 */
data class DownloadResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("file_path") val filePath: String,
    @SerializedName("size_bytes") val sizeBytes: Long,
    @SerializedName("duration_sec") val durationSec: Double
)