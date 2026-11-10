package id.mutqin.app

/** Elemen dalam satu baris halaman mushaf. */
sealed interface LineItem {
    data class Word(val word: QuranWord) : LineItem
    /** Penanda akhir ayat (nomor ayat dalam lingkaran). */
    data class AyahEnd(val ayah: QuranAyah) : LineItem
}

sealed interface PageLine
data class TextLine(val lineNumber: Int, val items: List<LineItem>) : PageLine
data class SurahBannerLine(val surahNumber: Int) : PageLine
data object BasmalahLine : PageLine

/**
 * Menyusun halaman seperti cetakan mushaf Madinah (15 baris per halaman): kata dikelompokkan menurut nomor baris,
 * dan spanduk nama surat + basmalah disisipkan sebelum baris tempat ayat 1 sebuah surat dimulai
 * (Al-Fatihah tanpa baris basmalah terpisah karena basmalah adalah ayat 1; At-Taubah tanpa basmalah).
 * Mengembalikan null jika ada kata tanpa nomor baris (aplikasi memakai tata letak mengalir sebagai cadangan).
 */
object MushafLayout {
    fun build(ayahs: List<QuranAyah>): List<PageLine>? {
        if (ayahs.isEmpty() || ayahs.any { a -> a.words.isEmpty() || a.words.any { it.line <= 0 } }) return null

        val byLine = java.util.TreeMap<Int, MutableList<LineItem>>()
        val firstLineOfSurahStart = LinkedHashMap<Int, Int>() // surat -> nomor baris tempat ayat 1 dimulai
        for (a in ayahs) {
            for (w in a.words) byLine.getOrPut(w.line) { ArrayList() }.add(LineItem.Word(w))
            byLine.getValue(a.words.last().line).add(LineItem.AyahEnd(a))
            if (a.ayahNumber == 1) firstLineOfSurahStart[a.surahNumber] = a.words.first().line
        }

        val result = ArrayList<PageLine>()
        for ((line, items) in byLine) {
            for ((surah, startLine) in firstLineOfSurahStart) {
                if (startLine != line) continue
                result += SurahBannerLine(surah)
                if (surah != 1 && surah != 9) result += BasmalahLine
            }
            result += TextLine(line, items)
        }
        return result
    }
}
