package com.example.readerapp

import com.example.readerapp.data.remote.GutendexDirectClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderIntegrationTest {

    @Test
    fun testSearchFromGutendexDirect() = runBlocking {
        println("\n🚀 INICIANDO PRUEBA DE BÚSQUEDA DIRECTA (GUTENDEX)...")

        val query = "quijote"
        val response = GutendexDirectClient.api.search(search = query, languages = "es")

        val results = response.results ?: emptyList()
        println("✅ Libros encontrados: ${results.size}")
        results.take(3).forEachIndexed { index, book ->
            println("   [${index + 1}] ${book.title} (ID: ${book.id})")
        }

        assertTrue("Se esperaba encontrar al menos un libro", results.isNotEmpty())
    }
}