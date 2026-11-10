package id.mutqin.app

/** Aturan penggabungan data saat mengimpor file JSON (murni Kotlin, diuji unit test). */
object CardMerge {

    private fun reviewedMillis(c: CardProgress): Long =
        c.lastReviewedDate?.let { runCatching { Time.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    private fun logMillis(l: ReviewLog): Long =
        runCatching { Time.parse(l.timestamp).toEpochMilli() }.getOrDefault(0L)

    /**
     * Pemenang = murajaah paling baru (`lastReviewedDate`); jika sama, repetisi terbanyak.
     * Riwayat kedua sisi digabung (tanpa duplikat, terbaru dulu, maksimal 30) agar tidak ada log yang hilang.
     */
    fun merge(a: CardProgress, b: CardProgress): CardProgress {
        val ta = reviewedMillis(a)
        val tb = reviewedMillis(b)
        val winner = when {
            ta != tb -> if (ta > tb) a else b
            a.repetitions != b.repetitions -> if (a.repetitions > b.repetitions) a else b
            else -> a
        }
        val history = (a.history + b.history)
            .distinctBy { it.id }
            .sortedByDescending { logMillis(it) }
            .take(30)
        return winner.copy(history = history)
    }
}
