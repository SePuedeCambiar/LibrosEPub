package com.example.readerapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.readerapp.domain.model.Book
import com.example.readerapp.ui.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Barra de Búsqueda
        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.onQueryChanged(it) },
            label = { Text("Buscar libros o novelas...") },
            trailingIcon = {
                IconButton(onClick = { viewModel.search() }) {
                    Icon(Icons.Default.Search, contentDescription = "Buscar")
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filtro de Idioma
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Idioma:", style = MaterialTheme.typography.bodyMedium)
            FilterChip(
                selected = state.language == "es",
                onClick = { viewModel.onLanguageChanged("es") },
                label = { Text("Español") }
            )
            FilterChip(
                selected = state.language == "en",
                onClick = { viewModel.onLanguageChanged("en") },
                label = { Text("Inglés") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Mensajes de Éxito / Error
        state.successMessage?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }
        state.errorMessage?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Estado de carga o Lista de libros
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.books) { book ->
                    BookSearchCard(
                        book = book,
                        isDownloading = state.downloadingBookId == book.id,
                        onDownloadClick = { viewModel.downloadBook(book) }
                    )
                }
            }
        }
    }
}

@Composable
fun BookSearchCard(
    book: Book,
    isDownloading: Boolean,
    onDownloadClick: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Portada optimizada con Coil
            if (book.coverUrl != null) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 60.dp, height = 90.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Datos del libro
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.authors.joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                SuggestionChip(
                    onClick = {},
                    label = { Text(book.extension.uppercase()) }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Botón de Descarga
            IconButton(
                onClick = onDownloadClick,
                enabled = !isDownloading && !book.isDownloaded
            ) {
                when {
                    isDownloading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    book.isDownloaded -> Icon(Icons.Default.Check, contentDescription = "Descargado", tint = MaterialTheme.colorScheme.primary)
                    else -> Icon(Icons.Default.Download, contentDescription = "Descargar al servidor")
                }
            }
        }
    }
}