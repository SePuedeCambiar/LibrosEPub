package com.example.readerapp.domain.model

data class ReadingProgress(
    val bookPath: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val totalPagesInChapter: Int,
    val progressPercent: Float,
    val lastReadTimestamp: Long = System.currentTimeMillis()
)
