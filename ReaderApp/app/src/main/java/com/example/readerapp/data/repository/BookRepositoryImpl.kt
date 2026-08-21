package com.example.readerapp.data.repository

import com.example.readerapp.data.local.AppPreferences
import com.example.readerapp.data.local.DirectDownloader
import com.example.readerapp.data.local.SyncthingScanner
import com.example.readerapp.data.remote.GutendexDirectClient
import com.example.readerapp.data.remote.ReaderApiService
import com.example.readerapp.data.remote.dto.DownloadRequestDto
import com.example.readerapp.domain.model.Book
import com.example.readerapp.domain.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class BookRepositoryImpl(
    private val preferences: AppPreferences,
    private val localScanner: SyncthingScanner,
    private val directDownloader: DirectDownloader
) : BookRepository {

    // Cliente dinámico que se adapta a la IP guardada en los ajustes
    private fun getDynamicApiService(): ReaderApiService {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(preferences.serverUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ReaderApiService::class.java)
    }

    override suspend fun searchBooks(query: String, language: String): Result<List<Book>> = withContext(Dispatchers.IO) {
        val localBooks = localScanner.scanLocalBooks()

        // MODO 1: Si el usuario activó "Usar Servidor Go"
        if (preferences.useServerMode) {
            try {
                val api = getDynamicApiService()
                val response = api.searchBooks(query = query, language = language)
                val books = response.books?.map { dto ->
                    val isDownloaded = localBooks.any { it.title.equals(dto.title, ignoreCase = true) }
                    dto.toDomain().copy(isDownloaded = isDownloaded)
                } ?: emptyList()

                return@withContext Result.success(books)
            } catch (e: Exception) {
                // Si falla el servidor, se notifica el fallo
                return@withContext Result.failure(Exception("Servidor Go no responde (${preferences.serverUrl}): ${e.localizedMessage}"))
            }
        }

        // MODO 2: Búsqueda directa desde la tablet (Autónoma)
        try {
            val response = GutendexDirectClient.api.search(search = query, languages = language)
            val domainBooks = response.results?.mapNotNull { item ->
                val epubUrl = item.formats?.get("application/epub+zip") ?: return@mapNotNull null
                val coverUrl = item.formats["image/jpeg"]
                val authors = item.authors?.map { it.name } ?: listOf("Desconocido")
                val isDownloaded = localBooks.any { it.title.equals(item.title, ignoreCase = true) }

                Book(
                    id = item.id.toString(),
                    title = item.title,
                    authors = authors,
                    language = language,
                    coverUrl = coverUrl,
                    downloadUrl = epubUrl,
                    extension = "epub",
                    provider = "gutenberg_direct",
                    isDownloaded = isDownloaded
                )
            } ?: emptyList()

            Result.success(domainBooks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadBook(book: Book): Result<Boolean> = withContext(Dispatchers.IO) {
        // Si está en modo servidor, le pide a Go que descargue
        if (preferences.useServerMode) {
            try {
                val api = getDynamicApiService()
                val req = DownloadRequestDto(
                    bookId = book.id,
                    title = book.title,
                    downloadUrl = book.downloadUrl,
                    extension = book.extension
                )
                val resp = api.requestDownload(req)
                return@withContext Result.success(resp.success)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }

        // Si está en modo autónomo, descarga directo a la tablet
        val downloadResult = directDownloader.downloadToTablet(book.downloadUrl, book.title, book.extension)
        if (downloadResult.isSuccess) {
            Result.success(true)
        } else {
            Result.failure(downloadResult.exceptionOrNull() ?: Exception("Fallo en la descarga"))
        }
    }

    override fun getLocalBooks(): List<Book> {
        return localScanner.scanLocalBooks()
    }
}