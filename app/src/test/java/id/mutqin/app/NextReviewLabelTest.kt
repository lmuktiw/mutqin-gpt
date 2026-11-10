package id.mutqin.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class NextReviewLabelTest {
    private val now: Instant = Instant.parse("2026-10-22T10:00:00Z") // Kamis
    private val zone = ZoneOffset.UTC

    @Test
    fun todayIsUrgent() {
        assertEquals(NextReviewText("hari ini", true), NextReviewLabel.describe("2026-10-22T23:30:00Z", now, zone))
        assertEquals(NextReviewText("hari ini", true), NextReviewLabel.describe("2026-10-22T01:00:00Z", now, zone))
    }

    @Test
    fun tomorrowIsPlain() {
        assertEquals(NextReviewText("besok", false), NextReviewLabel.describe("2026-10-23T00:10:00Z", now, zone))
    }

    @Test
    fun futureDatesShowTheDate() {
        assertEquals(NextReviewText("3 Nov 2026", false), NextReviewLabel.describe("2026-11-03T08:00:00Z", now, zone))
        assertEquals(NextReviewText("25 Des 2026", false), NextReviewLabel.describe("2026-12-25T08:00:00Z", now, zone))
    }

    @Test
    fun pastDatesShowTheDateAndAreUrgent() {
        assertEquals(NextReviewText("20 Okt 2026", true), NextReviewLabel.describe("2026-10-20T09:00:00Z", now, zone))
    }
}
