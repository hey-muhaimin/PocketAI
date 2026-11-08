package com.example.pocketai

import com.example.pocketai.core.context.ReciprocalRankFusion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReciprocalRankFusionTest {

    @Test
    fun testEmptyRankings() {
        val fused = ReciprocalRankFusion.fuse<Long>(emptyList(), emptyList())
        assertTrue(fused.isEmpty())
    }

    @Test
    fun testItemInBothListsOutranksSingleLists() {
        val fts = listOf(10L, 20L, 30L)
        val vec = listOf(20L, 40L, 50L)

        // Item 20L is #2 in FTS and #1 in Vec -> 1/(60+2) + 1/(60+1) = 1/62 + 1/61 ≈ 0.0161 + 0.0163 = 0.0325
        // Item 10L is #1 in FTS only -> 1/(60+1) ≈ 0.0163
        // Item 40L is #2 in Vec only -> 1/(60+2) ≈ 0.0161
        val fused = ReciprocalRankFusion.fuse(fts, vec, k = 60)

        assertEquals(20L, fused[0].item)
        assertTrue(fused[0].score > fused[1].score)
        assertEquals(10L, fused[1].item)
    }

    @Test
    fun testRankTieBreakingPreservesOrder() {
        val fts = listOf(1L, 2L)
        val vec = listOf(3L, 4L)
        val fused = ReciprocalRankFusion.fuse(fts, vec, k = 60)

        assertEquals(4, fused.size)
        // 1L has rank 1 in FTS, 3L has rank 1 in Vec -> same score 1/61
        assertEquals(1.0 / 61.0, fused[0].score, 1e-6)
        assertEquals(1.0 / 61.0, fused[1].score, 1e-6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidKThrows() {
        ReciprocalRankFusion.fuse(listOf(1L), listOf(2L), k = 0)
    }
}
