package com.example.pocketai

import com.example.pocketai.core.context.FtsQuerySanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FtsQuerySanitizerTest {

    @Test
    fun testEmptyOrPunctuationOnly() {
        assertEquals("", FtsQuerySanitizer.sanitize(""))
        assertEquals("", FtsQuerySanitizer.sanitize("   "))
        assertEquals("", FtsQuerySanitizer.sanitize("!@#$%^&*()_+=-~`{}[]:;\"'<>?,./"))
    }

    @Test
    fun testNormalQueryPrefixWildcard() {
        val sanitized = FtsQuerySanitizer.sanitize("pocket ai android")
        assertEquals("\"pocket\"* AND \"ai\"* AND \"android\"*", sanitized)
    }

    @Test
    fun testReservedWordsHandling() {
        val sanitized = FtsQuerySanitizer.sanitize("cats AND dogs OR near")
        // Reserved words must be quoted as literals without wildcard to prevent syntax errors
        assertTrue(sanitized.contains("\"and\""))
        assertTrue(sanitized.contains("\"or\""))
        assertTrue(sanitized.contains("\"near\""))
        assertTrue(sanitized.contains("\"cats\"*"))
        assertTrue(sanitized.contains("\"dogs\"*"))
    }

    @Test
    fun testSpecialCharactersStrippedSafely() {
        val sanitized = FtsQuerySanitizer.sanitize("hello (world) [test] {123} -flag *star")
        assertEquals("\"hello\"* AND \"world\"* AND \"test\"* AND \"123\"* AND \"flag\"* AND \"star\"*", sanitized)
    }
}
