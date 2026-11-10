package id.mutqin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class QueueEngineTest {
    private val now: Instant = Instant.parse("2026-10-22T10:00:00Z")
    private val zone = ZoneOffset.UTC

    private fun card(page: Int, box: Int, due: Instant, reps: Int = 1) =
        CardProgress(pageNumber = page, box = box, repetitions = reps, nextReviewDate = due.toString())

    private val scope = (1..29).toList()

    @Test
    fun debtFromPreviousDaysComesFirst() {
        val progress = mapOf(
            5 to card(5, 3, now.minus(2, ChronoUnit.DAYS)),
            6 to card(6, 1, now.minus(3, ChronoUnit.HOURS)),
            7 to card(7, 2, now.minus(1, ChronoUnit.HOURS))
        )
        val pick = QueueEngine.nextFlashcard(progress, scope, null, null, now, zone)
        assertNotNull(pick)
        assertEquals(5, pick!!.pageNumber)
        assertEquals(PickReason.DUE_SRS, pick.reason)
        assertTrue(pick.isOverdueDebt)
    }

    @Test
    fun oldestDebtBeforeNewerDebt() {
        val progress = mapOf(
            5 to card(5, 3, now.minus(2, ChronoUnit.DAYS)),
            8 to card(8, 1, now.minus(5, ChronoUnit.DAYS))
        )
        assertEquals(8, QueueEngine.nextFlashcard(progress, scope, null, null, now, zone)!!.pageNumber)
    }

    @Test
    fun dueTodayFollowsDebt_lowestBoxFirst() {
        val progress = mapOf(
            6 to card(6, 1, now.minus(3, ChronoUnit.HOURS)),
            7 to card(7, 2, now.minus(1, ChronoUnit.HOURS))
        )
        val pick = QueueEngine.nextFlashcard(progress, scope, null, null, now, zone)!!
        assertEquals(6, pick.pageNumber)
        assertFalse(pick.isOverdueDebt)
    }

    @Test
    fun newPagesFollowQueueAfterDueCards() {
        val pick = QueueEngine.nextFlashcard(emptyMap(), scope, null, null, now, zone)!!
        assertEquals(1, pick.pageNumber)
        assertEquals(PickReason.NEW_UNREVIEWED, pick.reason)
    }

    @Test
    fun earlyReviewWhenEverythingIsDone() {
        val progress = mapOf(
            1 to card(1, 2, now.plus(5, ChronoUnit.DAYS)),
            2 to card(2, 2, now.plus(2, ChronoUnit.DAYS)),
            3 to card(3, 2, now.plus(9, ChronoUnit.DAYS))
        )
        val pick = QueueEngine.nextFlashcard(progress, listOf(1, 2, 3), null, null, now, zone)!!
        assertEquals(2, pick.pageNumber)
        assertEquals(PickReason.EARLY_REVIEW, pick.reason)
    }

    /** Jatuh tempo berbasis HARI: jadwal pukul 15.00 hari ini sudah jatuh tempo walau sekarang baru pukul 10.00. */
    @Test
    fun dueLaterTodayCountsAsDueAllDay() {
        val progress = mapOf(7 to card(7, 2, now.plus(5, ChronoUnit.HOURS)))
        val pick = QueueEngine.nextFlashcard(progress, scope, null, null, now, zone)!!
        assertEquals(7, pick.pageNumber)
        assertEquals(PickReason.DUE_SRS, pick.reason)
        assertFalse(pick.isOverdueDebt)
    }

    @Test
    fun dueTomorrowIsNotDueYet() {
        val progress = mapOf(7 to card(7, 2, Instant.parse("2026-10-23T00:30:00Z")))
        val pick = QueueEngine.nextFlashcard(progress, scope, null, null, now, zone)!!
        assertEquals(1, pick.pageNumber) // halaman baru, bukan 7
        assertEquals(PickReason.NEW_UNREVIEWED, pick.reason)
    }

    @Test
    fun dueCardsAreOrderedDebtFirstThenTodayByBox() {
        val progress = mapOf(
            3 to card(3, 4, now.plus(2, ChronoUnit.HOURS)),
            4 to card(4, 1, now.minus(1, ChronoUnit.HOURS)),
            5 to card(5, 3, now.minus(3, ChronoUnit.DAYS)),
            6 to card(6, 2, now.minus(1, ChronoUnit.DAYS))
        )
        val order = QueueEngine.dueCards(progress, scope, null, now, zone).map { it.page }
        assertEquals(listOf(5, 6, 4, 3), order)
    }

    @Test
    fun dueCardsOnlyWithinActiveScope() {
        val progress = mapOf(
            5 to card(5, 3, now.minus(3, ChronoUnit.DAYS)),
            500 to card(500, 3, now.minus(3, ChronoUnit.DAYS))
        )
        assertEquals(listOf(5), QueueEngine.dueCards(progress, scope, null, now, zone).map { it.page })
    }

    @Test
    fun excludedPageIsSkipped() {
        val pick = QueueEngine.nextFlashcard(emptyMap(), scope, 1, null, now, zone)!!
        assertEquals(2, pick.pageNumber)
    }
}
