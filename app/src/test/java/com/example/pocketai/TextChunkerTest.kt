package com.example.pocketai

import com.example.pocketai.core.context.TextChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {

    @Test
    fun testEmptyAndBlankText() {
        val chunker = TextChunker(windowSizeWords = 50, overlapWords = 10)
        assertTrue(chunker.chunk("").isEmpty())
        assertTrue(chunker.chunk("   \n\t  ").isEmpty())
    }

    @Test
    fun testShortTextSingleChunk() {
        val chunker = TextChunker(windowSizeWords = 20, overlapWords = 5)
        val text = "PocketAI is an on-device private AI runtime for Android."
        val chunks = chunker.chunk(text)

        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].index)
        assertEquals(text, chunks[0].text)
        assertEquals(0, chunks[0].startOffset)
        assertEquals(text.length, chunks[0].endOffset)
        assertEquals(9, chunks[0].wordCount)
    }

    @Test
    fun testSlidingWindowWithOverlap() {
        val chunker = TextChunker(windowSizeWords = 10, overlapWords = 4)
        // 20 words
        val words = (1..20).map { "word$it" }
        val text = words.joinToString(" ")
        val chunks = chunker.chunk(text)

        // Step = 10 - 4 = 6.
        // Chunk 0: words 1..10
        // Chunk 1: words 7..16
        // Chunk 2: words 13..20
        assertTrue("Expected >= 2 chunks, got ${chunks.size}", chunks.size >= 2)
        assertEquals(0, chunks[0].index)
        assertTrue(chunks[0].text.startsWith("word1"))
        assertTrue(chunks[0].text.endsWith("word10"))

        assertTrue(chunks[1].text.startsWith("word7"))
        assertTrue(chunks[1].text.contains("word10"))
    }

    @Test
    fun testOffsetsAreExactSubstrings() {
        val chunker = TextChunker(windowSizeWords = 5, overlapWords = 2)
        val text = "Alpha beta gamma delta epsilon zeta eta theta iota kappa."
        val chunks = chunker.chunk(text)

        for (c in chunks) {
            val extracted = text.substring(c.startOffset, c.endOffset).trim()
            assertEquals(c.text, extracted)
        }
    }
}
