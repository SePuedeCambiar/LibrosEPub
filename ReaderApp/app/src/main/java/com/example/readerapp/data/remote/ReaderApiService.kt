package com.example.readerapp.data.remote

import com.example.readerapp.data.remote.dto.DownloadRequestDto
import com.example.readerapp.data.remote.dto.DownloadResponseDto
import com.example.readerapp.data.remote.dto.SearchResponseDto
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Interfaz de Retrofit (SOLID - ISP).
 * Define los endpoints exactos que expone nuestro servidor en Go.
 */
interface ReaderApiService {

    @GET("api/v1/books/search")
    suspend fun searchBooks(
        @Query("q") query: String,
        @Query("lang") language: String = "es",
        @Query("author") author: String = "",
        @Query("limit") limit: Int = 15
    ): SearchResponseDto

    @POST("api/v1/books/download")
    suspend fun requestDownload(
        @Body request: DownloadRequestDto
    ): DownloadResponseDto
}

/**
 * Creador del cliente HTTP Retrofit (Singleton).
 */
object RetrofitClient {
    // ⚠️ NOTA: Si pruebas en la tablet real por Wi-Fi, pon la IP local de tu PC con Debian (ej: "http://192.168.1.15:8080/")
    // Si usas el emulador de Android Studio dentro de la misma PC, usa "http://10.0.2.2:8080/"
    private const val BASE_URL = "http://10.0.2.2:8080/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val apiService: ReaderApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ReaderApiService::class.java)
    }
}