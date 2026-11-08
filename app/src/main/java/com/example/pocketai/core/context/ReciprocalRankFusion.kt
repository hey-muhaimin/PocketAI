package com.example.pocketai.core.context

/**
 * Fused ranking result containing reciprocal rank score and individual source rankings.
 */
data class FusedRankResult<T>(
    val item: T,
    val score: Double,
    val ftsRank: Int? = null,
    val vectorRank: Int? = null
)

/**
 * Reciprocal Rank Fusion (RRF).
 *
 * Combines ranked result lists from disparate scoring algorithms (e.g., SQLite FTS4 BM25
 * and Dense Vector Cosine Similarity) without requiring score normalization or calibration.
 *
 * Formula:
 *   RRF_score(d) = Σ [ 1.0 / (k + rank_i(d)) ]
 * where rank_i(d) is 1-based (1, 2, 3...) and k is smoothing factor (typically 60).
 */
object ReciprocalRankFusion {

    const val DEFAULT_K: Int = 60

    /**
     * Fuses two ranked lists of identifiers (e.g. chunk IDs) using RRF.
     *
     * @param ftsRankings List of items ordered by FTS keyword match relevance (1st item = rank 1).
     * @param vectorRankings List of items ordered by Dense Vector cosine similarity (1st item = rank 1).
     * @param k Smoothing constant (default 60).
     * @return List of [FusedRankResult] ordered descending by fused score.
     */
    fun <T> fuse(
        ftsRankings: List<T>,
        vectorRankings: List<T>,
        k: Int = DEFAULT_K
    ): List<FusedRankResult<T>> {
        require(k > 0) { "Smoothing constant k must be > 0" }

        val ftsRankMap = mutableMapOf<T, Int>()
        ftsRankings.forEachIndexed { index, item ->
            if (!ftsRankMap.containsKey(item)) {
                ftsRankMap[item] = index + 1
            }
        }

        val vectorRankMap = mutableMapOf<T, Int>()
        vectorRankings.forEachIndexed { index, item ->
            if (!vectorRankMap.containsKey(item)) {
                vectorRankMap[item] = index + 1
            }
        }

        val allItems = LinkedHashSet<T>()
        allItems.addAll(ftsRankings)
        allItems.addAll(vectorRankings)

        val fusedList = mutableListOf<FusedRankResult<T>>()

        for (item in allItems) {
            var totalScore = 0.0
            val ftsRank = ftsRankMap[item]
            val vecRank = vectorRankMap[item]

            if (ftsRank != null) {
                totalScore += 1.0 / (k + ftsRank)
            }
            if (vecRank != null) {
                totalScore += 1.0 / (k + vecRank)
            }

            fusedList.add(
                FusedRankResult(
                    item = item,
                    score = totalScore,
                    ftsRank = ftsRank,
                    vectorRank = vecRank
                )
            )
        }

        return fusedList.sortedWith(
            compareByDescending<FusedRankResult<T>> { it.score }
                .thenBy { it.ftsRank ?: Int.MAX_VALUE }
                .thenBy { it.vectorRank ?: Int.MAX_VALUE }
        )
    }
}
