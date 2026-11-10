package id.mutqin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class ScopeTransitionTest {
    private val now: Instant = Instant.parse("2026-10-22T10:00:00Z")
    private val zone = ZoneOffset.UTC

    private fun juz(j: Int) = QuranStructure.pagesInJuz(j)
    private fun juzRange(from: Int, to: Int) = (from..to).flatMap { juz(it) }
    private fun card(page: Int, box: Int, due: Instant) =
        CardProgress(pageNumber = page, box = box, repetitions = 1, nextReviewDate = due.toString())

    private fun apply(
        oldScope: List<Int>, oldQueue: List<Int>?, newScope: Collection<Int>, current: Int,
        progress: Map<Int, CardProgress> = emptyMap()
    ) = ScopeTransition.apply(oldScope, oldQueue, newScope, current, progress, now, zone)

    /** Kartu aktif di Juz 3, rentang baru mulai Juz 7: langsung tampil halaman paling awal rentang baru. */
    @Test
    fun newRangeStartingAfterActiveCardJumpsToItsFirstPage() {
        val r = apply(juz(3), null, juz(7), current = 45)
        assertEquals(122, r.page)
        assertTrue(r.jumped)
        assertEquals(juz(7), r.queueOrder)
        assertTrue("sisa Juz 3 harus keluar dari program", r.queueOrder.none { it in juz(3) })
    }

    /** Kartu aktif tetap di dalam rentang baru: tidak berpindah; halaman sebelum kartu aktif antre melingkar di belakang. */
    @Test
    fun activeCardInsideNewRangeStaysAndEarlierPagesQueueAtBack() {
        val r = apply(juz(7), null, juzRange(5, 9), current = 130)
        assertEquals(130, r.page)
        assertFalse(r.jumped)
        val expected = juz(7) + juzRange(8, 9) + juzRange(5, 6)
        assertEquals(expected, r.queueOrder)
    }

    @Test
    fun afterActiveCardNextUnreviewedFollowsQueueOrder() {
        val r = apply(juz(7), null, juzRange(5, 9), current = 130)
        val done = (122..129).associateWith { card(it, 2, now.plus(3, ChronoUnit.DAYS)) }
        val next = QueueEngine.nextFlashcard(done, juzRange(5, 9), 130, r.queueOrder, now, zone)!!
        assertEquals(131, next.pageNumber)
    }

    @Test
    fun activeCardOutsideRangeWithPagesAfterItFollowsCircularQueue() {
        val oldScope = (1..604).toList()
        val newScope = (10..20).toList() + (200..210).toList()
        val progress = (1..99).associateWith { card(it, 2, now.plus(4, ChronoUnit.DAYS)) }
        assertEquals(200, apply(oldScope, null, newScope, current = 100, progress = progress).page)
    }

    @Test
    fun newRangeEntirelyBeforeActiveCardUsesEngineQueue() {
        val r = apply((1..604).toList(), null, (1..21).toList(), current = 300)
        assertEquals(1, r.page)
    }

    @Test
    fun jumpSkipsFirstPageAlreadyReviewedAndNotDue() {
        val progress = mapOf(122 to card(122, 3, now.plus(5, ChronoUnit.DAYS)))
        val r = apply(juz(3), null, juz(7), current = 45, progress = progress)
        assertEquals(123, r.page)
    }

    @Test
    fun jumpShowsFirstPageWhenItIsDue() {
        val progress = mapOf(122 to card(122, 3, now.minus(1, ChronoUnit.HOURS)))
        assertEquals(122, apply(juz(3), null, juz(7), current = 45, progress = progress).page)
    }

    @Test
    fun existingQueueOrderIsPreservedAndFiltered() {
        val oldQueue = listOf(125, 126, 122, 123, 124)
        val r = apply((122..126).toList(), oldQueue, (122..125).toList(), current = 125)
        assertEquals(listOf(125, 122, 123, 124), r.queueOrder)
        assertEquals(125, r.page)
    }
}
