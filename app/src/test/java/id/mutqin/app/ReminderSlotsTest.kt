package id.mutqin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderSlotsTest {
    private val zone = ZoneId.of("Asia/Jakarta")

    // 2026-10-23 adalah hari Jumat
    private fun at(h: Int, m: Int, day: Int = 23) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone)

    private fun settings(days: List<Int> = (0..6).toList(), times: List<String>, enabled: Boolean = true) =
        NotificationSettings(
            enabled = enabled, title = "t", days = days,
            times = times.mapIndexed { i, t -> ReminderSchedule("id$i", t, true) }
        )

    @Test
    fun nextTriggerPicksLaterTimeToday() {
        val n = settings(times = listOf("05:00", "18:30"))
        assertEquals(at(18, 30), ReminderScheduler.nextTrigger(n, at(12, 0)))
    }

    @Test
    fun nextTriggerSkipsUnselectedDays() {
        // hanya Sabtu (6): dari Jumat 23 Okt -> Sabtu 24 Okt 05:00
        val n = settings(days = listOf(6), times = listOf("05:00"))
        assertEquals(at(5, 0, day = 24), ReminderScheduler.nextTrigger(n, at(12, 0)))
    }

    @Test
    fun disabledSettingsHaveNoTrigger() {
        val n = settings(times = listOf("05:00"), enabled = false)
        assertEquals(null, ReminderScheduler.nextTrigger(n, at(1, 0)))
    }

    @Test
    fun dueSlotsOnlyWithinGraceWindow() {
        val n = settings(times = listOf("05:00", "18:30"))
        assertEquals(listOf(at(5, 0)), ReminderScheduler.dueSlots(n, at(5, 20), 30))
        assertTrue(ReminderScheduler.dueSlots(n, at(5, 20), 10).isEmpty())
        assertTrue("belum waktunya", ReminderScheduler.dueSlots(n, at(4, 59), 30).isEmpty())
    }

    @Test
    fun dueSlotsRespectSelectedDays() {
        val n = settings(days = listOf(1), times = listOf("05:00")) // hanya Senin
        assertTrue(ReminderScheduler.dueSlots(n, at(5, 10), 30).isEmpty())
    }

    @Test
    fun slotKeyIsStableAndSortable() {
        assertEquals("2026-10-23 05:00", ReminderScheduler.slotKey(at(5, 0)))
        assertEquals("2026-10-23 18:30", ReminderScheduler.slotKey(at(18, 30)))
    }
}
