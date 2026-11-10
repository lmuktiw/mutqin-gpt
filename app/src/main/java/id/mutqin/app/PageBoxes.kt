package id.mutqin.app

import org.json.JSONArray
import org.json.JSONObject

/** Kotak satu kata pada gambar halaman, dinormalkan 0..1 terhadap lebar/tinggi gambar. */
data class WordBox(val wordId: String, val ayahNumber: Int, val x0: Float, val y0: Float, val x1: Float, val y1: Float)

/** Kotak penanda akhir ayat (lingkaran nomor ayat) pada gambar halaman, dinormalkan 0..1. */
data class AyahMarkBox(val surahNumber: Int, val ayahNumber: Int, val x0: Float, val y0: Float, val x1: Float, val y1: Float)

/** Hasil baca assets/page_boxes/pNNN.json (dibuat tools/generate_page_images.py). */
data class PageBoxes(val words: List<WordBox>, val marks: List<AyahMarkBox>) {

    /** true bila setiap kata pada [text] punya kotak dan setiap ayat punya penanda: syarat memakai tampilan gambar. */
    fun coversAll(text: PageText): Boolean {
        val ids = HashSet<String>(words.size * 2).also { set -> words.forEach { set += it.wordId } }
        val markKeys = HashSet<Pair<Int, Int>>().also { set -> marks.forEach { set += it.surahNumber to it.ayahNumber } }
        for (a in text.ayahs) {
            if ((a.surahNumber to a.ayahNumber) !in markKeys) return false
            for (w in a.words) if (w.id !in ids) return false
        }
        return true
    }

    companion object {
        private const val NORM = 10000f

        /** Mengurai JSON kotak; null bila rusak. [page] dipakai membentuk id kata yang sama dengan QuranRepository. */
        fun parse(json: String, page: Int): PageBoxes? = try {
            val root = JSONObject(json)
            val t = root.getJSONArray("t")
            val e = root.getJSONArray("e")
            val words = (0 until t.length()).map { i ->
                val r = t.getJSONArray(i)
                WordBox(
                    wordId = QuranRepository.wordId(page, r.getInt(0), r.getInt(1), r.getInt(2)),
                    ayahNumber = r.getInt(1),
                    x0 = r.getInt(3) / NORM, y0 = r.getInt(4) / NORM, x1 = r.getInt(5) / NORM, y1 = r.getInt(6) / NORM
                )
            }
            val marks = (0 until e.length()).map { i ->
                val r: JSONArray = e.getJSONArray(i)
                AyahMarkBox(r.getInt(0), r.getInt(1), r.getInt(2) / NORM, r.getInt(3) / NORM, r.getInt(4) / NORM, r.getInt(5) / NORM)
            }
            if (words.isEmpty()) null else PageBoxes(words, marks)
        } catch (ex: Exception) {
            null
        }
    }
}

/** Sasaran sentuh pada halaman bergambar. */
sealed interface PageHit {
    data class Word(val box: WordBox) : PageHit
    data class Mark(val box: AyahMarkBox) : PageHit
}

/**
 * Menentukan kotak yang disentuh pada titik ([nx], [ny]) (0..1). Penanda ayat didahulukan; lalu kata yang memuat titik;
 * bila titik jatuh di sela antarkotak, dipilih kata terdekat yang masih dalam toleransi ([padX], [padY]).
 */
object PageHitTest {
    fun find(boxes: PageBoxes, nx: Float, ny: Float, padX: Float, padY: Float): PageHit? {
        boxes.marks.firstOrNull { nx >= it.x0 - padX && nx <= it.x1 + padX && ny >= it.y0 - padY && ny <= it.y1 + padY }
            ?.let { return PageHit.Mark(it) }
        boxes.words.firstOrNull { nx >= it.x0 && nx <= it.x1 && ny >= it.y0 && ny <= it.y1 }
            ?.let { return PageHit.Word(it) }
        var best: WordBox? = null
        var bestD = Float.MAX_VALUE
        for (b in boxes.words) {
            if (nx < b.x0 - padX || nx > b.x1 + padX || ny < b.y0 - padY || ny > b.y1 + padY) continue
            val dx = maxOf(b.x0 - nx, 0f, nx - b.x1) / maxOf(padX, 1e-6f)
            val dy = maxOf(b.y0 - ny, 0f, ny - b.y1) / maxOf(padY, 1e-6f)
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = b }
        }
        return best?.let { PageHit.Word(it) }
    }
}
