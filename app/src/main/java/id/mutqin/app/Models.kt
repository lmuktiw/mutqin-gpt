package id.mutqin.app

/** Label kesalahan per kata/ayat (sama dengan ErrorLabelType di versi web). */
enum class ErrorLabel(val key: String) {
    MISTAKE("mistake"),
    HESITATION("hesitation"),
    SKIPPED("skipped"),
    TAJWEED("tajweed"),
    NONE("none");

    companion object {
        fun from(key: String?): ErrorLabel = entries.firstOrNull { it.key == key } ?: NONE
    }
}

enum class PageSide(val id: String, val title: String) {
    DEPAN("depan", "Depan"),
    BELAKANG("belakang", "Belakang")
}

data class SurahMeta(
    val number: Int,
    val nameArabic: String,
    val nameLatin: String,
    val startPage: Int,
    val endPage: Int,
    val totalVerses: Int
)

data class QuranPageMeta(
    val pageNumber: Int,
    val juz: Int,
    val lembar: Int,
    val side: PageSide,
    /** Mushaf Madinah: halaman ganjil = kanan, genap = kiri. */
    val isRightPage: Boolean,
    val surahNumber: Int,
    val surahNameArabic: String,
    val surahNameLatin: String,
    val ayahRange: String,
    val memoryTriggerArabic: String
) {
    val positionLabel: String get() = if (isRightPage) "Halaman Kanan" else "Halaman Kiri"
    val positionLabelLong: String get() = if (isRightPage) "Halaman Kanan (Ganjil)" else "Halaman Kiri (Genap)"
    val juzLembarLabel: String get() = "Juz $juz Lembar $lembar ${side.id}"
}

data class QuranWord(
    val id: String,
    val text: String,
    val ayahNumber: Int,
    val position: Int,
    /** Nomor baris (1..15) pada halaman cetak mushaf Madinah; 0 = tidak diketahui. */
    val line: Int = 0
)

data class QuranAyah(
    val surahNumber: Int,
    val ayahNumber: Int,
    val words: List<QuranWord>
)

/** Teks lengkap satu halaman (dari assets APK). */
data class PageText(
    val ayahs: List<QuranAyah>
)

data class LabeledWord(
    val ayahNumber: Int,
    val wordText: String,
    val label: ErrorLabel,
    /** Nomor kata dalam ayat (1..n); 0 = tidak diketahui (log lama). */
    val wordIndex: Int = 0
)

data class ReviewLog(
    val id: String,
    val timestamp: String,           // ISO-8601 (UTC)
    val userRating: Int,             // 1..5
    val compositeScore: Int,         // 0..100
    val aiGrade: String,
    val mistakeCount: Int,
    val hesitationCount: Int,
    val skippedCount: Int,
    val tajweedCount: Int,
    val durationSeconds: Int,
    val previousBox: Int,
    val newBox: Int,
    val nextReviewDate: String,
    val notes: String? = null,
    val scientificFeedback: String? = null,
    val labeledWordsSummary: List<LabeledWord> = emptyList()
)

data class CardProgress(
    val pageNumber: Int,
    val box: Int = 1,                // Leitner 1..7
    val intervalDays: Int = 1,
    val easeFactor: Double = 2.5,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val lastReviewedDate: String? = null,
    val nextReviewDate: String,
    val lastUserRating: Int? = null,
    val lastCompositeScore: Int? = null,
    val history: List<ReviewLog> = emptyList(),
    val pinned: Boolean = false
) {
    val isReviewed: Boolean get() = repetitions > 0
}

data class ReminderSchedule(
    val id: String,
    val time: String,                // "HH:mm"
    val enabled: Boolean
)

data class NotificationSettings(
    val enabled: Boolean = true,
    val title: String = DEFAULT_TITLE,
    /** 0 = Ahad, 1 = Senin, ... 6 = Sabtu (sama dengan JavaScript Date.getDay). */
    val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 0),
    val times: List<ReminderSchedule> = listOf(
        ReminderSchedule("1", "05:00", true),
        ReminderSchedule("2", "18:30", true)
    )
) {
    companion object {
        const val DEFAULT_TITLE = "Waktunya Murajaah Mutqin!"
    }
}

data class UserSettings(
    val dailyTargetPages: Int = 5,
    val activeRecallTimerEnabled: Boolean = true,
    val activeRecallSeconds: Int = 30,
    val defaultAudioReciter: String = "Alafasy_128kbps",
    val theme: String = "mushaf",
    val enableAIInsights: Boolean = true,
    val scopeMode: String = "all",   // "all" | "custom"
    val activeScopePages: List<Int> = (1..604).toList(),
    val activeScopeJuz: List<Int> = (1..30).toList(),
    val activeScopeSurahs: List<Int> = (1..114).toList(),
    val scopeQueueOrder: List<Int>? = null,
    val notifications: NotificationSettings? = null
)

data class EvaluationInput(
    val userRating: Int,
    val mistakeCount: Int,
    val hesitationCount: Int,
    val skippedCount: Int,
    val tajweedCount: Int,
    val currentBox: Int,
    /** Berapa kali berturut-turut murajaah terakhir berpredikat "Cukup" tanpa berpindah box (lihat SrsEngine.cukupStreak). */
    val cukupStreak: Int = 0
)

data class EvaluationResult(
    val compositeScore: Int,
    val aiGrade: String,
    val recommendedBox: Int,
    val intervalDays: Int,
    val intervalLabel: String,
    val scientificDiagnosis: String,
    val taktikAliRajabi: List<String>
)
