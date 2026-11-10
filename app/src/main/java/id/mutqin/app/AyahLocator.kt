package id.mutqin.app

/**
 * Memetakan (surat, ayat) -> nomor halaman mushaf Madinah, murni Kotlin (mudah diuji).
 *
 * [starts] berisi pasangan (surat, ayat) pembuka tiap halaman; indeks 0 = halaman 1.
 * Sebuah ayat berada di halaman terakhir yang ayat pembukanya <= ayat tersebut.
 */
class AyahLocator(private val starts: List<IntArray>) {

    val pageCount: Int get() = starts.size

    fun pageOf(surah: Int, ayah: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        var answer = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val s = starts[mid]
            val startsAtOrBefore = s[0] < surah || (s[0] == surah && s[1] <= ayah)
            if (startsAtOrBefore) {
                answer = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return answer + 1
    }

    /** Halaman tempat ayat 1 sebuah surat berada (bisa di tengah halaman, bukan di awalnya). */
    fun surahStartPage(surah: Int): Int = pageOf(surah, 1)

    /** Halaman tempat ayat terakhir sebuah surat berada. */
    fun surahEndPage(surah: Int, totalVerses: Int): Int = pageOf(surah, totalVerses)

    /** Halaman-halaman yang memuat ayat [fromAyah]..[toAyah] dari [surah] (inklusif). */
    fun pagesForAyahRange(surah: Int, fromAyah: Int, toAyah: Int): List<Int> {
        val lo = minOf(fromAyah, toAyah)
        val hi = maxOf(fromAyah, toAyah)
        return (pageOf(surah, lo)..pageOf(surah, hi)).toList()
    }
}
