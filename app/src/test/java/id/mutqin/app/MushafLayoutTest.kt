package id.mutqin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MushafLayoutTest {
    private fun w(s: Int, a: Int, pos: Int, line: Int) = QuranWord("s${s}a${a}w$pos", "k$pos", a, pos, line)

    @Test
    fun returnsNullWhenAnyWordHasNoLineNumber() {
        val ayahs = listOf(QuranAyah(2, 5, listOf(w(2, 5, 1, 3), w(2, 5, 2, 0))))
        assertNull(MushafLayout.build(ayahs))
        assertNull(MushafLayout.build(emptyList()))
    }

    @Test
    fun groupsWordsByLineInOrderAndPutsAyahEndAfterLastWord() {
        val ayahs = listOf(
            QuranAyah(2, 6, listOf(w(2, 6, 1, 1), w(2, 6, 2, 1), w(2, 6, 3, 2))),
            QuranAyah(2, 7, listOf(w(2, 7, 1, 2), w(2, 7, 2, 3)))
        )
        val layout = MushafLayout.build(ayahs)!!
        assertEquals(3, layout.size)
        val l1 = layout[0] as TextLine
        assertEquals(1, l1.lineNumber)
        assertEquals(2, l1.items.size) // dua kata ayat 6, belum ada penanda akhir ayat di baris ini
        val l2 = layout[1] as TextLine
        // baris 2: kata ke-3 ayat 6, penanda akhir ayat 6, lalu kata ke-1 ayat 7
        assertTrue(l2.items[0] is LineItem.Word)
        assertTrue(l2.items[1] is LineItem.AyahEnd && (l2.items[1] as LineItem.AyahEnd).ayah.ayahNumber == 6)
        assertTrue(l2.items[2] is LineItem.Word)
        val l3 = layout[2] as TextLine
        assertTrue(l3.items.last() is LineItem.AyahEnd && (l3.items.last() as LineItem.AyahEnd).ayah.ayahNumber == 7)
    }

    @Test
    fun surahStartInsertsBannerAndBasmalahBeforeItsFirstLine() {
        val ayahs = listOf(
            QuranAyah(2, 286, listOf(w(2, 286, 1, 1))),
            QuranAyah(3, 1, listOf(w(3, 1, 1, 4), w(3, 1, 2, 4))),
            QuranAyah(3, 2, listOf(w(3, 2, 1, 5)))
        )
        val layout = MushafLayout.build(ayahs)!!
        assertTrue(layout[0] is TextLine)
        assertEquals(SurahBannerLine(3), layout[1])
        assertEquals(BasmalahLine, layout[2])
        assertEquals(4, (layout[3] as TextLine).lineNumber)
    }

    @Test
    fun fatihahAndTawbahHaveBannerButNoSeparateBasmalah() {
        val fatihah = MushafLayout.build(listOf(QuranAyah(1, 1, listOf(w(1, 1, 1, 2)))))!!
        assertEquals(SurahBannerLine(1), fatihah[0])
        assertTrue(fatihah[1] is TextLine)
        val tawbah = MushafLayout.build(listOf(QuranAyah(9, 1, listOf(w(9, 1, 1, 1)))))!!
        assertEquals(SurahBannerLine(9), tawbah[0])
        assertTrue(tawbah[1] is TextLine)
    }
}
