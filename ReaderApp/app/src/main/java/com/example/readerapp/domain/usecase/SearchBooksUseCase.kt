package com.example.readerapp.domain.usecase

import com.example.readerapp.domain.model.Book
import com.example.readerapp.domain.repository.BookRepository

class SearchBooksUseCase(private val repository: BookRepository) {
    suspend operator fun invoke(query: String, language: String = "es"): Result<List<Book>> {
        if (query.isBlank()) return Result.success(emptyList())
        return repository.searchBooks(query.trim(), language)
    }
}