package id.mutqin.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class CardSide { FRONT, BACK }
enum class MainTab { FLASHCARD, EXPLORER, STATS, GUIDE }

data class LabelCounts(val mistake: Int = 0, val hesitation: Int = 0, val skipped: Int = 0, val tajweed: Int = 0) {
    val total: Int get() = mistake + hesitation + skipped + tajweed
}

fun countLabels(ayahs: List<QuranAyah>, labels: Map<String, ErrorLabel>): LabelCounts {
    var m = 0; var h = 0; var s = 0; var t = 0
    for (a in ayahs) for (w in a.words) {
        when (labels[w.id]) {
            ErrorLabel.MISTAKE -> m++
            ErrorLabel.HESITATION -> h++
            ErrorLabel.SKIPPED -> s++
            ErrorLabel.TAJWEED -> t++
            else -> Unit
        }
    }
    return LabelCounts(m, h, s, t)
}

data class GradingUi(
    val pageNumber: Int,
    val result: EvaluationResult,
    val userRating: Int,
    val counts: LabelCounts
)

class MutqinViewModel(private val app: Application) : AndroidViewModel(app) {

    private val store = MutqinStore(app)
    private val persistMutex = Mutex()

    var isReady by mutableStateOf(false); private set
    var progress by mutableStateOf<Map<Int, CardProgress>>(emptyMap()); private set
    var settings by mutableStateOf(UserSettings()); private set
    var stats by mutableStateOf(DailyStats()); private set

    var tab by mutableStateOf(MainTab.FLASHCARD)
    var currentPage by mutableIntStateOf(1); private set
    var cardSide by mutableStateOf(CardSide.FRONT); private set
    var elapsedSeconds by mutableIntStateOf(0); private set

    var pageText by mutableStateOf<PageText?>(null); private set
    /** Gambar halaman utuh + kotak kata; null bila aset gambar belum ada (layar memakai tampilan teks). */
    var pageImage by mutableStateOf<PageImage?>(null); private set
    var dueNowPages by mutableStateOf<List<Int>>(emptyList()); private set
    var isLoadingText by mutableStateOf(true); private set
    var pageTextError by mutableStateOf(false); private set
    var labels by mutableStateOf<Map<String, ErrorLabel>>(emptyMap()); private set
    /** Label dari murajaah sebelumnya pada halaman ini (id kata -> label); ditampilkan pudar sebagai bahan evaluasi. */
    var previousLabels by mutableStateOf<Map<String, ErrorLabel>>(emptyMap()); private set
    var previousReviewAt by mutableStateOf<String?>(null); private set
    var showPreviousLabels by mutableStateOf(true); private set

    var grading by mutableStateOf<GradingUi?>(null); private set
    var themeKey by mutableStateOf(store.themeKey); private set

    val deviceId: String get() = store.deviceId

    private var textJob: Job? = null

    // ---- cadangan otomatis ke Download/Mutqin
    var autoBackupEnabled by mutableStateOf(store.autoBackupEnabled); private set
    var lastBackupAt by mutableStateOf(store.lastBackupAt); private set
    var backupStatus by mutableStateOf<Pair<String, Boolean>?>(null); private set
    private var backupJob: Job? = null

    private val backupMutex = Mutex()

    /** Tanggal (lokal) saat kartu aktif ditentukan; dipakai agar kartu dipilih ulang saat hari berganti. */
    private var cardDay: LocalDate = LocalDate.now()

    init {
        viewModelScope.launch {
            val (p, s) = withContext(Dispatchers.IO) { store.loadProgress() to store.loadSettings() }
            progress = p
            settings = s
            currentPage = openingPage(p, s)
            cardDay = LocalDate.now()
            recomputeStats()
            isReady = true
            val reviewed = p.values.any { it.repetitions > 0 }
            if (reviewed) store.restoreChecked = true
            else if (!store.restoreChecked) firstRunStep = FirstRunStep.CHOOSE
            loadPageText()
            ReminderScheduler.schedule(app, s, acknowledgePast = true)
        }
    }

    // ------------------------------------------------------------------ navigasi

