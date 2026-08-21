package com.example.readerapp

import com.example.readerapp.data.local.SyncthingScanner
import com.example.readerapp.data.remote.ReaderApiService
import com.example.readerapp.data.repository.BookRepositoryImpl
import com.example.readerapp.domain.usecase.SearchBooksUseCase
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ReaderIntegrationTest {

    @Test
    fun testSearchFromGoServer() = runBlocking {
        println("\n🚀 INICIANDO PRUEBA DE COMUNICACIÓN KOTLIN -> GO...")

        // Configuramos OkHttp con 30 segundos de tiempo de espera
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        val apiService = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:8080/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ReaderApiService::class.java)

        val repository = BookRepositoryImpl(apiService, SyncthingScanner())
        val searchUseCase = SearchBooksUseCase(repository)

        val query = "quijote"
        println("🔍 Buscando '$query' en idioma español...")
        val result = searchUseCase(query, "es")

        // Si ocurre algún fallo, imprimimos el error exacto
        if (result.isFailure) {
            println("❌ DETALLE DEL ERROR:")
            result.exceptionOrNull()?.printStackTrace()
        }

        assertTrue("La petición al servidor Go debió ser exitosa", result.isSuccess)

        val books = result.getOrNull() ?: emptyList()
        println("\n✅ ¡ÉXITO TOTAL! Libros recibidos en Kotlin (${books.size} encontrados):")
        books.forEachIndexed { index, book ->
            println("   [${index + 1}] ${book.title}")
            println("       Autor: ${book.authors.joinToString()}")
            println("       Formato: ${book.extension} | Fuente: ${book.provider}")
            println("       URL Descarga: ${book.downloadUrl}\n")
        }

        assertTrue("Se esperaba recibir al menos un libro", books.isNotEmpty())
    }
}