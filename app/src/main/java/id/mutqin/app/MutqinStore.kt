package id.mutqin.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Penyimpanan lokal: progres & pengaturan (file JSON atomik) + preferensi ringan (SharedPreferences). */
class MutqinStore(context: Context) {
    private val appContext = context.applicationContext
    private val progressFile = File(appContext.filesDir, "progress_v1.json")
    private val settingsFile = File(appContext.filesDir, "settings_v1.json")
    private val prefs = appContext.getSharedPreferences("mutqin_prefs", Context.MODE_PRIVATE)

    private fun readText(file: File): String? =
        try {
            if (file.exists()) AtomicFile(file).readFully().toString(Charsets.UTF_8) else null
        } catch (e: Exception) {
            null
        }

    private fun writeAtomic(file: File, text: String) {
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    fun loadProgress(): Map<Int, CardProgress> =
        try {
            readText(progressFile)?.let { Codec.progressFromJson(JSONObject(it)) } ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }

    fun saveProgress(map: Map<Int, CardProgress>) {
        writeAtomic(progressFile, Codec.progressToJson(map).toString())
    }

    fun loadSettings(): UserSettings =
        try {
            readText(settingsFile)?.let { Codec.settingsFromJson(JSONObject(it)) } ?: UserSettings()
        } catch (e: Exception) {
            UserSettings()
        }

    fun saveSettings(settings: UserSettings) {
        writeAtomic(settingsFile, Codec.settingsToJson(settings).toString())
    }

    val deviceId: String
        get() {
            prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
            val id = "dev_" + UUID.randomUUID().toString().substring(0, 8)
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            return id
        }

    var lastPage: Int?
        get() = if (prefs.contains(KEY_LAST_PAGE)) prefs.getInt(KEY_LAST_PAGE, 1) else null
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_LAST_PAGE) else putInt(KEY_LAST_PAGE, value) }.apply()
        }

    /** Tema aplikasi: "mushaf", "emerald", atau "dark". Pengguna lama (mode gelap/terang) dimigrasi otomatis. */
    var themeKey: String
        get() = prefs.getString(KEY_THEME, null) ?: if (prefs.getBoolean(KEY_DARK, true)) "dark" else "mushaf"
        set(value) { prefs.edit().putString(KEY_THEME, value).apply() }

    /** Cadangan otomatis ke Download/Mutqin (aktif secara bawaan). */
    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP, true)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_BACKUP, value).apply() }

    /** Waktu (ms) cadangan otomatis terakhir yang berhasil ditulis; 0 = belum pernah. */
    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_BACKUP, value).apply() }

    /** Dialog dua pilihan pada pembukaan pertama sudah dijawab (agar tidak muncul terus). */
    var restoreChecked: Boolean
        get() = prefs.getBoolean(KEY_RESTORE_CHECKED, false)
        set(value) { prefs.edit().putBoolean(KEY_RESTORE_CHECKED, value).apply() }

    /** File cadangan yang terus diperbarui: file buatan aplikasi di Download/Mutqin (null = buat file baru pada penyimpanan berikutnya). */
    var backupUri: String?
        get() = prefs.getString(KEY_BACKUP_URI, null)
        set(value) { prefs.edit().apply { if (value == null) remove(KEY_BACKUP_URI) else putString(KEY_BACKUP_URI, value) }.apply() }

    var backupName: String?
        get() = prefs.getString(KEY_BACKUP_NAME, null)
        set(value) { prefs.edit().apply { if (value == null) remove(KEY_BACKUP_NAME) else putString(KEY_BACKUP_NAME, value) }.apply() }

    private companion object {
        const val KEY_BACKUP_URI = "backup_uri"
        const val KEY_BACKUP_NAME = "backup_name"
        const val KEY_RESTORE_CHECKED = "restore_checked"
        const val KEY_AUTO_BACKUP = "auto_backup"
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_LAST_PAGE = "last_page"
        const val KEY_DARK = "dark_mode"
        const val KEY_THEME = "theme_key"
    }
}