    /**
     * Kartu yang tampil saat aplikasi dibuka: selalu ditentukan mesin antrean
     * (utang jatuh tempo -> jatuh tempo hari ini -> halaman baru sesuai antrean -> early review),
     * bukan "halaman yang terakhir dibuka". Dengan begitu halaman yang sudah dinilai tidak muncul lagi,
     * dan setiap hari utang selalu didahulukan.
     */
    private fun openingPage(prog: Map<Int, CardProgress>, s: UserSettings): Int {
        val pick = QueueEngine.nextFlashcard(prog, s.activeScopePages, null, s.scopeQueueOrder)
        return pick?.pageNumber ?: s.activeScopePages.minOrNull() ?: 1
    }

    /** true jika kartu yang tampil dibuka manual (dari daftar halaman/statistik), bukan dari antrean. */
    var isManualPick by mutableStateOf(false); private set

    /** Alasan kartu yang tampil sekarang dipilih antrean (null jika dibuka manual atau bukan kartu antrean). */
    fun queueReason(): String? {
        val pick = QueueEngine.nextFlashcard(progress, settings.activeScopePages, null, settings.scopeQueueOrder) ?: return null
        if (pick.pageNumber != currentPage) return null
        return when (pick.reason) {
            PickReason.DUE_SRS -> {
                if (pick.isOverdueDebt) {
                    val due = progress[pick.pageNumber]?.let { NextReviewLabel.describe(it.nextReviewDate).text } ?: "-"
                    "Utang murajaah: jatuh tempo $due (Box ${pick.box})"
                } else {
                    "Jatuh tempo hari ini (Box ${pick.box})"
                }
            }
            PickReason.NEW_UNREVIEWED -> "Halaman baru sesuai urutan antrean"
            PickReason.EARLY_REVIEW -> "Semua target selesai: review dini (jadwal terdekat)"
        }
    }

    /** Kartu utama menurut antrean (utang -> jatuh tempo hari ini -> halaman baru -> early review). */
    fun mainCardPage(): Int = openingPage(progress, settings)

    /** Kembali ke flashcard utama yang ada di antrean. */
    fun returnToMainCard() {
        selectPage(mainCardPage())
    }

    fun selectPage(page: Int, manual: Boolean = false, switchTab: Boolean = true) {
        isManualPick = manual
        if (page !in 1..QuranStructure.TOTAL_PAGES) return
        currentPage = page
        cardDay = LocalDate.now()
        cardSide = CardSide.FRONT
        elapsedSeconds = 0
        if (switchTab) tab = MainTab.FLASHCARD
        store.lastPage = page
        loadPageText()
    }

    fun flipToBack(durationSeconds: Int) {
        elapsedSeconds = durationSeconds
        cardSide = CardSide.BACK
    }

    fun backToFront() {
        cardSide = CardSide.FRONT
    }

    private fun loadPageText() {
        textJob?.cancel()
        val page = currentPage
        isLoadingText = true
        pageTextError = false
        pageText = null
        pageImage = null
        labels = emptyMap()
        previousLabels = emptyMap()
        previousReviewAt = null
        textJob = viewModelScope.launch {
            val text = QuranRepository.getPage(app, page)
            val image = text?.let { QuranRepository.getPageImage(app, page, it) }
            if (page == currentPage) {
                pageText = text
                pageImage = image
                pageTextError = text == null
                val lastLog = progress[page]?.history?.firstOrNull()
                previousLabels = if (text != null) PreviousLabels.map(text.ayahs, lastLog) else emptyMap()
                previousReviewAt = if (previousLabels.isNotEmpty()) lastLog?.timestamp else null
                isLoadingText = false
            }
        }
    }

    // ------------------------------------------------------------------ pelabelan kata / ayat

    fun toggleWordLabel(wordId: String, tool: ErrorLabel) {
        val current = labels[wordId] ?: ErrorLabel.NONE
        val next = if (current == tool) ErrorLabel.NONE else tool
        labels = if (next == ErrorLabel.NONE) labels - wordId else labels + (wordId to next)
    }

    fun toggleAyahLabel(ayah: QuranAyah, tool: ErrorLabel) {
        val allHave = ayah.words.all { (labels[it.id] ?: ErrorLabel.NONE) == tool }
        setAyahLabel(ayah, if (allHave) ErrorLabel.NONE else tool)
    }

    fun setAyahLabel(ayah: QuranAyah, label: ErrorLabel) {
        val m = labels.toMutableMap()
        for (w in ayah.words) if (label == ErrorLabel.NONE) m.remove(w.id) else m[w.id] = label
        labels = m
    }

    fun clearAllLabels() {
        labels = emptyMap()
    }

