package com.example.pocketai.core.context

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt

/**
 * Common interface for text embedding engines.
 */
interface Embedder {
    val dimension: Int
    fun embed(text: String): FloatArray
}

/**
 * Deterministic on-device Feature-Hashing vector embedder.
 *
 * Maps arbitrary text to a normalized dense vector in R^d (default d = 256)
 * using the hashing trick over word tokens and character n-grams.
 *
 * Properties:
 * - 100% offline, zero network requests, zero memory model footprint.
 * - Deterministic: identical inputs always produce identical embeddings.
 * - L2 Normalized: dot product directly computes Cosine Similarity.
 */
class FeatureHashEmbedder(
    override val dimension: Int = 256
) : Embedder {

    override fun embed(text: String): FloatArray {
        val vector = FloatArray(dimension)
        if (text.isBlank()) return vector

        val cleanText = text.lowercase(Locale.ROOT)
        val tokens = cleanText.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }

        // 1. Hash word unigrams
        for (token in tokens) {
            val hash = token.hashCode()
            val index = (hash and 0x7FFFFFFF) % dimension
            val sign = if ((hash and 0x80000000.toInt()) != 0) -1.0f else 1.0f
            vector[index] += sign * 1.5f
        }

        // 2. Hash word bigrams
        for (i in 0 until tokens.size - 1) {
            val bigram = "${tokens[i]}_${tokens[i + 1]}"
            val hash = bigram.hashCode()
            val index = (hash and 0x7FFFFFFF) % dimension
            val sign = if ((hash and 0x80000000.toInt()) != 0) -1.0f else 1.0f
            vector[index] += sign * 2.0f
        }

        // 3. Hash character 3-grams for subword / typo robustness
        val trimmed = cleanText.replace("\\s+".toRegex(), " ")
        if (trimmed.length >= 3) {
            for (i in 0..trimmed.length - 3) {
                val tri = trimmed.substring(i, i + 3)
                val hash = tri.hashCode()
                val index = (hash and 0x7FFFFFFF) % dimension
                val sign = if ((hash and 0x80000000.toInt()) != 0) -1.0f else 1.0f
                vector[index] += sign * 0.5f
            }
        }

        // L2 Normalize
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)
        if (norm > 1e-6f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector
    }

    companion object {
        /**
         * Calculates cosine similarity between two normalized vectors (dot product).
         */
        fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
            if (v1.size != v2.size || v1.isEmpty()) return 0.0f
            var dot = 0.0f
            for (i in v1.indices) {
                dot += v1[i] * v2[i]
            }
            return dot.coerceIn(-1.0f, 1.0f)
        }

        /**
         * Serializes FloatArray into ByteArray for Room BLOB storage.
         */
        fun toByteArray(vector: FloatArray): ByteArray {
            val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (f in vector) {
                buffer.putFloat(f)
            }
            return buffer.array()
        }

        /**
         * Deserializes ByteArray back into FloatArray from Room BLOB.
         */
        fun fromByteArray(bytes: ByteArray): FloatArray {
            if (bytes.isEmpty() || bytes.size % 4 != 0) return FloatArray(0)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val floats = FloatArray(bytes.size / 4)
            for (i in floats.indices) {
                floats[i] = buffer.float
            }
            return floats
        }
    }
}
