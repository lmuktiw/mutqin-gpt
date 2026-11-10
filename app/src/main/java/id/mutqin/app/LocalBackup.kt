package id.mutqin.app

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Menyimpan cadangan JSON terbaru ke folder publik Download/Mutqin di perangkat (satu file per "progres", dinamai tanggal+jam pembuatan).
 *
 * Android TIDAK menyediakan cara apa pun bagi aplikasi untuk menjalankan kode tepat sebelum di-uninstall
 * (tidak ada callback/broadcast yang sampai ke aplikasi itu sendiri), jadi "simpan sebelum uninstall" mustahil
 * dibuat secara harfiah. Gantinya, file ini diperbarui otomatis setiap data berubah dan setiap aplikasi
 * ditinggalkan, sehingga isinya selalu yang terbaru saat pengguna mencopot aplikasi.
 * File di Download TIDAK ikut terhapus saat uninstall; impor kembali setelah memasang ulang.
 */
object LocalBackup {
    private const val SUBDIR = "Mutqin"
    const val DISPLAY_DIR = "Download/Mutqin"

    /** Android 9 ke bawah butuh izin tulis penyimpanan; Android 10+ tidak (MediaStore). */
    fun needsLegacyPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    fun hasPermission(context: Context): Boolean =
        !needsLegacyPermission() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Izin yang perlu diminta untuk MENULIS ke Download: hanya Android 9 ke bawah. */
    fun permissionsToRequest(context: Context): List<String> =
        if (needsLegacyPermission() && !hasPermission(context)) listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE) else emptyList()

    /** URI dokumen folder Download/Mutqin (untuk membuka aplikasi File / pemilih file langsung di folder itu). */
    fun folderDocumentUri(): Uri =
        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR")

    /**
     * Menyimpan [json]. Aturan dasar: cadangan selalu masuk ke file BARU di Download/Mutqin bernama
     * mutqin-cadangan-TANGGAL-JAM.json (waktu pembuatan); file itu lalu terus diperbarui tanpa ganti nama.
     * File yang diimpor pengguna tidak pernah diperbarui. Impor/reset/mulai dari awal mengosongkan target sehingga dibuat file baru.
     * Bila menulis ke file target gagal (mis. dihapus pengguna), dibuat file baru; file lama tidak diperiksa maupun dihapus.
     * Mengembalikan lokasi untuk ditampilkan.
     */
    fun save(context: Context, store: MutqinStore, json: String): Result<String> = runCatching {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val current = store.backupUri
        if (current != null && writeTo(context, current, bytes)) return@runCatching "$DISPLAY_DIR/${store.backupName ?: ""}"
        val (uri, name) = createNew(context, bytes)
        store.backupUri = uri
        store.backupName = name
        "$DISPLAY_DIR/$name"
    }

    private fun writeTo(context: Context, target: String, bytes: ByteArray): Boolean = runCatching {
        if (target.startsWith("/")) {
            val f = File(target)
            check(f.parentFile?.exists() == true) { "folder hilang" }
            f.writeBytes(bytes)
            true
        } else {
            context.contentResolver.openOutputStream(Uri.parse(target), "wt")?.use { it.write(bytes) } != null
        }
    }.getOrDefault(false)

    /** Nama berisi tanggal dan jam pembuatan file; tidak berubah pada pembaruan berikutnya. */
    private fun newName(): String =
        "mutqin-cadangan-${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.json"

    /** Membuat file baru di Download/Mutqin. Mengembalikan (alamat tersimpan, nama file). */
    @Suppress("DEPRECATION")
    private fun createNew(context: Context, bytes: ByteArray): Pair<String, String> {
        val name = newName()
        if (needsLegacyPermission()) {
            check(hasPermission(context)) { "Izin penyimpanan belum diberikan" }
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUBDIR)
            check(dir.exists() || dir.mkdirs()) { "Folder Download tidak bisa dibuat" }
            val f = File(dir, name)
            f.writeBytes(bytes)
            return f.absolutePath to name
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("Gagal membuat file di Download")
        try {
            resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Gagal menulis file")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        // Nama akhir bisa diberi akhiran "(1)" oleh sistem bila bentrok
        val finalName = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: name
        return uri.toString() to finalName
    }
}

/** Pemilih file yang langsung terbuka di folder Download/Mutqin. */
class OpenBackupDocument : androidx.activity.result.contract.ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, LocalBackup.folderDocumentUri())
}
