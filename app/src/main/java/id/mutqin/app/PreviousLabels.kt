package id.mutqin.app

/** Memetakan label dari murajaah sebelumnya ke kata-kata pada halaman yang sedang dievaluasi. */
object PreviousLabels {

    /**
     * Kunci hasil = id kata. Log baru memuat nomor kata (cocok persis, teks juga harus sama).
     * Log lama (tanpa nomor kata) dicocokkan per (ayat, teks kata) secara berurutan.
     */
    fun map(ayahs: List<QuranAyah>, log: ReviewLog?): Map<String, ErrorLabel> {
        if (log == null || log.labeledWordsSummary.isEmpty()) return emptyMap()
        val result = HashMap<String, ErrorLabel>()
        val entries = log.labeledWordsSummary

        // 1) cocok persis: ayat + nomor kata + teks
        val exact = entries.filter { it.wordIndex > 0 }.associateBy { it.ayahNumber to it.wordIndex }
        val used = HashSet<LabeledWord>()
        for (a in ayahs) for (w in a.words) {
            val hit = exact[a.ayahNumber to w.position] ?: continue
            if (hit.wordText == w.text) {
                result[w.id] = hit.label
                used += hit
            }
        }

        // 2) sisanya: cocokkan per (ayat, teks) berurutan
        val queues = HashMap<Pair<Int, String>, ArrayDeque<ErrorLabel>>()
        for (e in entries) {
            if (e in used) continue
            queues.getOrPut(e.ayahNumber to e.wordText) { ArrayDeque() }.addLast(e.label)
        }
        if (queues.isNotEmpty()) {
            for (a in ayahs) for (w in a.words) {
                if (w.id in result) continue
                val q = queues[a.ayahNumber to w.text] ?: continue
                if (q.isNotEmpty()) result[w.id] = q.removeFirst()
            }
        }
        return result
    }

    fun counts(labels: Map<String, ErrorLabel>): Map<ErrorLabel, Int> =
        labels.values.filter { it != ErrorLabel.NONE }.groupingBy { it }.eachCount()
}
