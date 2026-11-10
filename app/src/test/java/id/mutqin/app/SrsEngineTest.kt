package id.mutqin.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Paritas dengan rumus di versi web (src/services/srsEngine.ts). */
class SrsEngineTest {
    private fun eval(rating: Int, m: Int = 0, h: Int = 0, s: Int = 0, t: Int = 0, box: Int = 2, streak: Int = 0) =
        SrsEngine.evaluate(EvaluationInput(rating, m, h, s, t, box, streak))

    @Test
    fun perfectRatingPromotes() {
        val r = eval(5)
        assertEquals(100, r.compositeScore)
        assertEquals("Sangat Lancar", r.aiGrade)
        assertEquals(3, r.recommendedBox)
        assertEquals(7, r.intervalDays)
    }

    @Test
    fun oneMistakeStillPromotesWhenRatingHigh() {
        val r = eval(4, m = 1) // 88 - 12 = 76
        assertEquals(76, r.compositeScore)
        assertEquals("Lancar", r.aiGrade)
        assertEquals(3, r.recommendedBox)
    }

    @Test
    fun skippedKeepsBox() {
        val r = eval(2, s = 1) // 64 - 10 = 54
        assertEquals(54, r.compositeScore)
        assertEquals("Cukup", r.aiGrade)
        assertEquals(2, r.recommendedBox)
    }

    @Test
    fun twoMistakesDropToBoxOne() {
        val r = eval(2, m = 2) // 64 - 24 = 40
        assertEquals(40, r.compositeScore)
        assertEquals("Kurang Lancar", r.aiGrade)
        assertEquals(1, r.recommendedBox)
        assertEquals(1, r.intervalDays)
    }

    @Test
    fun scoreNeverBelowTen() {
        val r = eval(1, m = 10)
        assertEquals(10, r.compositeScore)
        assertEquals("Perlu Diulang", r.aiGrade)
    }

    @Test
    fun boxSevenIsOneHundredTwentyDays() {
        assertEquals(120, Leitner.intervalFor(7))
        assertEquals(7, eval(5, box = 7).recommendedBox)
    }

    @Test
    fun partialLapseDropsTwoBoxesNotToBoxOne() {
        val r = eval(2, m = 2, box = 5) // skor 40 = Kurang Lancar
        assertEquals("Kurang Lancar", r.aiGrade)
        assertEquals(3, r.recommendedBox)
        assertEquals(7, r.intervalDays)
        assertEquals(5, eval(2, m = 2, box = 7).recommendedBox)
        assertEquals(1, eval(2, m = 2, box = 2).recommendedBox)
    }

    @Test
    fun totalFailureStillGoesToBoxOne() {
        val r = eval(1, m = 2, box = 6) // skor < 35 = Perlu Diulang
        assertEquals("Perlu Diulang", r.aiGrade)
        assertEquals(1, r.recommendedBox)
    }

    @Test
    fun selfRatingNeedsRepeatIsRespectedEvenWithoutLabels() {
        val r = eval(1, box = 7)
        assertEquals("Perlu Diulang", r.aiGrade)
        assertEquals(1, r.recommendedBox)
    }

    @Test
    fun secondConsecutiveCukupPromotesSoReviewsNeverGetStuck() {
        assertEquals(2, eval(2, box = 2).recommendedBox)            // Cukup pertama: tetap
        assertEquals(3, eval(2, box = 2, streak = 1).recommendedBox) // Cukup kedua berturut-turut: naik
        assertEquals(7, eval(2, box = 7, streak = 1).recommendedBox) // tidak melewati Box 7
    }

    private fun log(grade: String, from: Int, to: Int) = ReviewLog(
        id = "r", timestamp = "2026-10-20T10:00:00Z", userRating = 3, compositeScore = 60, aiGrade = grade,
        mistakeCount = 0, hesitationCount = 0, skippedCount = 0, tajweedCount = 0, durationSeconds = 10,
        previousBox = from, newBox = to, nextReviewDate = "2026-10-21T10:00:00Z"
    )

    @Test
    fun cukupStreakCountsOnlyConsecutiveStationaryCukup() {
        assertEquals(0, SrsEngine.cukupStreak(emptyList()))
        assertEquals(1, SrsEngine.cukupStreak(listOf(log("Cukup", 2, 2), log("Lancar", 1, 2))))
        assertEquals(2, SrsEngine.cukupStreak(listOf(log("Cukup", 2, 2), log("Cukup", 2, 2), log("Lancar", 1, 2))))
        assertEquals(0, SrsEngine.cukupStreak(listOf(log("Cukup", 2, 3), log("Cukup", 2, 2)))) // yang terakhir sudah menaikkan box
        assertEquals(0, SrsEngine.cukupStreak(listOf(log("Lancar", 2, 3), log("Cukup", 2, 2))))
    }
}
