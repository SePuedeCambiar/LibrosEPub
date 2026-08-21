package com.example.readerapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.readerapp.domain.model.Book
import com.example.readerapp.domain.usecase.GetLocalBooksUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val localBooks: List<Book> = emptyList(),
    val isLoading: Boolean = false
)

class LibraryViewModel(
    private val getLocalBooksUseCase: GetLocalBooksUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        refreshLocalBooks()
    }

    fun refreshLocalBooks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val books = getLocalBooksUseCase()
            _uiState.update { it.copy(localBooks = books, isLoading = false) }
        }
    }
}