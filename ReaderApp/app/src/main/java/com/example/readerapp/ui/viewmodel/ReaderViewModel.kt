package com.example.readerapp.ui.viewmodel

import android.app.Application
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readerapp.data.local.AppPreferences
import com.example.readerapp.data.local.EpubParser
import com.example.readerapp.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipFile

class ReaderViewModel(
    application: Application,
    val bookFile: File
) : AndroidViewModel(application) {

    private val tag = "ReaderViewModel"
    private val preferences = AppPreferences(application)
    private val parser = EpubParser()
    private var zipInstance: ZipFile? = null

    var book by mutableStateOf<EpubBook?>(null)
        private set

    var currentChapterIndex by mutableIntStateOf(0)
        private set

    var currentHtmlContent by mutableStateOf("")
        private set

    var currentChapterHref by mutableStateOf("")
        private set

    var targetPageInChapter by mutableIntStateOf(0)
        private set

    var chapterCurrentPage by mutableIntStateOf(0)
        private set

    var chapterTotalPages by mutableIntStateOf(1)
        private set

    var globalCurrentPage by mutableIntStateOf(1)
        private set

    var isChapterLoading by mutableStateOf(false)
        private set

    var settings by mutableStateOf(preferences.getReaderSettings())
        private set

    inner class WebAppInterface {
        @JavascriptInterface
        fun onPaginationReady(total: Int, current: Int) {
            chapterTotalPages = maxOf(1, total)
            chapterCurrentPage = current
            isChapterLoading = false
            updateGlobalPage()
            Log.d(tag, "📡 [Bridge] Paginación lista -> Cap ${currentChapterIndex + 1}: Pág ${current + 1} de $total")
        }

        @JavascriptInterface
        fun onPageChanged(current: Int, total: Int) {
            chapterCurrentPage = current
            chapterTotalPages = maxOf(1, total)
            updateGlobalPage()
            Log.d(tag, "📡 [Bridge] Pág: ${current + 1} / $total (Capítulo ${currentChapterIndex + 1})")
        }
    }

    val jsInterface = WebAppInterface()

    init {
        loadBook()
    }

    @Synchronized
    fun getAssetInputStream(path: String): Pair<String, InputStream>? {
        return try {
            if (zipInstance == null) {
                zipInstance = ZipFile(bookFile)
            }
            val zip = zipInstance ?: return null
            val clean = path.substringBefore("#")
            val decoded = try { URLDecoder.decode(clean, "UTF-8") } catch (_: Exception) { clean }

            val entry = zip.getEntry(clean)
                ?: zip.getEntry(decoded)
                ?: zip.entries().asSequence().firstOrNull {
                    it.name.equals(clean, ignoreCase = true) || it.name.equals(decoded, ignoreCase = true)
                }

            if (entry != null) {
                val ext = File(clean).extension.lowercase()
                val mime = when (ext) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "svg" -> "image/svg+xml"
                    "css" -> "text/css"
                    else -> "application/octet-stream"
                }
                Pair(mime, zip.getInputStream(entry))
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Error leyendo recurso EPUB ($path): ${e.message}")
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            zipInstance?.close()
            zipInstance = null
        } catch (_: Exception) {}
    }

    private fun loadBook() {
        viewModelScope.launch(Dispatchers.IO) {
            val parsedBook = parser.parseEpub(bookFile)
            book = parsedBook
            if (parsedBook != null && parsedBook.chapters.isNotEmpty()) {
                Log.d(tag, "📖 Libro cargado: '${parsedBook.title}' (${parsedBook.chapters.size} capítulos)")
                loadChapter(0, 0)
            } else {
                Log.e(tag, "❌ Error: No se pudo parsear el archivo EPUB")
            }
        }
    }

    fun loadChapter(index: Int, initialPage: Int = 0) {
        val currentBook = book ?: return
        if (index !in currentBook.chapters.indices) return

        isChapterLoading = true
        currentChapterIndex = index
        targetPageInChapter = initialPage

        viewModelScope.launch(Dispatchers.IO) {
            val chapter = currentBook.chapters[index]
            currentChapterHref = chapter.href
            Log.d(tag, "🔄 Cargando capítulo $index ('${chapter.title}')")
            val rawHtml = parser.getChapterContent(bookFile, chapter)
            currentHtmlContent = buildExactGridHtml(rawHtml, targetPageInChapter)
        }
    }

    private fun updateGlobalPage() {
        val currentBook = book ?: return
        val chapter = currentBook.chapters.getOrNull(currentChapterIndex) ?: return
        val offset = (chapterCurrentPage.toFloat() / maxOf(1, chapterTotalPages) * chapter.estimatedPages).toInt()
        globalCurrentPage = (chapter.startGlobalPage + offset).coerceIn(1, currentBook.totalEstimatedPages)
    }

    fun onNextChapterRequested() {
        if (isChapterLoading) return
        val totalChapters = book?.chapters?.size ?: 0
        if (currentChapterIndex < totalChapters - 1) {
            Log.d(tag, "⏭️ Avanzando al capítulo: ${currentChapterIndex + 1}")
            loadChapter(currentChapterIndex + 1, initialPage = 0)
        }
    }

    fun onPrevChapterRequested() {
        if (isChapterLoading) return
        if (currentChapterIndex > 0) {
            Log.d(tag, "⏮️ Retrocediendo al capítulo: ${currentChapterIndex - 1}")
            loadChapter(currentChapterIndex - 1, initialPage = -1)
        }
    }

    fun updateSettings(newSettings: ReaderSettings) {
        settings = newSettings
        preferences.saveReaderSettings(newSettings)
        loadChapter(currentChapterIndex, chapterCurrentPage)
    }

    private fun extractBodyContent(rawHtml: String): String {
        val bodyRegex = "(?is)<body[^>]*>(.*?)</body>".toRegex()
        val match = bodyRegex.find(rawHtml)
        return if (match != null) {
            match.groupValues[1]
        } else {
            rawHtml.replace("(?i)<!DOCTYPE[^>]*>".toRegex(), "")
                .replace("(?i)<html[^>]*>".toRegex(), "")
                .replace("(?i)</html>".toRegex(), "")
                .replace("(?i)<head[^>]*>.*?</head>".toRegex(), "")
        }
    }

    private fun buildExactGridHtml(rawHtml: String, startPage: Int): String {
        val bodyContent = extractBodyContent(rawHtml)

        val bgColor = when (settings.theme) {
            ReaderTheme.LIGHT -> "#FAF8F5"
            ReaderTheme.DARK -> "#141414"
            ReaderTheme.SEPIA -> "#F4ECD8"
        }

        val textColor = when (settings.theme) {
            ReaderTheme.LIGHT -> "#1A1A1A"
            ReaderTheme.DARK -> "#D6D6D6"
            ReaderTheme.SEPIA -> "#4A3B32"
        }

        val fontFace = when (settings.font) {
            ReaderFont.SANS -> "sans-serif"
            ReaderFont.SERIF -> "Georgia, serif"
            ReaderFont.MONOSPACE -> "monospace"
        }

        val fontSize = settings.fontSize
        val lineHeight = Math.round(fontSize * 1.6).toInt()

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    /* 1. Reset estructural */
                    head, style, script, link, meta, title {
                        display: none !important;
                    }

                    html {
                        margin: 0 !important;
                        padding: 0 !important;
                        width: 100vw !important;
                        height: 100vh !important;
                        background-color: $bgColor !important;
                        overflow-x: scroll !important;
                        overflow-y: hidden !important;
                        scrollbar-width: none !important;
                    }

                    html::-webkit-scrollbar, body::-webkit-scrollbar {
                        display: none !important;
                        width: 0 !important;
                        height: 0 !important;
                    }

                    /* 2. El cuerpo es el lienzo de columnas horizontales */
                    body {
                        box-sizing: border-box !important;
                        margin: 0 !important;
                        padding: 44px 0 36px 0 !important;
                        height: 100vh !important;
                        background-color: $bgColor !important;
                        color: $textColor !important;
                        font-family: $fontFace !important;
                        font-size: ${fontSize}px !important;
                        line-height: ${lineHeight}px !important;
                        text-align: justify !important;
                        word-break: break-word !important;

                        /* PAGINACIÓN ESTÁNDAR READIUM: 1 columna = 100vw */
                        column-width: 100vw !important;
                        column-gap: 0px !important;
                        column-fill: auto !important;
                    }

                    /* 3. Márgenes internos de lectura por página */
                    body > *, p, h1, h2, h3, h4, div, section, article, ul, ol, blockquote {
                        margin-left: 24px !important;
                        margin-right: 24px !important;
                        max-width: calc(100vw - 48px) !important;
                        box-sizing: border-box !important;
                    }

                    * {
                        color: inherit !important;
                        font-family: inherit !important;
                    }

                    p {
                        margin-top: 0 !important;
                        margin-bottom: ${lineHeight / 2}px !important;
                        padding: 0 !important;
                        text-indent: 1.4em !important;
                        line-height: ${lineHeight}px !important;
                        break-inside: auto !important;
                    }

                    p:empty { display: none !important; }

                    h1, h2, h3, h4 {
                        text-align: center !important;
                        text-indent: 0 !important;
                        line-height: ${lineHeight * 1.4}px !important;
                        margin-top: ${lineHeight}px !important;
                        margin-bottom: ${lineHeight}px !important;
                        padding: 0 !important;
                        break-inside: avoid !important;
                        break-after: avoid !important;
                    }

                    /* 4. Soporte para Portadas SVG e Imágenes */
                    svg {
                        width: 100% !important;
                        max-width: calc(100vw - 48px) !important;
                        height: calc(100vh - 100px) !important;
                        max-height: calc(100vh - 100px) !important;
                        display: block !important;
                        margin: 0 auto !important;
                    }

                    svg image {
                        width: 100% !important;
                        height: 100% !important;
                    }

                    img {
                        max-width: calc(100vw - 48px) !important;
                        max-height: calc(100vh - 100px) !important;
                        height: auto !important;
                        display: block !important;
                        margin: 10px auto !important;
                        object-fit: contain !important;
                        break-inside: avoid !important;
                    }
                </style>
            </head>
            <body>
                $bodyContent

                <script>
                    var totalPages = 1;
                    var currentPage = 0;
                    var pageWidth = window.innerWidth;

                    function initPages(target) {
                        try {
                            pageWidth = window.innerWidth;
                            var scrollW = document.scrollingElement.scrollWidth || document.documentElement.scrollWidth || document.body.scrollWidth;
                            
                            totalPages = Math.max(1, Math.round(scrollW / pageWidth));

                            if (target === -1) {
                                currentPage = totalPages - 1;
                            } else {
                                currentPage = Math.min(Math.max(0, target), totalPages - 1);
                            }

                            applyScroll();

                            console.log("📖 [Lector Listo] Ancho Total: " + scrollW + " | Páginas: " + totalPages + " | Pág Actual: " + (currentPage + 1));

                            if (window.AndroidBridge) {
                                window.AndroidBridge.onPaginationReady(totalPages, currentPage);
                            }
                        } catch (e) {
                            console.error("Error initPages: " + e.message);
                        }
                    }

                    function applyScroll() {
                        var targetX = currentPage * pageWidth;
                        window.scrollTo({
                            left: targetX,
                            top: 0,
                            behavior: 'instant'
                        });
                    }

                    function nextPage() {
                        if (currentPage < totalPages - 1) {
                            currentPage++;
                            applyScroll();
                            if (window.AndroidBridge) {
                                window.AndroidBridge.onPageChanged(currentPage, totalPages);
                            }
                            return true;
                        }
                        return false;
                    }

                    function prevPage() {
                        if (currentPage > 0) {
                            currentPage--;
                            applyScroll();
                            if (window.AndroidBridge) {
                                window.AndroidBridge.onPageChanged(currentPage, totalPages);
                            }
                            return true;
                        }
                        return false;
                    }

                    window.addEventListener('DOMContentLoaded', function() {
                        initPages($startPage);
                    });

                    window.addEventListener('load', function() {
                        setTimeout(function() { initPages($startPage); }, 80);
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}