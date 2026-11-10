package id.mutqin.app

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AyahLocatorTest {

    private fun loadLocator(): AyahLocator {
        val arr = JSONArray(File("src/main/assets/page_start_ayahs.json").readText(Charsets.UTF_8))
        return AyahLocator((0 until arr.length()).map {
            val pair = arr.getJSONArray(it)
            intArrayOf(pair.getInt(0), pair.getInt(1))
        })
    }

    @Test
    fun has604Pages() {
        assertEquals(604, loadLocator().pageCount)
    }

    @Test
    fun ayahToPageMatchesKnownMushafMadinah() {
        val l = loadLocator()
        assertEquals(1, l.pageOf(1, 1))
        assertEquals(2, l.pageOf(2, 1))
        assertEquals(22, l.pageOf(2, 142))
        assertEquals(42, l.pageOf(2, 255))   // Ayat Kursi
        assertEquals(49, l.pageOf(2, 286))
        assertEquals(50, l.pageOf(3, 1))
        assertEquals(293, l.pageOf(18, 1))   // Al-Kahfi
        assertEquals(440, l.pageOf(36, 1))   // Yasin
        assertEquals(562, l.pageOf(67, 1))   // Al-Mulk
        assertEquals(582, l.pageOf(78, 1))   // An-Naba
        assertEquals(604, l.pageOf(114, 6))
    }

    /** Surat yang ayat 1-nya di TENGAH halaman: halaman pertama tetap halaman tempat ayat 1 berada. */
    @Test
    fun surahFirstPageIsPageOfAyahOneEvenWhenMidPage() {
        val l = loadLocator()
        assertEquals(106, l.surahStartPage(5))   // Al-Ma'idah (halaman 106 dibuka dari An-Nisa 176)
        assertEquals(293, l.surahStartPage(18))  // Al-Kahfi (halaman 293 dibuka dari Al-Isra 105)
        assertEquals(583, l.surahStartPage(79))  // An-Nazi'at
    }

    @Test
    fun surahPagesAreConsistentWithSurahList() {
        val l = loadLocator()
        val arr = JSONArray(File("src/main/assets/surahs.json").readText(Charsets.UTF_8))
        var previousStart = 0
        var verses = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val n = o.getInt("number")
            val total = o.getInt("totalVerses")
            val start = l.surahStartPage(n)
            val end = l.surahEndPage(n, total)
            assertTrue("surat $n: halaman akhir < awal", end >= start)
            assertTrue("surat $n: urutan halaman awal mundur", start >= previousStart)
            previousStart = start
            verses += total
        }
        assertEquals(1, l.surahStartPage(1))
        assertEquals(604, l.surahEndPage(114, 6))
        assertEquals(6236, verses)
    }

    @Test
    fun ayahRangeCoversAllPagesInBetween() {
        val l = loadLocator()
        assertEquals((42..49).toList(), l.pagesForAyahRange(2, 255, 286))
        assertEquals(listOf(582, 583), l.pagesForAyahRange(78, 1, 40))
        // urutan terbalik tetap menghasilkan rentang yang sama
        assertEquals((42..49).toList(), l.pagesForAyahRange(2, 286, 255))
    }
}
