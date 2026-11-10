package id.mutqin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardMergeTest {
    private fun log(id: String, ts: String) = ReviewLog(
        id = id, timestamp = ts, userRating = 4, compositeScore = 80, aiGrade = "Lancar",
        mistakeCount = 0, hesitationCount = 0, skippedCount = 0, tajweedCount = 0, durationSeconds = 10,
        previousBox = 1, newBox = 2, nextReviewDate = ts
    )

    private fun card(reviewed: String?, reps: Int, box: Int = 2, history: List<ReviewLog> = emptyList()) = CardProgress(
        pageNumber = 5, box = box, repetitions = reps, lastReviewedDate = reviewed,
        nextReviewDate = "2026-11-01T00:00:00Z", history = history
    )

    @Test
    fun newerReviewWins_inBothDirections() {
        val old = card("2026-10-20T10:00:00Z", reps = 1, box = 2)
        val new = card("2026-10-22T10:00:00Z", reps = 2, box = 3)
        assertEquals(3, CardMerge.merge(old, new).box)
        assertEquals(3, CardMerge.merge(new, old).box)
    }

    @Test
    fun neverReviewedLosesToReviewed() {
        val fresh = card(null, reps = 0, box = 1)
        val reviewed = card("2026-10-22T10:00:00Z", reps = 1, box = 2)
        assertEquals(2, CardMerge.merge(fresh, reviewed).box)
        assertEquals(2, CardMerge.merge(reviewed, fresh).box)
    }

    @Test
    fun tieBreaksOnMoreRepetitions() {
        val a = card("2026-10-22T10:00:00Z", reps = 3, box = 4)
        val b = card("2026-10-22T10:00:00Z", reps = 2, box = 3)
        assertEquals(4, CardMerge.merge(a, b).box)
        assertEquals(4, CardMerge.merge(b, a).box)
    }

    @Test
    fun historiesAreUnionedWithoutDuplicatesNewestFirstAndCapped() {
        val shared = log("rev_1", "2026-10-20T10:00:00Z")
        val onlyA = log("rev_2", "2026-10-21T10:00:00Z")
        val onlyB = log("rev_3", "2026-10-22T10:00:00Z")
        val a = card("2026-10-21T10:00:00Z", 2, history = listOf(onlyA, shared))
        val b = card("2026-10-22T10:00:00Z", 2, history = listOf(onlyB, shared))
        val merged = CardMerge.merge(a, b)
        assertEquals(listOf("rev_3", "rev_2", "rev_1"), merged.history.map { it.id })

        val many = (1..40).map { log("x$it", "2026-09-%02dT10:00:00Z".format((it % 28) + 1)) }
        assertTrue(CardMerge.merge(card("2026-10-22T10:00:00Z", 1, history = many), a).history.size <= 30)
    }

    @Test
    fun identicalCardsStayUnchanged() {
        val a = card("2026-10-22T10:00:00Z", reps = 2, history = listOf(log("rev_1", "2026-10-22T10:00:00Z")))
        assertEquals(a, CardMerge.merge(a, a))
    }
}
