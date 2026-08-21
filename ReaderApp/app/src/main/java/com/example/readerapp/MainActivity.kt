package com.example.readerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.readerapp.data.local.AppPreferences
import com.example.readerapp.data.local.DirectDownloader
import com.example.readerapp.data.local.SyncthingScanner
import com.example.readerapp.data.repository.BookRepositoryImpl
import com.example.readerapp.domain.usecase.DownloadBookUseCase
import com.example.readerapp.domain.usecase.GetLocalBooksUseCase
import com.example.readerapp.domain.usecase.SearchBooksUseCase
import com.example.readerapp.ui.screens.LibraryScreen
import com.example.readerapp.ui.screens.SearchScreen
import com.example.readerapp.ui.screens.SettingsDialog
import com.example.readerapp.ui.theme.ReaderAppTheme
import com.example.readerapp.ui.viewmodel.LibraryViewModel
import com.example.readerapp.ui.viewmodel.SearchViewModel

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Inicialización de componentes (SOLID)
        val preferences = AppPreferences(applicationContext)
        val scanner = SyncthingScanner()
        val directDownloader = DirectDownloader()
        val repository = BookRepositoryImpl(preferences, scanner, directDownloader)

        val searchUseCase = SearchBooksUseCase(repository)
        val downloadUseCase = DownloadBookUseCase(repository)
        val getLocalUseCase = GetLocalBooksUseCase(repository)

        val searchViewModel = SearchViewModel(searchUseCase, downloadUseCase)
        val libraryViewModel = LibraryViewModel(getLocalUseCase)

        setContent {
            ReaderAppTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                var showSettings by remember { mutableStateOf(false) }

                if (showSettings) {
                    SettingsDialog(
                        preferences = preferences,
                        onDismiss = { showSettings = false }
                    )
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (selectedTab == 0) "Explorar Libros" else "Mi Biblioteca") },
                            actions = {
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                                label = { Text("Buscar") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = {
                                    selectedTab = 1
                                    libraryViewModel.refreshLocalBooks()
                                },
                                icon = { Icon(Icons.Default.Folder, contentDescription = "Biblioteca") },
                                label = { Text("Biblioteca") }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> SearchScreen(viewModel = searchViewModel, modifier = Modifier.padding(innerPadding))
                        1 -> LibraryScreen(viewModel = libraryViewModel, modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}