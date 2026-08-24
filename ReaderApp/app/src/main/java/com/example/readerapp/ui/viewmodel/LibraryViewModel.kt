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

/**
 * Estado de la interfaz de la Biblioteca.
 * Usamos una data class para que Compose pueda detectar cambios
 * y redibujar solo lo necesario.
 */
data class LibraryUiState(
    val localBooks: List<Book> = emptyList(),
    val isLoading: Boolean = false
)

class LibraryViewModel(
    private val getLocalBooksUseCase: GetLocalBooksUseCase
) : ViewModel() {

    // Estado interno (mutable)
    private val _uiState = MutableStateFlow(LibraryUiState())

    // Estado público (inmutable para la UI)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        // Cargamos los libros automáticamente al iniciar el ViewModel
        refreshLocalBooks()
    }

    /**
     * Escanea la carpeta local de Syncthing y actualiza la lista de libros.
     */
    fun refreshLocalBooks() {
        viewModelScope.launch {
            // Marcamos que estamos cargando
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Ejecutamos el caso de uso para obtener los libros
                val books = getLocalBooksUseCase()

                // Actualizamos la lista en el estado
                _uiState.update { it.copy(
                    localBooks = books,
                    isLoading = false
                )}
            } catch (e: Exception) {
                // En caso de error, simplemente dejamos de cargar
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}