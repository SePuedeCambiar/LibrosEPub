package com.example.readerapp.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

data class GutendexDirectPerson(@SerializedName("name") val name: String)

data class GutendexDirectBook(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("authors") val authors: List<GutendexDirectPerson>?,
    @SerializedName("languages") val languages: List<String>?,
    @SerializedName("formats") val formats: Map<String, String>?
)

data class GutendexDirectResponse(
    @SerializedName("count") val count: Int,
    @SerializedName("results") val results: List<GutendexDirectBook>?
)

interface GutendexDirectApi {
    @GET("books")
    suspend fun search(
        @Query("search") search: String,
        @Query("languages") languages: String = "es"
    ): GutendexDirectResponse
}

object GutendexDirectClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val api: GutendexDirectApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://gutendex.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GutendexDirectApi::class.java)
    }
}