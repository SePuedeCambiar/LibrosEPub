package com.example.readerapp.data.local

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class DirectDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadToTablet(downloadUrl: String, title: String, extension: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(downloadUrl).build()
            val resp = client.newCall(req).execute()

            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("Error HTTP: ${resp.code}"))
            }

            val body = resp.body ?: return@withContext Result.failure(Exception("Cuerpo vacío"))

            // Carpeta de destino en la tablet
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val syncthingDir = File(downloadsDir, "Syncthing")
            val targetFolder = if (syncthingDir.exists()) syncthingDir else downloadsDir
            targetFolder.mkdirs()

            val cleanName = title.trim().lowercase().replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }
            val targetFile = File(targetFolder, "${cleanName}.$extension")

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}