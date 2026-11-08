package com.example.pocketai.core.context

/**
 * Chunk representation with offset locations.
 */
data class TextChunk(
    val index: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val wordCount: Int
)

/**
 * Sliding token/word window text chunker.
 *
 * @param windowSizeWords Maximum number of words in a chunk (default 256).
 * @param overlapWords Number of overlapping words between consecutive chunks (default 64).
 */
class TextChunker(
    val windowSizeWords: Int = 256,
    val overlapWords: Int = 64
) {
    init {
        require(windowSizeWords > 0) { "windowSizeWords must be > 0" }
        require(overlapWords in 0 until windowSizeWords) { "overlapWords must be >= 0 and < windowSizeWords" }
    }

    /**
     * Splits source text into sliding window chunks while tracking exact character offsets.
     */
    fun chunk(text: String): List<TextChunk> {
        if (text.isBlank()) return emptyList()

        val wordsWithOffsets = extractWordsWithOffsets(text)
        if (wordsWithOffsets.isEmpty()) return emptyList()

        // If total words <= windowSizeWords, return single chunk
        if (wordsWithOffsets.size <= windowSizeWords) {
            val start = wordsWithOffsets.first().startOffset
            val end = wordsWithOffsets.last().endOffset
            return listOf(
                TextChunk(
                    index = 0,
                    text = text.substring(start, end).trim(),
                    startOffset = start,
                    endOffset = end,
                    wordCount = wordsWithOffsets.size
                )
            )
        }

        val chunks = mutableListOf<TextChunk>()
        val step = windowSizeWords - overlapWords
        var chunkIndex = 0
        var wordStart = 0

        while (wordStart < wordsWithOffsets.size) {
            val wordEnd = minOf(wordStart + windowSizeWords, wordsWithOffsets.size)
            val startChar = wordsWithOffsets[wordStart].startOffset
            val endChar = wordsWithOffsets[wordEnd - 1].endOffset

            val chunkText = text.substring(startChar, endChar).trim()
            if (chunkText.isNotBlank()) {
                chunks.add(
                    TextChunk(
                        index = chunkIndex++,
                        text = chunkText,
                        startOffset = startChar,
                        endOffset = endChar,
                        wordCount = wordEnd - wordStart
                    )
                )
            }

            if (wordEnd >= wordsWithOffsets.size) break
            wordStart += step
        }

        return chunks
    }

    private data class WordOffset(val word: String, val startOffset: Int, val endOffset: Int)

    private fun extractWordsWithOffsets(text: String): List<WordOffset> {
        val list = mutableListOf<WordOffset>()
        var inWord = false
        var startIndex = 0

        for (i in text.indices) {
            val isWhitespace = text[i].isWhitespace()
            if (!isWhitespace && !inWord) {
                inWord = true
                startIndex = i
            } else if (isWhitespace && inWord) {
                inWord = false
                list.add(WordOffset(text.substring(startIndex, i), startIndex, i))
            }
        }
        if (inWord) {
            list.add(WordOffset(text.substring(startIndex, text.length), startIndex, text.length))
        }

        return list
    }
}
