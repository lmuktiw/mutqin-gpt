package id.mutqin.app

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray

/**
 * Teks Uthmani dan audio petunjuk seluruh 604 halaman dibundel di dalam APK (assets/pages dan assets/audio),
 * sehingga aplikasi sepenuhnya offline. Berkas dibuat oleh workflow "Generate Assets" (lihat README).
 */
object QuranRepository {
    private const val TAG = "QuranRepository"

    fun pageAssetPath(page: Int): String = "pages/p${page.toString().padStart(3, '0')}.json"

    fun imageAssetPath(page: Int): String = "pages_img/p${page.toString().padStart(3, '0')}.webp"

    fun boxesAssetPath(page: Int): String = "page_boxes/p${page.toString().padStart(3, '0')}.json"

    fun audioAssetPath(page: Int): String = "audio/p${page.toString().padStart(3, '0')}.mp3"

    /** Pembuka akses klip audio 3,5 detik pertama halaman [page]; null jika berkas tidak ada. */
    fun openHintAudio(context: Context, page: Int): AssetFileDescriptor? =
        try {
            context.applicationContext.assets.openFd(audioAssetPath(page))
        } catch (e: Exception) {
            Log.w(TAG, "Audio halaman $page tidak ditemukan: ${e.message}")
            null
        }

    internal fun wordId(page: Int, surah: Int, ayah: Int, index: Int) = "p${page}_s${surah}_a${ayah}_w$index"

    /** Memuat teks satu halaman dari assets; null jika berkas tidak ada/rusak (APK tidak lengkap). */
    suspend fun getPage(context: Context, page: Int): PageText? = withContext(Dispatchers.IO) {
        try {
            val json = context.applicationContext.assets.open(pageAssetPath(page))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(json)
            val ayahs = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val surah = o.getInt("s")
                val ayah = o.getInt("a")
                val wArr = o.getJSONArray("w")
                val lArr = o.optJSONArray("l") // nomor baris tiap kata (opsional)
                val words = (0 until wArr.length()).map { j ->
                    val line = if (lArr != null && lArr.length() == wArr.length()) lArr.optInt(j, 0) else 0
                    QuranWord(wordId(page, surah, ayah, j + 1), wArr.getString(j), ayah, j + 1, line)
                }
                QuranAyah(surah, ayah, words)
            }
            if (ayahs.isEmpty()) null else PageText(ayahs)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat teks halaman $page: ${e.message}")
            null
        }
    }

    /**
     * Gambar halaman utuh (mushaf Madinah cetak) + kotak tiap kata/ayat, dari assets/pages_img dan assets/page_boxes.
     * null bila aset belum dibuat (workflow "Generate Page Images") atau tidak cocok dengan teks halaman;
     * layar lalu memakai tampilan teks sebagai cadangan.
     */
    suspend fun getPageImage(context: Context, page: Int, text: PageText): PageImage? = withContext(Dispatchers.IO) {
        try {
            val assets = context.applicationContext.assets
            val boxes = PageBoxes.parse(
                assets.open(boxesAssetPath(page)).bufferedReader(Charsets.UTF_8).use { it.readText() }, page
            ) ?: return@withContext null
            if (!boxes.coversAll(text)) {
                Log.w(TAG, "Kotak halaman $page tidak cocok dengan teks; memakai tampilan teks")
                return@withContext null
            }
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            val bmp = assets.open(imageAssetPath(page)).use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null
            PageImage(bmp.asImageBitmap(), boxes)
        } catch (e: Exception) {
            Log.w(TAG, "Gambar halaman $page tidak tersedia: ${e.message}")
            null
        }
    }
}

/** Gambar satu halaman beserta kotak kata/ayatnya. */
class PageImage(val bitmap: ImageBitmap, val boxes: PageBoxes) {
    val aspect: Float get() = bitmap.width.toFloat() / bitmap.height.toFloat()
}
