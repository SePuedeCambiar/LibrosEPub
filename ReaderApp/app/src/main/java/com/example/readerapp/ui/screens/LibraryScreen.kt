package com.example.readerapp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.readerapp.domain.model.Book
import com.example.readerapp.ui.viewmodel.LibraryViewModel
import java.io.File

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mi Biblioteca (Syncthing)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { viewModel.refreshLocalBooks() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.localBooks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay libros en la carpeta de Syncthing.\nDescarga uno desde la pestaña Buscar.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.localBooks) { book ->
                    LocalBookCard(book = book, onOpenBook = { openBookFile(context, book) })
                }
            }
        }
    }
}

@Composable
fun LocalBookCard(book: Book, onOpenBook: () -> Unit) {
    Card(
        onClick = onOpenBook,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Formato: ${book.extension.uppercase()}", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onOpenBook) {
                Text("Leer")
            }
        }
    }
}

private fun openBookFile(context: Context, book: Book) {
    val path = book.localFilePath ?: return
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "El archivo no existe", Toast.LENGTH_SHORT).show()
        return
    }

    val uri: Uri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } catch (e: Exception) {
        Uri.fromFile(file)
    }

    val mimeType = if (book.extension == "pdf") "application/pdf" else "application/epub+zip"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Abrir con"))
    } catch (e: Exception) {
        Toast.makeText(context, "No tienes una app lectora instalada", Toast.LENGTH_SHORT).show()
    }
}