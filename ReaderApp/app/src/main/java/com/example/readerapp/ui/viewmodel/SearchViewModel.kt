package com.example.readerapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.readerapp.domain.model.Book
import com.example.readerapp.domain.usecase.DownloadBookUseCase
import com.example.readerapp.domain.usecase.SearchBooksUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val language: String = "es",
    val isLoading: Boolean = false,
    val books: List<Book> = emptyList(),
    val errorMessage: String? = null,
    val downloadingBookId: String? = null,
    val successMessage: String? = null
)

class SearchViewModel(
    private val searchBooksUseCase: SearchBooksUseCase,
    private val downloadBookUseCase: DownloadBookUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
    }

    fun onLanguageChanged(newLang: String) {
        _uiState.update { it.copy(language = newLang) }
        search()
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

            val result = searchBooksUseCase(query, _uiState.value.language)

            result.onSuccess { books ->
                _uiState.update { it.copy(isLoading = false, books = books) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.localizedMessage ?: "Error de conexión") }
            }
        }
    }

    fun downloadBook(book: Book) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingBookId = book.id) }

            val result = downloadBookUseCase(book)

            result.onSuccess {
                _uiState.update { state ->
                    // Marcamos el libro como descargado en la lista visual
                    val updatedBooks = state.books.map { if (it.id == book.id) it.copy(isDownloaded = true) else it }
                    state.copy(
                        downloadingBookId = null,
                        books = updatedBooks,
                        successMessage = "¡'${book.title}' descargado en el servidor!"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        downloadingBookId = null,
                        errorMessage = "Error al descargar: ${error.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}