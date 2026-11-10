package id.mutqin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageBoxesTest {
    private val json = """{"v":1,"w":100,"h":200,
        "t":[[2,6,1,6000,1000,9000,1900],[2,6,2,3000,1000,5800,1900]],
        "e":[[2,6,1000,1000,2800,1900]]}"""

    private fun text() = PageText(
        listOf(
            QuranAyah(2, 6, listOf(
                QuranWord("p5_s2_a6_w1", "a", 6, 1, 1),
                QuranWord("p5_s2_a6_w2", "b", 6, 2, 1)
            ))
        )
    )

    @Test fun parse_readsNormalizedBoxes() {
        val b = PageBoxes.parse(json, 5)!!
        assertEquals(2, b.words.size)
        assertEquals("p5_s2_a6_w1", b.words[0].wordId)
        assertEquals(0.6f, b.words[0].x0, 1e-4f)
        assertEquals(1, b.marks.size)
    }

    @Test fun parse_brokenJson_returnsNull() {
        assertNull(PageBoxes.parse("{oops", 5))
    }

    @Test fun coversAll_requiresEveryWordAndAyahMark() {
        val b = PageBoxes.parse(json, 5)!!
        assertTrue(b.coversAll(text()))
        assertFalse(PageBoxes.parse(json, 6)!!.coversAll(text())) // id kata beda halaman
        assertFalse(b.copy(marks = emptyList()).coversAll(text()))
    }

    @Test fun hit_wordMarkAndGap() {
        val b = PageBoxes.parse(json, 5)!!
        assertTrue(PageHitTest.find(b, 0.7f, 0.14f, 0.01f, 0.01f) is PageHit.Word)
        assertTrue(PageHitTest.find(b, 0.15f, 0.14f, 0.01f, 0.01f) is PageHit.Mark)
        // sela sempit antar kata (0.58..0.60) masih memilih kata terdekat
        assertNotNull(PageHitTest.find(b, 0.59f, 0.14f, 0.02f, 0.01f))
        // jauh dari kotak apa pun
        assertNull(PageHitTest.find(b, 0.5f, 0.9f, 0.01f, 0.01f))
    }
}
