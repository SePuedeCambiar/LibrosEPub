package com.example.readerapp.data.local

import android.util.Log
import android.util.Xml
import com.example.readerapp.domain.model.EpubBook
import com.example.readerapp.domain.model.EpubChapter
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

class EpubParser {
    private val tag = "EpubParser"

    fun parseEpub(file: File): EpubBook? {
        return try {
            ZipFile(file).use { zip ->
                val opfPath = findOpfPath(zip) ?: return null
                val opfEntry = zip.getEntry(opfPath) ?: return null
                val opfDir = File(opfPath).parent?.let { "$it/" } ?: ""

                val opfContent = zip.getInputStream(opfEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseOpf(opfContent, opfDir, file.nameWithoutExtension, zip)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parseando EPUB: ${e.message}", e)
            null
        }
    }

    fun getChapterContent(file: File, chapter: EpubChapter): String {
        return try {
            ZipFile(file).use { zip ->
                val cleanHref = chapter.href.substringBefore("#")
                val decodedHref = try { URLDecoder.decode(cleanHref, "UTF-8") } catch (_: Exception) { cleanHref }

                val entry = zip.getEntry(cleanHref)
                    ?: zip.getEntry(decodedHref)
                    ?: zip.entries().asSequence().firstOrNull {
                        it.name.equals(cleanHref, ignoreCase = true) || it.name.equals(decodedHref, ignoreCase = true)
                    }
                    ?: return "<p>Página no disponible.</p>"

                zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error extrayendo ${chapter.id}: ${e.message}", e)
            "<p>Error al cargar el contenido.</p>"
        }
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val containerEntry = zip.getEntry("META-INF/container.xml")
            ?: zip.entries().asSequence().firstOrNull { it.name.equals("META-INF/container.xml", ignoreCase = true) }
            ?: return null

        val containerContent = zip.getInputStream(containerEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val parser = Xml.newPullParser()
        parser.setInput(containerContent.reader())

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                val fullPath = parser.getAttributeValue(null, "full-path")
                if (!fullPath.isNullOrBlank()) return fullPath
            }
            eventType = parser.next()
        }
        return null
    }

    private fun parseOpf(opfContent: String, opfDir: String, fallbackTitle: String, zip: ZipFile): EpubBook {
        val parser = Xml.newPullParser()
        parser.setInput(opfContent.reader())

        var title: String? = null
        var author: String? = null
        var ncxHref: String? = null
        val manifest = mutableMapOf<String, String>()
        val spineIds = mutableListOf<String>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val localName = parser.name.substringAfterLast(":")
                when {
                    localName.equals("title", ignoreCase = true) && title == null -> {
                        title = try { parser.nextText() } catch (_: Exception) { null }
                    }
                    localName.equals("creator", ignoreCase = true) && author == null -> {
                        author = try { parser.nextText() } catch (_: Exception) { null }
                    }
                    localName.equals("item", ignoreCase = true) -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        val mediaType = parser.getAttributeValue(null, "media-type")
                        if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                            val cleanHref = href.substringBefore("#")
                            val normalized = normalizeZipPath(opfDir, cleanHref)
                            manifest[id] = normalized

                            if (mediaType == "application/x-dtbncx+xml" || id.equals("ncx", ignoreCase = true)) {
                                ncxHref = normalized
                            }
                        }
                    }
                    localName.equals("itemref", ignoreCase = true) -> {
                        val idref = parser.getAttributeValue(null, "idref")
                        if (!idref.isNullOrBlank()) {
                            spineIds.add(idref)
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val tocTitles = if (ncxHref != null) parseNcx(zip, ncxHref) else emptyMap()

        var currentGlobalPageCounter = 1
        val spine = mutableListOf<EpubChapter>()

        spineIds.forEach { idref ->
            val href = manifest[idref]
            if (href != null) {
                val cleanHref = href.substringBefore("#")
                val chapterTitle = tocTitles[cleanHref]
                    ?: when {
                        idref.contains("cover", ignoreCase = true) || href.contains("cover", ignoreCase = true) || href.contains("wrap", ignoreCase = true) -> "Portada"
                        idref.contains("header", ignoreCase = true) -> "Información de Edición"
                        idref.contains("toc", ignoreCase = true) -> "Índice"
                        else -> "Sección ${spine.size + 1}"
                    }

                // Estimar páginas por tamaño de archivo (~1800 bytes = 1 página de lectura estándar)
                val entrySize = zip.getEntry(cleanHref)?.size ?: 3000L
                val estPages = maxOf(1, (entrySize / 1800).toInt())

                spine.add(
                    EpubChapter(
                        id = idref,
                        href = href,
                        title = chapterTitle,
                        estimatedPages = estPages,
                        startGlobalPage = currentGlobalPageCounter
                    )
                )
                currentGlobalPageCounter += estPages
            }
        }

        val totalPages = maxOf(1, currentGlobalPageCounter - 1)
        val finalTitle = if (!title.isNullOrBlank()) title.trim() else fallbackTitle.replace("_", " ")
        val finalAuthor = if (!author.isNullOrBlank()) author.trim() else "Autor Desconocido"

        return EpubBook(finalTitle, finalAuthor, spine, totalPages)
    }

    private fun parseNcx(zip: ZipFile, ncxPath: String): Map<String, String> {
        val titles = mutableMapOf<String, String>()
        try {
            val entry = zip.getEntry(ncxPath) ?: return titles
            val content = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parser = Xml.newPullParser()
            parser.setInput(content.reader())

            var currentLabel = ""
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tag = parser.name.substringAfterLast(":")
                    if (tag.equals("text", ignoreCase = true)) {
                        currentLabel = try { parser.nextText() } catch (_: Exception) { "" }
                    } else if (tag.equals("content", ignoreCase = true)) {
                        val src = parser.getAttributeValue(null, "src")?.substringBefore("#")
                        if (!src.isNullOrBlank() && currentLabel.isNotBlank()) {
                            val ncxDir = File(ncxPath).parent?.let { "$it/" } ?: ""
                            val normalized = normalizeZipPath(ncxDir, src)
                            titles[normalized] = currentLabel.trim()
                            currentLabel = ""
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(tag, "No se pudo leer TOC NCX: ${e.message}")
        }
        return titles
    }

    private fun normalizeZipPath(baseDir: String, href: String): String {
        val raw = if (baseDir.isNotEmpty() && !href.startsWith("/")) baseDir + href else href.removePrefix("/")
        return File(raw).normalize().path.replace("\\", "/").removePrefix("/")
    }
}