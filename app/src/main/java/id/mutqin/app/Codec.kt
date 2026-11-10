package id.mutqin.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialisasi JSON manual (org.json bawaan Android). Nama field SAMA dengan versi web,
 * sehingga file cadangan dari web Mutqin bisa diimpor di sini dan sebaliknya.
 */
object Codec {

    // ---- helper aman terhadap `null` JSON (optString() mengembalikan "null" untuk JSONObject.NULL)
    private fun JSONObject.strOrNull(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)
    private fun JSONObject.intOr(key: String, def: Int): Int = if (isNull(key)) def else optInt(key, def)
    private fun JSONObject.boolOr(key: String, def: Boolean): Boolean = if (isNull(key)) def else optBoolean(key, def)
    private fun JSONObject.strOr(key: String, def: String): String = strOrNull(key) ?: def
    private fun JSONObject.intList(key: String): List<Int>? {
        val arr = optJSONArray(key) ?: return null
        return (0 until arr.length()).map { arr.optInt(it) }
    }

    // ---- ReviewLog -------------------------------------------------------
    private fun logToJson(l: ReviewLog): JSONObject = JSONObject().apply {
        put("id", l.id)
        put("timestamp", l.timestamp)
        put("userRating", l.userRating)
        put("compositeScore", l.compositeScore)
        put("aiGrade", l.aiGrade)
        put("mistakeCount", l.mistakeCount)
        put("hesitationCount", l.hesitationCount)
        put("skippedCount", l.skippedCount)
        put("tajweedCount", l.tajweedCount)
        put("durationSeconds", l.durationSeconds)
        put("previousBox", l.previousBox)
        put("newBox", l.newBox)
        put("nextReviewDate", l.nextReviewDate)
        l.notes?.let { put("notes", it) }
        l.scientificFeedback?.let { put("scientificFeedback", it) }
        if (l.labeledWordsSummary.isNotEmpty()) {
            put("labeledWordsSummary", JSONArray().apply {
                l.labeledWordsSummary.forEach {
                    put(JSONObject().put("ayahNumber", it.ayahNumber).put("wordText", it.wordText).put("label", it.label.key).apply { if (it.wordIndex > 0) put("wordIndex", it.wordIndex) })
                }
            })
        }
    }