    // ------------------------------------------------------------------ penilaian & SRS

    fun finishReview(userRating: Int) {
        val text = pageText ?: return
        val page = currentPage
        val duration = elapsedSeconds

        val counts = countLabels(text.ayahs, labels)
        val labeled = ArrayList<LabeledWord>()
        for (a in text.ayahs) for (w in a.words) {
            val l = labels[w.id] ?: ErrorLabel.NONE
            if (l != ErrorLabel.NONE) labeled += LabeledWord(a.ayahNumber, w.text, l, w.position)
        }

        val current = progress[page] ?: CardProgress(pageNumber = page, nextReviewDate = Time.nowIso())
        val result = SrsEngine.evaluate(
            EvaluationInput(
                userRating, counts.mistake, counts.hesitation, counts.skipped, counts.tajweed, current.box,
                cukupStreak = SrsEngine.cukupStreak(current.history)
            )
        )

        val nowIso = Time.nowIso()
        val nextIso = Time.plusDaysIso(result.intervalDays)
        val log = ReviewLog(
            id = "rev_${System.currentTimeMillis()}_p$page",
            timestamp = nowIso,
            userRating = userRating,
            compositeScore = result.compositeScore,
            aiGrade = result.aiGrade,
            mistakeCount = counts.mistake,
            hesitationCount = counts.hesitation,
            skippedCount = counts.skipped,
            tajweedCount = counts.tajweed,
            durationSeconds = duration,
            previousBox = current.box,
            newBox = result.recommendedBox,
            nextReviewDate = nextIso,
            labeledWordsSummary = labeled
        )
        val updated = current.copy(
            box = result.recommendedBox,
            intervalDays = result.intervalDays,
            repetitions = current.repetitions + 1,
            lapses = if (counts.mistake > 0 || counts.skipped > 0) current.lapses + 1 else current.lapses,
            lastReviewedDate = nowIso,
            nextReviewDate = nextIso,
            lastUserRating = userRating,
            lastCompositeScore = result.compositeScore,
            history = (listOf(log) + current.history).take(30)
        )

        progress = progress + (page to updated)
        recomputeStats()
        grading = GradingUi(page, result, userRating, counts)
        persistProgress()
        scheduleLocalBackup()
    }

    fun dismissGrading() {
        grading = null
    }

    /** "Simpan & Lanjut": Bayar utang dulu -> jatuh tempo hari ini -> halaman baru sesuai antrean. */
    fun nextAfterReview() {
        grading = null
        val pick = nextPick(excludeCurrent = true)
        if (pick != null) {
            selectPage(pick.pageNumber)
        } else {
            val scope = settings.activeScopePages
            if (scope.isNotEmpty()) {
                val idx = scope.indexOf(currentPage)
                selectPage(scope[(idx + 1) % scope.size])
            }
        }
    }

    fun nextPick(excludeCurrent: Boolean = true): NextCardSelection? =
        QueueEngine.nextFlashcard(
            progress, settings.activeScopePages,
            if (excludeCurrent) currentPage else null,
            settings.scopeQueueOrder
        )

    /** Halaman yang akan dipakai di isi notifikasi. */
    val targetNextPage: Int get() = nextPick()?.pageNumber ?: currentPage

    // ------------------------------------------------------------------ pengaturan

    fun updateSettings(updated: UserSettings) {
        settings = updated
        recomputeStats()
        persistSettings()
        ReminderScheduler.schedule(app, updated, acknowledgePast = true)
        scheduleLocalBackup()
    }

    fun updateDailyTarget(pages: Int) = updateSettings(settings.copy(dailyTargetPages = pages.coerceIn(1, 100)))

    /**
     * Menyimpan target hafalan baru. Antrean dan kartu aktif diatur oleh [ScopeTransition]:
     * jika rentang baru dimulai setelah kartu aktif, kartu langsung berpindah ke halaman paling awal rentang baru.
     */
    fun saveScope(mode: String, pages: Set<Int>) {
        if (pages.isEmpty()) return
        val result = ScopeTransition.apply(
            oldScope = settings.activeScopePages,
            oldQueue = settings.scopeQueueOrder,
            newScopePages = pages,
            currentPage = currentPage,
            progress = progress
        )
        val effective = pages.sorted()
        val juz = (1..30).filter { j -> QuranStructure.pagesInJuz(j).any { it in pages } }
        val surahs = QuranData.surahs
            .filter { s -> (s.startPage..s.endPage).any { it in pages } }
            .map { it.number }
        updateSettings(
            settings.copy(
                scopeMode = mode,
                activeScopePages = effective,
                scopeQueueOrder = result.queueOrder,
                activeScopeJuz = juz,
                activeScopeSurahs = surahs
            )
        )
        if (result.jumped) selectPage(result.page)
    }

