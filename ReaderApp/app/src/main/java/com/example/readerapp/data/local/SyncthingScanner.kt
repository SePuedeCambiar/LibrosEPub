package com.example.readerapp.data.local

import android.os.Environment
import com.example.readerapp.domain.model.Book
import java.io.File

/**
 * Escanea el almacenamiento local de la tablet para detectar
 * los libros sincronizados por Syncthing (SOLID - SRP / DIP).
 */
class SyncthingScanner(
    private val customBaseDir: File? = null
) {

    // Carpeta donde Syncthing guarda los libros en la tablet
    private val defaultFolder: File by lazy {
        if (customBaseDir != null) return@lazy customBaseDir

        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val syncthingDir = File(downloadsDir, "Syncthing")
            if (syncthingDir.exists()) syncthingDir else downloadsDir
        } catch (e: Throwable) {
            // Fallback seguro cuando ejecutamos pruebas en la PC (donde android.os.Environment no existe)
            File("./downloads_test")
        }
    }

    fun scanLocalBooks(customPath: String? = null): List<Book> {
        val targetDir = if (customPath != null) File(customPath) else defaultFolder
        if (!targetDir.exists() || !targetDir.isDirectory) {
            return emptyList()
        }

        val supportedExtensions = listOf("epub", "pdf", "mobi")
        val files = targetDir.listFiles { file ->
            file.isFile && supportedExtensions.contains(file.extension.lowercase())
        } ?: return emptyList()

        return files.map { file ->
            val cleanTitle = file.nameWithoutExtension.replace("_", " ").replace("-", " ")
            Book(
                id = file.name.hashCode().toString(),
                title = cleanTitle,
                authors = listOf("Archivo Local"),
                language = "es",
                coverUrl = null,
                downloadUrl = "",
                extension = file.extension.lowercase(),
                provider = "local",
                isDownloaded = true,
                localFilePath = file.absolutePath
            )
        }
    }
}