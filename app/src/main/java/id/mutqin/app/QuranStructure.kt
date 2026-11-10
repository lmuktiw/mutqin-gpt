package id.mutqin.app

/**
 * Struktur Mushaf Madinah (604 halaman, 30 juz). Murni Kotlin, tanpa dependensi Android,
 * sehingga mudah diuji.
 */
object QuranStructure {
    const val TOTAL_PAGES = 604

    /** Halaman pertama tiap juz (indeks 0 = juz 1). */
    val JUZ_START_PAGES: IntArray = intArrayOf(
        1, 22, 42, 62, 82, 102, 122, 142, 162, 182,
        202, 222, 242, 262, 282, 302, 322, 342, 362, 382,
        402, 422, 442, 462, 482, 502, 522, 542, 562, 582
    )

    fun juzForPage(page: Int): Int {
        for (i in JUZ_START_PAGES.indices.reversed()) {
            if (page >= JUZ_START_PAGES[i]) return i + 1
        }
        return 1
    }

    data class LembarInfo(val lembar: Int, val side: PageSide, val juz: Int, val juzStartPage: Int)

    /**
     * Lembar (lembaran fisik) dan sisi (depan/belakang).
     * Khusus juz 1: halaman 1 (Al-Fatihah) = lembar 1, halaman 2 sudah lembar 2 depan.
     */
    fun lembarAndSide(page: Int): LembarInfo {
        val juz = juzForPage(page)
        val juzStart = JUZ_START_PAGES[juz - 1]

        if (juz == 1) {
            if (page == 1) return LembarInfo(1, PageSide.DEPAN, 1, 1)
            val offsetFrom2 = page - 2
            val lembar = 2 + offsetFrom2 / 2
            val side = if (offsetFrom2 % 2 == 0) PageSide.DEPAN else PageSide.BELAKANG
            return LembarInfo(lembar, side, 1, 1)
        }

        val offset = page - juzStart
        val lembar = offset / 2 + 1
        val side = if (offset % 2 == 0) PageSide.DEPAN else PageSide.BELAKANG
        return LembarInfo(lembar, side, juz, juzStart)
    }

    fun pagesInJuz(juz: Int): List<Int> {
        val start = JUZ_START_PAGES[juz - 1]
        val end = if (juz == 30) TOTAL_PAGES else JUZ_START_PAGES[juz] - 1
        return (start..end).toList()
    }
}