    fun togglePreviousLabels() {
        showPreviousLabels = !showPreviousLabels
    }

    /**
     * Menghapus seluruh progres murajaah di perangkat ini (pengaturan tetap, antrean kembali berurutan).
     * Sama seperti "mulai dari awal" pada dialog pertama: file cadangan lama dibiarkan dan dibuat file baru
     * (nama bertanggal+jam) di Download/Mutqin yang akan terus diperbarui.
     */
    fun resetLocalData() {
        progress = emptyMap()
        persistProgress()
        settings = settings.copy(scopeQueueOrder = null)
        persistSettings()
        recomputeStats()
        selectPage(openingPage(progress, settings))
        startNewBackupFile()
    }

    /** Melepas file cadangan yang sedang dipakai dan segera membuat file baru (progres baru / impor baru). */
    private fun startNewBackupFile() {
        store.backupUri = null
        store.backupName = null
        scheduleLocalBackup(immediate = true)
    }

    fun setTheme(key: String) {
        themeKey = key
        store.themeKey = key
    }

    // ------------------------------------------------------------------ cadangan JSON

    suspend fun exportJson(): String = withContext(Dispatchers.Default) {
        Codec.exportSync(store.deviceId, progress, settings)
    }

    // ---- impor JSON: baca dulu -> tanya pengguna (ambil semua dari file / gabung) -> terapkan
    var pendingImport by mutableStateOf<PendingImport?>(null); private set
    // ---- pembukaan pertama: dua pilihan wajib (tanpa memeriksa isi folder)
    var firstRunStep by mutableStateOf(FirstRunStep.NONE); private set

    /** Pilihan "Pertama kali menggunakan aplikasi / mulai dari awal": langsung membuat file cadangan baru di Download/Mutqin. */
    fun finishFreshStart() {
        store.restoreChecked = true
        firstRunStep = FirstRunStep.NONE
        startNewBackupFile()
    }

    /** Pilihan "Impor progres lama": file dipilih lewat pemilih file; seluruh isi file diambil. */
    suspend fun importFirstRun(json: String): Result<Int> {
        val o = withContext(Dispatchers.Default) { Codec.importSync(json, progress, settings) }
        if (!o.success) return Result.failure(IllegalArgumentException(o.error ?: "Format data tidak sesuai"))
        pendingImport = PendingImport(json, "File yang Anda pilih", o.incomingReviewed, o.exportedAt, 0)
        return confirmImport(replace = true).also { if (it.isSuccess) firstRunStep = FirstRunStep.NONE }
    }

    /** Membaca [json] tanpa mengubah data; bila sah, [pendingImport] terisi dan dialog pilihan muncul. */
    suspend fun prepareImport(json: String, source: String): Result<Unit> {
        val o = withContext(Dispatchers.Default) { Codec.importSync(json, progress, settings) }
        if (!o.success) return Result.failure(IllegalArgumentException(o.error ?: "Format data tidak sesuai"))
        pendingImport = PendingImport(json, source, o.incomingReviewed, o.exportedAt, progress.values.count { it.repetitions > 0 })
        return Result.success(Unit)
    }

    fun cancelImport() {
        pendingImport = null
    }

    /**
     * Menerapkan impor. [replace] = ambil SEMUA dari file (progres perangkat diganti); kalau tidak, digabung per halaman
     * (murajaah terbaru menang, riwayat disatukan). Pengaturan + urutan antrean file dipakai, lalu kartu yang tampil
     * langsung dipilih ulang dari antrean itu. Mengembalikan jumlah halaman yang diperbarui.
     */
    suspend fun confirmImport(replace: Boolean): Result<Int> {
        val pending = pendingImport ?: return Result.failure(IllegalStateException("Tidak ada impor yang menunggu"))
        val outcome = withContext(Dispatchers.Default) { Codec.importSync(pending.json, progress, settings, replace) }
        if (!outcome.success) return Result.failure(IllegalArgumentException(outcome.error ?: "Format data tidak sesuai"))
        pendingImport = null
        store.restoreChecked = true
        // File yang diimpor boleh di mana saja dan tidak diperbarui; cadangan berikutnya masuk ke file baru di Download/Mutqin
        store.backupUri = null
        store.backupName = null

        progress = outcome.progress
        outcome.settings?.let { settings = it }
        recomputeStats()
        persistProgress()
        persistSettings()
        ReminderScheduler.schedule(app, settings, acknowledgePast = true)
        scheduleLocalBackup(immediate = true)

        // Label/penilaian yang belum disimpan dibuang karena kartunya berganti.
        grading = null
        selectPage(openingPage(progress, settings), manual = false, switchTab = true)
        return Result.success(outcome.count)
    }

