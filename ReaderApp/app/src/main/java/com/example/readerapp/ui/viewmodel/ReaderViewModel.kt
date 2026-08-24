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

    var settings by mutableStateOf(preferences.getReaderSettings())
        private set

    inner class WebAppInterface {
        @JavascriptInterface
        fun onPaginationReady(total: Int, current: Int) {
            Log.d(tag, "📡 [Bridge] Paginación lista -> Total: $total, Página actual: $current")
            chapterTotalPages = maxOf(1, total)
            chapterCurrentPage = current
            updateGlobalPage()
        }

        @JavascriptInterface
        fun onPageChanged(current: Int, total: Int) {
            Log.d(tag, "📡 [Bridge] Página cambiada -> ${current + 1} / $total")
            chapterCurrentPage = current
            chapterTotalPages = maxOf(1, total)
            updateGlobalPage()
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
            } else null
        } catch (e: Exception) {
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
                Log.d(tag, "📖 Libro cargado: ${parsedBook.title}")
                loadChapter(0, 0)
            }
        }
    }

    fun loadChapter(index: Int, initialPage: Int = 0) {
        val currentBook = book ?: return
        if (index !in currentBook.chapters.indices) return

        currentChapterIndex = index
        targetPageInChapter = initialPage

        viewModelScope.launch(Dispatchers.IO) {
            val chapter = currentBook.chapters[index]
            currentChapterHref = chapter.href
            Log.d(tag, "🔄 Cargando capítulo $index: '${chapter.title}'")
            val rawHtml = parser.getChapterContent(bookFile, chapter)
            currentHtmlContent = buildPaginatedHtml(rawHtml, targetPageInChapter)
        }
    }

    private fun updateGlobalPage() {
        val currentBook = book ?: return
        val chapter = currentBook.chapters.getOrNull(currentChapterIndex) ?: return
        val offset = (chapterCurrentPage.toFloat() / maxOf(1, chapterTotalPages) * chapter.estimatedPages).toInt()
        globalCurrentPage = (chapter.startGlobalPage + offset).coerceIn(1, currentBook.totalEstimatedPages)
    }

    fun onNextChapterRequested() {
        val totalChapters = book?.chapters?.size ?: 0
        if (currentChapterIndex < totalChapters - 1) {
            loadChapter(currentChapterIndex + 1, initialPage = 0)
        }
    }

    fun onPrevChapterRequested() {
        if (currentChapterIndex > 0) {
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

    private fun buildPaginatedHtml(rawHtml: String, startPage: Int): String {
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

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * {
                        box-sizing: border-box !important;
                    }
                    html, body {
                        margin: 0 !important;
                        padding: 0 !important;
                        background-color: $bgColor !important;
                        color: $textColor !important;
                        font-family: $fontFace !important;
                        font-size: ${settings.fontSize}px !important;
                        line-height: 1.7 !important;
                        overflow-x: hidden !important;
                    }
                    #book-container {
                        padding: 24px 20px 90px 20px !important;
                        text-align: justify !important;
                        word-break: break-word !important;
                    }
                    #book-container * {
                        color: inherit !important;
                        max-width: 100% !important;
                    }
                    p {
                        text-indent: 1.2em !important;
                        margin-top: 0.45em !important;
                        margin-bottom: 0.45em !important;
                    }
                    h1, h2, h3, h4 {
                        text-align: center !important;
                        text-indent: 0 !important;
                        margin: 1em 0 0.5em 0 !important;
                    }
                    img, svg {
                        max-width: 100% !important;
                        height: auto !important;
                        display: block !important;
                        margin: 16px auto !important;
                    }
                </style>
                <script>
                    var totalPages = 1;
                    var currentPage = 0;
                    var pageStep = 0;

                    function initPages(target) {
                        try {
                            var viewH = window.innerHeight || document.documentElement.clientHeight || 700;
                            var totalH = Math.max(document.documentElement.scrollHeight, document.body.scrollHeight);
                            
                            // Altura de 1 página de lectura (dejando 50px de overlap para lectura continua)
                            pageStep = Math.max(200, viewH - 50);
                            totalPages = Math.max(1, Math.ceil(totalH / pageStep));
                            
                            console.log("📊 [Páginas] AlturaTotal=" + totalH + ", Paso=" + pageStep + " -> Páginas=" + totalPages);

                            if (target === -1) {
                                currentPage = totalPages - 1;
                            } else {
                                currentPage = Math.min(Math.max(0, target), totalPages - 1);
                            }

                            applyScroll();

                            if (window.AndroidBridge) {
                                window.AndroidBridge.onPaginationReady(totalPages, currentPage);
                            }
                        } catch (e) {
                            console.error("❌ Error en initPages: " + e.message);
                        }
                    }

                    function applyScroll() {
                        var targetY = currentPage * pageStep;
                        window.scrollTo({
                            top: targetY,
                            left: 0,
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

                    window.addEventListener('load', function() {
                        setTimeout(function() { initPages($startPage); }, 80);
                    });
                </script>
            </head>
            <body>
                <div id="book-container">
                    $bodyContent
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}