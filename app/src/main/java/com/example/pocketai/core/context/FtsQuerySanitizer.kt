package com.example.pocketai.core.context

import java.util.Locale

/**
 * Sanitizer for user search inputs targeting SQLite FTS4 virtual tables.
 * Prevents syntax exceptions and malformed queries caused by unescaped quotes,
 * operators (AND, OR, NOT, NEAR), or punctuation.
 */
object FtsQuerySanitizer {

    private val RESERVED_FTS_WORDS = setOf("and", "or", "not", "near")

    /**
     * Transforms an arbitrary user query string into a safe, valid FTS4 MATCH query.
     * Returns an empty string if no valid search terms remain.
     */
    fun sanitize(rawQuery: String): String {
        if (rawQuery.isBlank()) return ""

        // Extract alphanumeric tokens
        val tokens = rawQuery
            .replace(Regex("[^a-zA-Z0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) return ""

        val validTerms = mutableListOf<String>()

        for (token in tokens) {
            val lower = token.lowercase(Locale.ROOT)
            // If the token is a bare reserved word, quote it explicitly so it's treated as a literal search term
            if (RESERVED_FTS_WORDS.contains(lower)) {
                validTerms.add("\"$lower\"")
            } else {
                // Use wildcard prefix matching for interactive user search
                validTerms.add("\"$token\"*")
            }
        }

        return if (validTerms.isEmpty()) "" else validTerms.joinToString(" AND ")
    }
}
