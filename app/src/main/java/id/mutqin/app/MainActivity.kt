@file:OptIn(ExperimentalMaterial3Api::class)

package id.mutqin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import id.mutqin.app.ui.ImportChoiceDialog
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import id.mutqin.app.ui.BackupDialog
import id.mutqin.app.ui.ExplorerScreen
import id.mutqin.app.ui.FlashcardScreen
import id.mutqin.app.ui.GradingDialog
import id.mutqin.app.ui.GuideScreen
import id.mutqin.app.ui.LocalPalette
import id.mutqin.app.ui.MutqinTheme
import id.mutqin.app.ui.THEME_OPTIONS
import id.mutqin.app.ui.NotificationSettingsDialog
import id.mutqin.app.ui.Pill
import id.mutqin.app.ui.ScopeManagerDialog
import id.mutqin.app.ui.StatsScreen

/**
 * Mutqin - Quran Murajaah Flashcard (Android native).
 * Metode saintifik Dr. Ali Rajabi: Active Recall + Spaced Repetition (Leitner 7 kotak).
 */
class MainActivity : ComponentActivity() {

    private val vm: MutqinViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Header & bilah navigasi selalu hijau tua -> ikon status bar selalu terang.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        QuranData.init(applicationContext)
        ReminderScheduler.ensureChannel(this)
        requestWritePermissionIfNeeded()

        setContent {
            MutqinTheme(themeKey = vm.themeKey) {
                MutqinApp(vm)
            }
        }
    }

    // Android 9 ke bawah: menulis ke folder Download butuh izin penyimpanan (Android 10+ tidak butuh).
    private val storagePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.scheduleLocalBackup(immediate = true)
        }

    private fun requestWritePermissionIfNeeded() {
        val needed = LocalBackup.permissionsToRequest(this)
        if (needed.isNotEmpty()) storagePermissions.launch(needed.toTypedArray())
    }
}

private data class TabSpec(val tab: MainTab, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabSpec(MainTab.FLASHCARD, "Flashcard", Icons.AutoMirrored.Filled.MenuBook),
    TabSpec(MainTab.EXPLORER, "604 Halaman", Icons.Filled.Layers),
    TabSpec(MainTab.STATS, "Statistik", Icons.Filled.BarChart),
    TabSpec(MainTab.GUIDE, "Metode", Icons.Filled.AutoAwesome)
)