    // ------------------------------------------------------------------ cadangan otomatis

    fun setAutoBackup(enabled: Boolean) {
        autoBackupEnabled = enabled
        store.autoBackupEnabled = enabled
        if (enabled) scheduleLocalBackup(immediate = true)
    }

    /** Menunda sebentar (menggabungkan perubahan beruntun) lalu menulis cadangan terbaru ke Download/Mutqin. */
    fun scheduleLocalBackup(immediate: Boolean = false) {
        if (!autoBackupEnabled || !isReady) return
        backupJob?.cancel()
        backupJob = viewModelScope.launch {
            if (!immediate) delay(BACKUP_DEBOUNCE_MS)
            writeLocalBackup(manual = false)
        }
    }

    /** Dipanggil saat aplikasi ditinggalkan: tulis sekarang juga bila ada perubahan yang masih menunggu. */
    fun flushLocalBackup() {
        if (!autoBackupEnabled || !isReady) return
        if (backupJob?.isActive == true) scheduleLocalBackup(immediate = true)
    }

    /** Tombol "Simpan sekarang" di dialog cadangan. */
    fun backupNow() {
        backupJob?.cancel()
        backupJob = viewModelScope.launch { writeLocalBackup(manual = true) }
    }

    private suspend fun writeLocalBackup(manual: Boolean) {
        val json = exportJson()
        val result = withContext(NonCancellable + Dispatchers.IO) { backupMutex.withLock { LocalBackup.save(app, store, json) } }
        result.fold(
            onSuccess = { label ->
                lastBackupAt = System.currentTimeMillis()
                store.lastBackupAt = lastBackupAt
                backupStatus = "Cadangan terbaru tersimpan: $label" to true
            },
            onFailure = { e ->
                // Gagal otomatis tidak mengganggu; pesan hanya tampil di dialog cadangan.
                backupStatus = (e.message ?: "Gagal menyimpan cadangan ke Download.") to false
            }
        )
    }

    // ------------------------------------------------------------------ util

    fun refresh() {
        if (!isReady) return
        recomputeStats()
        // Hari berganti saat aplikasi masih terbuka di kartu depan: utang hari baru harus didahulukan.
        if (LocalDate.now() != cardDay && tab == MainTab.FLASHCARD && cardSide == CardSide.FRONT && grading == null) {
            val page = openingPage(progress, settings)
            if (page != currentPage) selectPage(page) else cardDay = LocalDate.now()
        }
    }

    private fun recomputeStats() {
        // Jumlah & daftar jatuh tempo dihitung dengan aturan yang sama dengan antrean kartu (rentang aktif, berbasis hari)
        val due = QueueEngine.dueCards(progress, settings.activeScopePages)
        dueNowPages = due.map { it.page }
        stats = StatsEngine.dailyStats(progress.values.sortedBy { it.pageNumber }).copy(dueTodayCount = due.size)
    }

    private fun persistProgress() {
        val snapshot = progress
        viewModelScope.launch {
            withContext(NonCancellable + Dispatchers.IO) {
                persistMutex.withLock { runCatching { store.saveProgress(snapshot) } }
            }
        }
    }

    private fun persistSettings() {
        val snapshot = settings
        viewModelScope.launch {
            withContext(NonCancellable + Dispatchers.IO) {
                persistMutex.withLock { runCatching { store.saveSettings(snapshot) } }
            }
        }
    }
}

private const val BACKUP_DEBOUNCE_MS = 1500L

data class PendingImport(
    val json: String,
    val source: String,
    val filePages: Int,
    val exportedAt: String?,
    val localPages: Int
)

enum class FirstRunStep { NONE, CHOOSE }
