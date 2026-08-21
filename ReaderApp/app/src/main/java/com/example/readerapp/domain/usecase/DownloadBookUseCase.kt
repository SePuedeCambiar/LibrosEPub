package com.example.readerapp.domain.usecase

import com.example.readerapp.domain.model.Book
import com.example.readerapp.domain.repository.BookRepository

class DownloadBookUseCase(private val repository: BookRepository) {
    suspend operator fun invoke(book: Book): Result<Boolean> {
        return repository.downloadBook(book)
    }
}