@Composable
fun MutqinApp(vm: MutqinViewModel) {
    val p = LocalPalette.current
    var showScope by rememberSaveable { mutableStateOf(false) }
    var showNotif by rememberSaveable { mutableStateOf(false) }
    var showBackup by rememberSaveable { mutableStateOf(false) }
    // Menyimpan state tiap menu (filter, pencarian, posisi scroll) saat berpindah menu
    val tabStateHolder = rememberSaveableStateHolder()

    // Hitung ulang statistik (jatuh tempo, streak) setiap aplikasi kembali dibuka.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    // Aplikasi ditinggalkan (home, pindah aplikasi, layar mati): pastikan cadangan JSON terbaru sudah tertulis di Download.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flushLocalBackup() }

    BackHandler(enabled = vm.tab == MainTab.FLASHCARD && vm.cardSide == CardSide.BACK) { vm.backToFront() }
    BackHandler(enabled = vm.tab != MainTab.FLASHCARD) { vm.tab = MainTab.FLASHCARD }

    Scaffold(
        containerColor = p.background,
        topBar = {
            MutqinTopBar(
                streakDays = vm.stats.streakDays,
                notifEnabled = vm.settings.notifications?.enabled == true,
                themeKey = vm.themeKey,
                onTitleClick = { vm.tab = MainTab.FLASHCARD; vm.backToFront() },
                onScope = { showScope = true },
                onNotif = { showNotif = true },
                onSync = { showBackup = true },
                onTheme = vm::setTheme
            )
        },
        bottomBar = {
            NavigationBar(containerColor = p.headerBg, tonalElevation = 0.dp) {
                TABS.forEach { spec ->
                    NavigationBarItem(
                        selected = vm.tab == spec.tab,
                        onClick = {
                            vm.tab = spec.tab
                            if (spec.tab == MainTab.STATS) vm.refresh()
                        },
                        icon = { Icon(spec.icon, contentDescription = spec.label) },
                        label = { Text(spec.label, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF020617),
                            selectedTextColor = p.gold,
                            indicatorColor = p.gold,
                            unselectedIconColor = Color(0xFFA7F3D0),
                            unselectedTextColor = Color(0xFFA7F3D0)
                        )
                    )
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            if (!vm.isReady) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = p.emerald)
                }
            } else {
                tabStateHolder.SaveableStateProvider(vm.tab.name) {
                    when (vm.tab) {
                        MainTab.FLASHCARD -> FlashcardScreen(vm)
                        MainTab.EXPLORER -> ExplorerScreen(vm)
                        MainTab.STATS -> StatsScreen(vm, onOpenBackup = { showBackup = true })
                        MainTab.GUIDE -> GuideScreen(onStart = { vm.tab = MainTab.FLASHCARD; vm.backToFront() })
                    }
                }
            }
        }
    }

    // ---- Dialog
    vm.grading?.let { g ->
        GradingDialog(
            ui = g,
            meta = QuranData.pageMeta(g.pageNumber),
            onReview = vm::dismissGrading,
            onNext = vm::nextAfterReview
        )
    }
    if (showScope) {
        ScopeManagerDialog(
            settings = vm.settings,
            onSave = { mode, pages -> vm.saveScope(mode, pages) },
            onClose = { showScope = false }
        )
    }
    if (showNotif) NotificationSettingsDialog(vm = vm, onClose = { showNotif = false })
    if (showBackup) BackupDialog(vm = vm, onClose = { showBackup = false })
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickBackup = rememberLauncherForActivityResult(OpenBackupDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }.getOrNull()
            }
            val r = if (text == null) Result.failure(IllegalStateException("Gagal membaca file.")) else vm.importFirstRun(text)
            r.fold(
                onSuccess = { Toast.makeText(ctx, "Data dipulihkan: $it halaman. Kartu mengikuti antrean dari file.", Toast.LENGTH_LONG).show() },
                onFailure = { Toast.makeText(ctx, it.message ?: "File tidak sesuai.", Toast.LENGTH_LONG).show() }
            )
        }
    }
    when (vm.firstRunStep) {
        FirstRunStep.NONE -> Unit
        FirstRunStep.CHOOSE -> FirstRunDialog(
            onImport = { pickBackup.launch(arrayOf("application/json", "text/plain", "*/*")) },
            onFresh = { vm.finishFreshStart() }
        )
    }
    vm.pendingImport?.let { pending ->
        ImportChoiceDialog(
            pending = pending,
            onChoose = { replace ->
                scope.launch {
                    vm.confirmImport(replace).fold(
                        onSuccess = { Toast.makeText(ctx, "Impor selesai: $it halaman diperbarui. Kartu mengikuti antrean dari file.", Toast.LENGTH_LONG).show() },
                        onFailure = { Toast.makeText(ctx, it.message ?: "Impor gagal.", Toast.LENGTH_LONG).show() }
                    )
                }
            },
            onCancel = { vm.cancelImport() }
        )
    }
}

@Composable
private fun MutqinTopBar(
    streakDays: Int,
    notifEnabled: Boolean,
    themeKey: String,
    onTitleClick: () -> Unit,
    onScope: () -> Unit,
    onNotif: () -> Unit,
    onSync: () -> Unit,
    onTheme: (String) -> Unit
) {
    val p = LocalPalette.current
    var themeMenu by remember { mutableStateOf(false) }
    Surface(color = p.headerBg, shadowElevation = 4.dp) {
        Row(
            Modifier
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onTitleClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFFCD34D)))),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(p.headerBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
                    }
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("MUTQIN", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Pill("AI SRS", Color(0xFFFCD34D), textSize = 9)
                    }
                    Text("🔥 $streakDays hari beruntun", color = Color(0xFF6EE7B7), fontSize = 11.sp)
                }
            }
            IconButton(onClick = onScope) { Icon(Icons.Filled.Bookmark, contentDescription = "Target Hafalan", tint = Color(0xFFFCD34D)) }
            IconButton(onClick = onNotif) {
                BadgedBox(badge = { if (notifEnabled) Badge(containerColor = Color(0xFF34D399)) }) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Pengingat", tint = Color(0xFFFCD34D))
                }
            }
            IconButton(onClick = onSync) { Icon(Icons.Filled.Sync, contentDescription = "Cadangan & Sinkronisasi", tint = Color(0xFFA7F3D0)) }
            Box {
                IconButton(onClick = { themeMenu = true }) {
                    Icon(Icons.Filled.Palette, contentDescription = "Ganti tema", tint = Color(0xFFFCD34D))
                }
                DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                    THEME_OPTIONS.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(if (key == themeKey) "$label  ✓" else label) },
                            onClick = {
                                onTheme(key)
                                themeMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}
