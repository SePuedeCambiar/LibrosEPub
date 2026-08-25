package com.example.readerapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readerapp.data.local.AppPreferences
import com.example.readerapp.data.local.DirectDownloader
import com.example.readerapp.data.local.SyncthingScanner
import com.example.readerapp.data.repository.BookRepositoryImpl
import com.example.readerapp.domain.usecase.DownloadBookUseCase
import com.example.readerapp.domain.usecase.GetLocalBooksUseCase
import com.example.readerapp.domain.usecase.SearchBooksUseCase
import com.example.readerapp.ui.screens.LibraryScreen
import com.example.readerapp.ui.screens.ReaderScreen
import com.example.readerapp.ui.screens.SearchScreen
import com.example.readerapp.ui.screens.SettingsDialog
import com.example.readerapp.ui.theme.ReaderAppTheme
import com.example.readerapp.ui.viewmodel.LibraryViewModel
import com.example.readerapp.ui.viewmodel.ReaderViewModel
import com.example.readerapp.ui.viewmodel.SearchViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current

                var hasStoragePermission by remember { mutableStateOf(checkStoragePermission(context)) }
                var showPermissionDialog by remember { mutableStateOf(!hasStoragePermission) }

                var selectedTab by remember { mutableIntStateOf(0) }
                var showSettings by remember { mutableStateOf(false) }
                var readingBookPath by remember { mutableStateOf<String?>(null) }

                // Escuchar cuando el usuario regresa de Ajustes para revalidar el permiso
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            val granted = checkStoragePermission(context)
                            hasStoragePermission = granted
                            showPermissionDialog = !granted
                            if (granted) {
                                libraryViewModel.refreshLocalBooks()
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Diálogo para pedir permiso de almacenamiento en Android 11+ / Android 15
                if (showPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = { /* Forzar atención del usuario */ },
                        icon = { Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(36.dp)) },
                        title = { Text("Permiso de Archivos Necesario") },
                        text = {
                            Text(
                                "Para que ReaderApp pueda detectar y abrir tus libros EPUB y documentos PDF en la carpeta de Descargas/Syncthing, se necesita acceso a los archivos del dispositivo."
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                requestStoragePermission(context)
                            }) {
                                Text("Conceder Acceso")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPermissionDialog = false }) {
                                Text("Más tarde")
                            }
                        }
                    )
                }

                if (showSettings) {
                    SettingsDialog(
                        preferences = preferences,
                        onDismiss = { showSettings = false }
                    )
                }

                if (readingBookPath != null) {
                    val readerViewModel: ReaderViewModel = viewModel(
                        factory = ReaderViewModelFactory(application, File(readingBookPath!!))
                    )

                    ReaderScreen(
                        viewModel = readerViewModel,
                        onBack = { readingBookPath = null }
                    )
                } else {
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
                            1 -> LibraryScreen(
                                viewModel = libraryViewModel,
                                modifier = Modifier.padding(innerPadding),
                                onBookSelected = { path -> readingBookPath = path }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun requestStoragePermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(fallbackIntent)
            }
        }
    }
}

class ReaderViewModelFactory(private val app: android.app.Application, private val file: File) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return ReaderViewModel(app, file) as T
    }
}