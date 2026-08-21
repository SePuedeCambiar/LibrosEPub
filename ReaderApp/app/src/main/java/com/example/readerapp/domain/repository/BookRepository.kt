package com.example.readerapp.domain.repository

import com.example.readerapp.domain.model.Book

/**
 * Contrato del Repositorio (SOLID - DIP).
 * La interfaz de la que dependerán todas las pantallas de la app.
 */
interface BookRepository {
    suspend fun searchBooks(query: String, language: String = "es"): Result<List<Book>>
    suspend fun downloadBook(book: Book): Result<Boolean>
    fun getLocalBooks(): List<Book>
}