    private fun logFromJson(o: JSONObject): ReviewLog {
        val labeled = o.optJSONArray("labeledWordsSummary")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let {
                    LabeledWord(it.intOr("ayahNumber", 0), it.strOr("wordText", ""), ErrorLabel.from(it.strOrNull("label")), it.intOr("wordIndex", 0))
                }
            }
        } ?: emptyList()
        return ReviewLog(
            id = o.strOr("id", "rev_${System.nanoTime()}"),
            timestamp = o.strOr("timestamp", Time.nowIso()),
            userRating = o.intOr("userRating", 3),
            compositeScore = o.intOr("compositeScore", 0),
            aiGrade = o.strOr("aiGrade", ""),
            mistakeCount = o.intOr("mistakeCount", 0),
            hesitationCount = o.intOr("hesitationCount", 0),
            skippedCount = o.intOr("skippedCount", 0),
            tajweedCount = o.intOr("tajweedCount", 0),
            durationSeconds = o.intOr("durationSeconds", 0),
            previousBox = o.intOr("previousBox", 1),
            newBox = o.intOr("newBox", 1),
            nextReviewDate = o.strOr("nextReviewDate", Time.nowIso()),
            notes = o.strOrNull("notes"),
            scientificFeedback = o.strOrNull("scientificFeedback"),
            labeledWordsSummary = labeled
        )
    }

    // ---- CardProgress ----------------------------------------------------
    fun cardToJson(c: CardProgress): JSONObject = JSONObject().apply {
        put("pageNumber", c.pageNumber)
        put("box", c.box)
        put("intervalDays", c.intervalDays)
        put("easeFactor", c.easeFactor)
        put("repetitions", c.repetitions)
        put("lapses", c.lapses)
        c.lastReviewedDate?.let { put("lastReviewedDate", it) }
        put("nextReviewDate", c.nextReviewDate)
        c.lastUserRating?.let { put("lastUserRating", it) }
        c.lastCompositeScore?.let { put("lastCompositeScore", it) }
        put("history", JSONArray().apply { c.history.forEach { put(logToJson(it)) } })
        if (c.pinned) put("pinned", true)
    }

    fun cardFromJson(o: JSONObject, fallbackPage: Int): CardProgress {
        val history = o.optJSONArray("history")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::logFromJson) }
        } ?: emptyList()
        return CardProgress(
            pageNumber = o.intOr("pageNumber", fallbackPage),
            box = o.intOr("box", 1).coerceIn(1, 7),
            intervalDays = o.intOr("intervalDays", 1).coerceAtLeast(1),
            easeFactor = if (o.isNull("easeFactor")) 2.5 else o.optDouble("easeFactor", 2.5),
            repetitions = o.intOr("repetitions", 0).coerceAtLeast(0),
            lapses = o.intOr("lapses", 0).coerceAtLeast(0),
            lastReviewedDate = o.strOrNull("lastReviewedDate"),
            nextReviewDate = o.strOr("nextReviewDate", Time.nowIso()),
            lastUserRating = o.intOrNull("lastUserRating"),
            lastCompositeScore = o.intOrNull("lastCompositeScore"),
            history = history.take(30),
            pinned = o.boolOr("pinned", false)
        )
    }

    fun progressToJson(map: Map<Int, CardProgress>): JSONObject = JSONObject().apply {
        map.toSortedMap().forEach { (page, card) -> put(page.toString(), cardToJson(card)) }
    }

    fun progressFromJson(o: JSONObject): Map<Int, CardProgress> {
        val result = HashMap<Int, CardProgress>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val page = key.toIntOrNull() ?: continue
            if (page !in 1..QuranStructure.TOTAL_PAGES) continue
            val item = o.optJSONObject(key) ?: continue
            result[page] = cardFromJson(item, page)
        }
        return result
    }

    // ---- Settings --------------------------------------------------------
    private fun notifToJson(n: NotificationSettings): JSONObject = JSONObject().apply {
        put("enabled", n.enabled)
        put("title", n.title)
        put("days", JSONArray(n.days))
        put("times", JSONArray().apply {
            n.times.forEach { put(JSONObject().put("id", it.id).put("time", it.time).put("enabled", it.enabled)) }
        })
    }

    private fun notifFromJson(o: JSONObject): NotificationSettings {
        val def = NotificationSettings()
        val times = o.optJSONArray("times")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let {
                    val t = it.strOrNull("time") ?: return@let null
                    ReminderSchedule(it.strOr("id", "t$i"), t, it.boolOr("enabled", true))
                }
            }
        }
        return NotificationSettings(
            enabled = o.boolOr("enabled", def.enabled),
            title = o.strOr("title", def.title),
            days = o.intList("days")?.filter { it in 0..6 }?.distinct()?.ifEmpty { null } ?: def.days,
            times = if (times.isNullOrEmpty()) def.times else times
        )
    }

    fun settingsToJson(s: UserSettings): JSONObject = JSONObject().apply {
        put("dailyTargetPages", s.dailyTargetPages)
        put("activeRecallTimerEnabled", s.activeRecallTimerEnabled)
        put("activeRecallSeconds", s.activeRecallSeconds)
        put("defaultAudioReciter", s.defaultAudioReciter)
        put("theme", s.theme)
        put("enableAIInsights", s.enableAIInsights)
        put("scopeMode", s.scopeMode)
        put("activeScopePages", JSONArray(s.activeScopePages))
        put("activeScopeJuz", JSONArray(s.activeScopeJuz))
        put("activeScopeSurahs", JSONArray(s.activeScopeSurahs))
        s.scopeQueueOrder?.let { put("scopeQueueOrder", JSONArray(it)) }
        s.notifications?.let { put("notifications", notifToJson(it)) }
    }

    /** Menimpa [base] dengan field yang ada di [o] (perilaku `{...base, ...parsed}` pada web). */
    fun settingsFromJson(o: JSONObject, base: UserSettings = UserSettings()): UserSettings {
        val pages = o.intList("activeScopePages")
            ?.filter { it in 1..QuranStructure.TOTAL_PAGES }?.distinct()?.sorted()?.ifEmpty { null }
        return base.copy(
            dailyTargetPages = o.intOr("dailyTargetPages", base.dailyTargetPages).coerceIn(1, 100),
            activeRecallTimerEnabled = o.boolOr("activeRecallTimerEnabled", base.activeRecallTimerEnabled),
            activeRecallSeconds = o.intOr("activeRecallSeconds", base.activeRecallSeconds),
            defaultAudioReciter = o.strOr("defaultAudioReciter", base.defaultAudioReciter),
            theme = o.strOr("theme", base.theme),
            enableAIInsights = o.boolOr("enableAIInsights", base.enableAIInsights),
            scopeMode = o.strOr("scopeMode", base.scopeMode),
            activeScopePages = pages ?: base.activeScopePages,
            activeScopeJuz = o.intList("activeScopeJuz") ?: base.activeScopeJuz,
            activeScopeSurahs = o.intList("activeScopeSurahs") ?: base.activeScopeSurahs,
            scopeQueueOrder = o.intList("scopeQueueOrder") ?: base.scopeQueueOrder,
            notifications = o.optJSONObject("notifications")?.let(::notifFromJson) ?: base.notifications
        )
    }

    // ---- Cadangan / pemindahan data antar-perangkat ---------------------------
    fun exportSync(deviceId: String, progress: Map<Int, CardProgress>, settings: UserSettings): String =
        JSONObject().apply {
            put("version", 1)
            put("deviceId", deviceId)
            put("exportedAt", Time.nowIso())
            put("progressList", progressToJson(progress))
            put("settings", settingsToJson(settings))
        }.toString(2)

    data class ImportOutcome(
        val success: Boolean,
        val count: Int = 0,
        val progress: Map<Int, CardProgress> = emptyMap(),
        val settings: UserSettings? = null,
        val error: String? = null,
        /** Jumlah halaman bermurajaah di dalam file. */
        val incomingReviewed: Int = 0,
        val exportedAt: String? = null
    )

    /**
     * Untuk tiap halaman, data dengan `lastReviewedDate` paling baru dipertahankan dan riwayat kedua sisi digabung
     * ([CardMerge]); bila [replace], progres perangkat dibuang dan seluruhnya diambil dari file. Pengaturan di file (termasuk urutan antrean) langsung diterapkan.
     */
    fun importSync(json: String, existing: Map<Int, CardProgress>, currentSettings: UserSettings, replace: Boolean = false): ImportOutcome {
        return try {
            val root = JSONObject(json.trim())
            val list = root.optJSONObject("progressList")
                ?: return ImportOutcome(false, error = "Format data JSON tidak valid")
            val incoming = progressFromJson(list)

            val merged = if (replace) HashMap(incoming) else HashMap(existing)
            var count = if (replace) incoming.size else 0
            for ((page, item) in if (replace) emptyMap() else incoming) {
                val current = merged[page]
                val result = if (current == null) item else CardMerge.merge(current, item)
                if (result != current) {
                    merged[page] = result
                    count++
                }
            }
            // Urutan antrean ikut file: bila file tidak memuat antrean tersimpan, antrean kembali berurutan (bukan antrean lama perangkat).
            val newSettings = root.optJSONObject("settings")?.let {
                settingsFromJson(it, currentSettings).copy(scopeQueueOrder = it.intList("scopeQueueOrder"))
            }
            ImportOutcome(
                true, count, merged, newSettings,
                incomingReviewed = incoming.values.count { it.repetitions > 0 },
                exportedAt = root.optString("exportedAt").ifEmpty { null }
            )
        } catch (e: Exception) {
            ImportOutcome(false, error = e.message ?: "Gagal membaca data JSON")
        }
    }
}
