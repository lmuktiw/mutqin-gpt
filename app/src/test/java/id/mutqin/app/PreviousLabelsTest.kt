package id.mutqin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviousLabelsTest {
    private fun word(page: Int, ayah: Int, pos: Int, text: String) =
        QuranWord("p${page}_s2_a${ayah}_w$pos", text, ayah, pos)

    private val ayahs = listOf(
        QuranAyah(2, 1, listOf(word(2, 1, 1, "الم"), word(2, 1, 2, "ذلك"), word(2, 1, 3, "ذلك"))),
        QuranAyah(2, 2, listOf(word(2, 2, 1, "هدى"), word(2, 2, 2, "للمتقين")))
    )

    private fun log(vararg labeled: LabeledWord) = ReviewLog(
        id = "r1", timestamp = "2026-10-20T10:00:00Z", userRating = 3, compositeScore = 70, aiGrade = "Cukup",
        mistakeCount = 0, hesitationCount = 0, skippedCount = 0, tajweedCount = 0, durationSeconds = 30,
        previousBox = 1, newBox = 1, nextReviewDate = "2026-10-21T10:00:00Z", labeledWordsSummary = labeled.toList()
    )

    @Test
    fun noLogOrNoLabelsGivesEmptyMap() {
        assertEquals(emptyMap<String, ErrorLabel>(), PreviousLabels.map(ayahs, null))
        assertEquals(emptyMap<String, ErrorLabel>(), PreviousLabels.map(ayahs, log()))
    }

    @Test
    fun exactMatchUsesWordIndexEvenWithRepeatedWords() {
        val m = PreviousLabels.map(ayahs, log(LabeledWord(1, "ذلك", ErrorLabel.MISTAKE, 3)))
        assertEquals(ErrorLabel.MISTAKE, m["p2_s2_a1_w3"])
        assertNull(m["p2_s2_a1_w2"]) // kata kembar di posisi lain tidak ikut tertandai
    }

    @Test
    fun oldLogsWithoutIndexMatchByAyahAndTextInOrder() {
        val m = PreviousLabels.map(
            ayahs,
            log(LabeledWord(1, "ذلك", ErrorLabel.HESITATION), LabeledWord(2, "هدى", ErrorLabel.SKIPPED))
        )
        assertEquals(ErrorLabel.HESITATION, m["p2_s2_a1_w2"])
        assertEquals(ErrorLabel.SKIPPED, m["p2_s2_a2_w1"])
        assertEquals(2, m.size)
    }

    @Test
    fun indexMismatchFallsBackToTextMatch() {
        val m = PreviousLabels.map(ayahs, log(LabeledWord(2, "للمتقين", ErrorLabel.TAJWEED, 9)))
        assertEquals(ErrorLabel.TAJWEED, m["p2_s2_a2_w2"])
    }

    @Test
    fun wordsNotOnThisPageAreIgnoredAndCountsAreGrouped() {
        val m = PreviousLabels.map(
            ayahs,
            log(LabeledWord(1, "الم", ErrorLabel.MISTAKE, 1), LabeledWord(7, "غير", ErrorLabel.MISTAKE, 1), LabeledWord(2, "هدى", ErrorLabel.MISTAKE, 1))
        )
        assertEquals(2, m.size)
        assertEquals(2, PreviousLabels.counts(m)[ErrorLabel.MISTAKE])
    }
}
