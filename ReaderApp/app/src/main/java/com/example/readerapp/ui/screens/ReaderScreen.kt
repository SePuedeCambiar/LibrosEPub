package com.example.readerapp.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.readerapp.domain.model.ReaderFont
import com.example.readerapp.domain.model.ReaderSettings
import com.example.readerapp.domain.model.ReaderTheme
import com.example.readerapp.ui.viewmodel.ReaderViewModel

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    val tag = "ReaderScreen"
    var showSettings by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val currentChapter = viewModel.book?.chapters?.getOrNull(viewModel.currentChapterIndex)
    val chapterTitle = currentChapter?.title ?: "Leyendo..."
    val totalGlobal = viewModel.book?.totalEstimatedPages ?: 1

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it })
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = viewModel.book?.title ?: "Cargando...",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1
                            )
                            Text(
                                text = chapterTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showToc = true }) {
                            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Índice")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                        }
                    }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                BottomAppBar {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pág. ${viewModel.globalCurrentPage} de $totalGlobal",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        val percent = ((viewModel.globalCurrentPage.toFloat() / totalGlobal) * 100).toInt()
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                Log.d("WebViewConsole", "🌐 [JS] ${consoleMessage?.message()}")
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val url = request?.url ?: return null
                                if (url.scheme == "https" && url.host == "epub.local") {
                                    val path = url.path?.removePrefix("/") ?: return null
                                    val asset = viewModel.getAssetInputStream(path)
                                    if (asset != null) {
                                        return WebResourceResponse(asset.first, "UTF-8", asset.second)
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        // CONFIGURACIÓN DE PANTALLA Y RENDIMIENTO
                        settings.javaScriptEnabled = true
                        addJavascriptInterface(viewModel.jsInterface, "AndroidBridge")

                        // Escala 1:1 real (soluciona el problema de texto comprimido/una sola línea)
                        settings.useWideViewPort = false
                        settings.loadWithOverviewMode = false
                        settings.textZoom = 100

                        settings.setSupportZoom(false)
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false

                        webViewRef = this
                    }
                },
                update = { webView ->
                    webViewRef = webView
                    val baseUrl = "https://epub.local/${viewModel.currentChapterHref}"
                    webView.loadDataWithBaseURL(baseUrl, viewModel.currentHtmlContent, "text/html", "UTF-8", null)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Zonas de toque tipo libro (Pasar páginas una a una)
            Row(modifier = Modifier.fillMaxSize()) {
                // Izquierda (30%): Retroceder página
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.3f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            webViewRef?.evaluateJavascript("typeof prevPage === 'function' ? prevPage() : 'loading'") { result ->
                                if (result == "false") {
                                    viewModel.onPrevChapterRequested()
                                }
                            }
                        }
                )

                // Centro (40%): Menús
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.4f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showControls = !showControls
                        }
                )

                // Derecha (30%): Avanzar página
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.3f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            webViewRef?.evaluateJavascript("typeof nextPage === 'function' ? nextPage() : 'loading'") { result ->
                                if (result == "false") {
                                    viewModel.onNextChapterRequested()
                                }
                            }
                        }
                )
            }

            // Modal del Índice de Capítulos (TOC)
            if (showToc) {
                AlertDialog(
                    onDismissRequest = { showToc = false },
                    title = { Text("Índice del Libro") },
                    text = {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        ) {
                            val chapters = viewModel.book?.chapters ?: emptyList()
                            itemsIndexed(chapters) { index, ch ->
                                ListItem(
                                    headlineContent = { Text(ch.title) },
                                    supportingContent = { Text("Página ~${ch.startGlobalPage}") },
                                    modifier = Modifier.clickable {
                                        viewModel.loadChapter(index, initialPage = 0)
                                        showToc = false
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = if (index == viewModel.currentChapterIndex)
                                            MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    )
                                )
                                HorizontalDivider()
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showToc = false }) {
                            Text("Cerrar")
                        }
                    }
                )
            }

            // Diálogo de Ajustes de Lectura
            if (showSettings) {
                ReaderSettingsDialog(
                    currentSettings = viewModel.settings,
                    onDismiss = { showSettings = false },
                    onSave = { newSettings ->
                        viewModel.updateSettings(newSettings)
                        showSettings = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsDialog(
    currentSettings: ReaderSettings,
    onDismiss: () -> Unit,
    onSave: (ReaderSettings) -> Unit
) {
    var theme by remember { mutableStateOf(currentSettings.theme) }
    var fontSize by remember { mutableStateOf(currentSettings.fontSize) }
    var font by remember { mutableStateOf(currentSettings.font) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustes de Lectura") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Tema", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    ReaderTheme.entries.forEach { t ->
                        FilterChip(
                            selected = theme == t,
                            onClick = { theme = t },
                            label = { Text(t.name) }
                        )
                    }
                }

                Text("Tamaño de Letra: ${fontSize}px", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { fontSize = it.toInt() },
                    valueRange = 14f..32f
                )

                Text("Tipografía", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    ReaderFont.entries.forEach { f ->
                        FilterChip(
                            selected = font == f,
                            onClick = { font = f },
                            label = { Text(f.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(currentSettings.copy(theme = theme, fontSize = fontSize, font = font))
            }) {
                Text("Aplicar")
            }
        }
    )
}