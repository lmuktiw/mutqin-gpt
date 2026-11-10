package id.mutqin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecImportTest {
    private fun card(page: Int, reps: Int, at: String) = CardProgress(
        pageNumber = page, repetitions = reps, lastReviewedDate = at, nextReviewDate = "2026-12-01T00:00:00Z"
    )

    private val fileJson = Codec.exportSync(
        "dev_x",
        mapOf(1 to card(1, 2, "2026-11-01T00:00:00Z"), 2 to card(2, 1, "2026-11-02T00:00:00Z")),
        UserSettings(scopeQueueOrder = listOf(2, 1))
    )

    @Test fun merge_keepsLocalOnlyPages() {
        val local = mapOf(9 to card(9, 3, "2026-11-05T00:00:00Z"))
        val o = Codec.importSync(fileJson, local, UserSettings())
        assertTrue(o.success)
        assertEquals(setOf(1, 2, 9), o.progress.keys)
        assertEquals(2, o.incomingReviewed)
        assertEquals(listOf(2, 1), o.settings?.scopeQueueOrder)
    }

    @Test fun replace_dropsLocalPages() {
        val local = mapOf(9 to card(9, 3, "2026-11-05T00:00:00Z"))
        val o = Codec.importSync(fileJson, local, UserSettings(), replace = true)
        assertEquals(setOf(1, 2), o.progress.keys)
        assertEquals(2, o.count)
    }

    @Test fun invalidJson_fails() {
        assertFalse(Codec.importSync("{}", emptyMap(), UserSettings()).success)
    }
}
