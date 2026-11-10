package id.mutqin.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Pengingat murajaah harian (pengganti pemeriksa jadwal 30-detik di versi web). */
object ReminderScheduler {
    const val CHANNEL_ID = "mutqin_reminder"
    private const val REQUEST_CODE = 4101
    private const val NOTIF_ID = 4102

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, "Pengingat Murajaah", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pengingat harian untuk murajaah hafalan"
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /** Waktu pengingat berikutnya (hari terpilih + jam aktif) setelah [now], atau null. */
    fun nextTrigger(n: NotificationSettings, now: ZonedDateTime): ZonedDateTime? {
        if (!n.enabled) return null
        val times = n.times.filter { it.enabled }.mapNotNull { runCatching { LocalTime.parse(it.time) }.getOrNull() }
        if (times.isEmpty() || n.days.isEmpty()) return null

        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val dayId = date.dayOfWeek.value % 7 // Senin=1 ... Sabtu=6, Ahad=0
            if (dayId !in n.days) continue
            val candidate = times.sorted()
                .map { date.atTime(it).atZone(now.zone) }
                .firstOrNull { it.isAfter(now) }
            if (candidate != null) return candidate
        }
        return null
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // ------------------------------------------------------------------ jadwal alarm + jaring pengaman

    private const val WATCHDOG_NAME = "mutqin_reminder_watchdog"
    private const val PREFS = "mutqin_reminders"
    private const val KEY_FIRED = "fired_slots"

