package com.example.readerapp.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.readerapp.domain.model.ReaderFont
import com.example.readerapp.domain.model.ReaderSettings
import com.example.readerapp.domain.model.ReaderTheme
import com.example.readerapp.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Guardamos el último HTML cargado para evitar recargas infinitas en el bloque update
    var lastLoadedHtml by remember { mutableStateOf("") }

    var pdfPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()

    // Carga de página PDF (se mantiene igual, funciona bien)
    // En ReaderScreen.kt al cambiar de página:
    LaunchedEffect(viewModel.chapterCurrentPage, viewModel.isPdf) {
        if (viewModel.isPdf) {
            val oldBitmap = pdfPageBitmap
            pdfPageBitmap = viewModel.renderPdfPage(viewModel.chapterCurrentPage)
            oldBitmap?.recycle() // Liberar memoria nativa de inmediato
        }
    }

    val currentChapter = viewModel.book?.chapters?.getOrNull(viewModel.currentChapterIndex)
    val chapterTitle = if (viewModel.isPdf) "Documento PDF" else (currentChapter?.title ?: "Leyendo...")
    val totalChapters = if (viewModel.isPdf) 1 else (viewModel.book?.chapters?.size ?: 1)
    val totalGlobal = if (viewModel.isPdf) viewModel.chapterTotalPages else (viewModel.book?.totalEstimatedPages ?: 1)

    val bgColor = when (viewModel.settings.theme) {
        ReaderTheme.LIGHT -> Color(0xFFFAF8F5)
        ReaderTheme.DARK -> Color(0xFF141414)
        ReaderTheme.SEPIA -> Color(0xFFF4ECD8)
    }

    val nativeBgColor = when (viewModel.settings.theme) {
        ReaderTheme.LIGHT -> android.graphics.Color.parseColor("#FAF8F5")
        ReaderTheme.DARK -> android.graphics.Color.parseColor("#141414")
        ReaderTheme.SEPIA -> android.graphics.Color.parseColor("#F4ECD8")
    }

    val pdfColorFilter = remember(viewModel.settings.theme) {
        when (viewModel.settings.theme) {
            ReaderTheme.DARK -> {
                val matrix = ColorMatrix(floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                ))
                ColorFilter.colorMatrix(matrix)
            }
            ReaderTheme.SEPIA -> {
                val matrix = ColorMatrix(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                ColorFilter.colorMatrix(matrix)
            }
            ReaderTheme.LIGHT -> null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (viewModel.isPdf) {
            var scale by remember { mutableStateOf(1f) }
            val state = rememberTransformableState { zoomChange, _, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 3f)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = state)
                    .clickable { showControls = !showControls },
                contentAlignment = Alignment.Center
            ) {
                pdfPageBitmap?.let { bitmap ->
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF Page",
                        modifier = Modifier.fillMaxSize().scale(scale),
                        contentScale = ContentScale.Fit,
                        colorFilter = pdfColorFilter
                    )
                } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            // --- VISOR DE EPUB CORREGIDO ---
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setBackgroundColor(nativeBgColor)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = WebView.OVER_SCROLL_NEVER
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
                                    if (asset != null) return WebResourceResponse(asset.first, "UTF-8", asset.second)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        settings.javaScriptEnabled = true
                        addJavascriptInterface(viewModel.jsInterface, "AndroidBridge")
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
                    // 1. Actualizar color de fondo
                    webView.setBackgroundColor(nativeBgColor)

                    // 2. CARGA DEL CONTENIDO (Aquí es donde estaba el fallo)
                    val html = viewModel.currentHtmlContent
                    if (html.isNotEmpty() && html != lastLoadedHtml) {
                        val baseUrl = "https://epub.local/${viewModel.currentChapterHref}"
                        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                        lastLoadedHtml = html // Marcamos como cargado
                    }

                    if (webViewRef != webView) webViewRef = webView
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Capa de Toques
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxHeight().weight(0.3f).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (!viewModel.isChapterLoading) {
                        if (viewModel.isPdf) {
                            viewModel.onPrevChapterRequested()
                        } else {
                            webViewRef?.evaluateJavascript("typeof prevPage === 'function' ? prevPage() : 'loading'") { result ->
                                if (result == "false") viewModel.onPrevChapterRequested()
                            }
                        }
                    }
                }
            )
            Box(
                modifier = Modifier.fillMaxHeight().weight(0.4f).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showControls = !showControls
                }
            )
            Box(
                modifier = Modifier.fillMaxHeight().weight(0.3f).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (!viewModel.isChapterLoading) {
                        if (viewModel.isPdf) {
                            viewModel.onNextChapterRequested()
                        } else {
                            webViewRef?.evaluateJavascript("typeof nextPage === 'function' ? nextPage() : 'loading'") { result ->
                                if (result == "false") viewModel.onNextChapterRequested()
                            }
                        }
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = viewModel.book?.title ?: "Cargando...", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(text = chapterTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    if (!viewModel.isPdf) {
                        IconButton(onClick = { showToc = true }) { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Índice") }
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "Ajustes") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Página ${viewModel.chapterCurrentPage + 1} de ${viewModel.chapterTotalPages}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (viewModel.isPdf) "Documento PDF" else "Sección ${viewModel.currentChapterIndex + 1} de $totalChapters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val percent = ((viewModel.globalCurrentPage.toFloat() / totalGlobal) * 100).toInt().coerceIn(0, 100)
                    Text(text = "$percent%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (showToc && !viewModel.isPdf) {
            AlertDialog(
                onDismissRequest = { showToc = false },
                title = { Text("Índice del Libro") },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        val chapters = viewModel.book?.chapters ?: emptyList()
                        itemsIndexed(chapters) { index, ch ->
                            ListItem(
                                headlineContent = { Text(ch.title) },
                                supportingContent = { Text("Sección ${index + 1}") },
                                modifier = Modifier.clickable {
                                    viewModel.loadChapter(index, initialPage = 0)
                                    showToc = false
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (index == viewModel.currentChapterIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            )
                            HorizontalDivider()
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showToc = false }) { Text("Cerrar") } }
            )
        }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsDialog(
    currentSettings: ReaderSettings,
    onDismiss: () -> Unit,
    onSave: (ReaderSettings) -> Unit
) {
    var theme by remember { mutableStateOf(currentSettings.theme) }
    var fontSize by remember { mutableIntStateOf(currentSettings.fontSize) }
    var font by remember { mutableStateOf(currentSettings.font) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustes de Lectura") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Tema", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    ReaderTheme.entries.forEach { t ->
                        FilterChip(selected = theme == t, onClick = { theme = t }, label = { Text(t.name) })
                    }
                }
                Text("Tamaño de Letra: ${fontSize}px", style = MaterialTheme.typography.titleSmall)
                Slider(value = fontSize.toFloat(), onValueChange = { fontSize = it.toInt() }, valueRange = 14f..32f)
                Text("Tipografía", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    ReaderFont.entries.forEach { f ->
                        FilterChip(selected = font == f, onClick = { font = f }, label = { Text(f.name) })
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
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Cancelar")
            }
        }
    )
}