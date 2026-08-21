package com.example.readerapp.domain.usecase

import com.example.readerapp.domain.model.Book
import com.example.readerapp.domain.repository.BookRepository

class GetLocalBooksUseCase(private val repository: BookRepository) {
    operator fun invoke(): List<Book> {
        return repository.getLocalBooks()
    }
}