    /**
     * Menjadwalkan alarm berikutnya dan memastikan pemeriksa cadangan (WorkManager, tiap ~15 menit) aktif.
     * [acknowledgePast] = true menandai jam yang sudah lewat hari ini sebagai "sudah ditangani"
     * (dipakai saat pengguna mengubah pengaturan atau membuka aplikasi) agar tidak muncul notifikasi susulan.
     */
    fun schedule(context: Context, settings: UserSettings, acknowledgePast: Boolean = false) {
        val appCtx = context.applicationContext
        val n = settings.notifications
        if (acknowledgePast && n != null) acknowledgePastSlots(appCtx, n)
        scheduleAlarm(appCtx, settings)
        if (n != null && n.enabled && n.times.any { it.enabled }) ensureWatchdog(appCtx) else cancelWatchdog(appCtx)
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleAlarm(context: Context, settings: UserSettings) {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(appCtx)
        am.cancel(pi)

        val n = settings.notifications ?: return
        val next = nextTrigger(n, ZonedDateTime.now(ZoneId.systemDefault())) ?: return
        val millis = next.toInstant().toEpochMilli()

        try {
            if (canScheduleExact(appCtx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun ensureWatchdog(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWatchdogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WATCHDOG_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun cancelWatchdog(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WATCHDOG_NAME)
    }

    /** Jam pengingat yang sudah lewat dalam [graceMinutes] terakhir pada hari ini (hanya hari yang dipilih). */
    fun dueSlots(n: NotificationSettings, now: ZonedDateTime, graceMinutes: Long): List<ZonedDateTime> {
        if (!n.enabled) return emptyList()
        val today = now.toLocalDate()
        if ((today.dayOfWeek.value % 7) !in n.days) return emptyList()
        val earliest = now.minusMinutes(graceMinutes)
        return n.times.filter { it.enabled }
            .mapNotNull { runCatching { LocalTime.parse(it.time) }.getOrNull() }
            .distinct().sorted()
            .map { today.atTime(it).atZone(now.zone) }
            .filter { !it.isAfter(now) && it.isAfter(earliest) }
    }

    fun slotKey(slot: ZonedDateTime): String =
        "${slot.toLocalDate()} " + String.format(Locale.US, "%02d:%02d", slot.hour, slot.minute)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun firedKeys(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_FIRED, emptySet()) ?: emptySet()

    private fun markFired(context: Context, keys: Collection<String>, today: LocalDate) {
        val oldest = today.minusDays(1).toString()
        val kept = (firedKeys(context) + keys).filter { it.take(10) >= oldest }.toSet()
        prefs(context).edit().putStringSet(KEY_FIRED, kept).apply()
    }

    private fun acknowledgePastSlots(context: Context, n: NotificationSettings) {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        markFired(context, dueSlots(n, now, 24 * 60).map(::slotKey), now.toLocalDate())
    }

    /**
     * Menampilkan notifikasi untuk jam yang sudah lewat tetapi belum pernah ditampilkan
     * (dipanggil alarm tepat waktu dan pemeriksa cadangan). Satu notifikasi per panggilan,
     * dan setiap jam hanya ditampilkan sekali. Mengembalikan true jika notifikasi ditampilkan.
     */
    fun deliverDue(context: Context, graceMinutes: Long): Boolean {
        val appCtx = context.applicationContext
        val store = MutqinStore(appCtx)
        val settings = store.loadSettings()
        val n = settings.notifications ?: return false
        if (!n.enabled) return false

        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val fired = firedKeys(appCtx)
        val unfired = dueSlots(n, now, graceMinutes).filter { slotKey(it) !in fired }
        if (unfired.isEmpty()) return false
        markFired(appCtx, unfired.map(::slotKey), now.toLocalDate()) // tandai dulu agar tidak dobel

        QuranData.init(appCtx)
        val progress = store.loadProgress()
        val stats = StatsEngine.dailyStats(progress.values.sortedBy { it.pageNumber })
        val pick = QueueEngine.nextFlashcard(progress, settings.activeScopePages, null, settings.scopeQueueOrder)
        val meta = QuranData.pageMeta(pick?.pageNumber ?: store.lastPage ?: 1)
        show(
            appCtx,
            n.title.ifBlank { NotificationSettings.DEFAULT_TITLE },
            buildBody(stats.streakDays, meta)
        )
        return true
    }

    fun cancel(context: Context) {
        val appCtx = context.applicationContext
        appCtx.getSystemService(AlarmManager::class.java).cancel(pendingIntent(appCtx))
    }

    fun buildBody(streakDays: Int, next: QuranPageMeta): String =
        "🔥 Rekor Streak: $streakDays Hari Berturut-turut\n" +
            "📖 Halaman berikutnya: Hal ${next.pageNumber}, Lembar ke-${next.lembar} (${next.side.title}) Juz ${next.juz}"

    @SuppressLint("MissingPermission")
    fun show(context: Context, title: String, body: String) {
        val appCtx = context.applicationContext
        if (!hasNotificationPermission(appCtx)) return
        ensureChannel(appCtx)

        val open = PendingIntent.getActivity(
            appCtx, 0,
            Intent(appCtx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mutqin)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(appCtx).notify(NOTIF_ID, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.deliverDue(appCtx, graceMinutes = 30)
            } catch (e: Exception) {
                // jangan sampai galat menghentikan penjadwalan berikutnya
            } finally {
                try {
                    // jadwalkan pengingat berikutnya
                    ReminderScheduler.scheduleAlarm(appCtx, MutqinStore(appCtx).loadSettings())
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION = "id.mutqin.app.ACTION_REMINDER"
    }
}

/** Pemeriksa cadangan: menyusulkan notifikasi yang terlewat (mis. alarm dimatikan penghemat baterai) dan memasang ulang alarm. */
class ReminderWatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            ReminderScheduler.deliverDue(applicationContext, graceMinutes = 120)
            ReminderScheduler.scheduleAlarm(applicationContext, MutqinStore(applicationContext).loadSettings())
        } catch (e: Exception) {
            // dicoba lagi pada putaran berikutnya
        }
        return Result.success()
    }
}

/** Menjadwalkan ulang alarm setelah perangkat reboot / aplikasi diperbarui. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.schedule(appCtx, MutqinStore(appCtx).loadSettings())
            } catch (e: Exception) {
                // abaikan
            } finally {
                pending.finish()
            }
        }
    }
}
