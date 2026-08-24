package com.example.readerapp.domain.model

data class EpubBook(
    val title: String,
    val authors: String,
    val chapters: List<EpubChapter>,
    val totalEstimatedPages: Int = 1
)

data class EpubChapter(
    val id: String,
    val href: String,
    val title: String,
    val estimatedPages: Int = 1,
    val startGlobalPage: Int = 1
)