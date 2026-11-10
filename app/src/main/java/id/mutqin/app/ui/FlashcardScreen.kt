@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package id.mutqin.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import id.mutqin.app.BasmalahLine
import id.mutqin.app.LineItem
import id.mutqin.app.MushafLayout
import id.mutqin.app.PageLine
import id.mutqin.app.SurahBannerLine
import id.mutqin.app.TextLine
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.mutqin.app.CardSide
import id.mutqin.app.ErrorLabel
import id.mutqin.app.GradingUi
import id.mutqin.app.LabelCounts
import id.mutqin.app.Leitner
import id.mutqin.app.MutqinViewModel
import id.mutqin.app.PreviousLabels
import id.mutqin.app.Time
import id.mutqin.app.QuranAyah
import id.mutqin.app.QuranData
import id.mutqin.app.QuranPageMeta
import id.mutqin.app.QuranRepository
import id.mutqin.app.QuranWord
import id.mutqin.app.countLabels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Warna tetap kartu depan (gelap zamrud, sama seperti versi web di kedua tema).
private val FrontCardTop = Color(0xFF064E3B)
private val FrontCardMid = Color(0xFF0F172A)
private val FrontCardBottom = Color(0xFF022C22)
private val FrontMuted = Color(0xFF6EE7B7)

// ===========================================================================
// Pemutar potongan audio (3,5 detik pertama ayat pembuka halaman) - dibundel di APK
// ===========================================================================

@Stable
class HintPlayer(private val appContext: Context) {
    var isPlaying by mutableStateOf(false)
        private set
    var hasError by mutableStateOf(false)
        private set

    private var player: MediaPlayer? = null

    /** Memutar klip halaman [page] dari assets (lokal, tanpa jeda); ketuk lagi untuk menghentikan. */
    fun toggle(page: Int) {
        if (isPlaying) {
            stop()
            return
        }
        hasError = false
        val afd = QuranRepository.openHintAudio(appContext, page)
        if (afd == null) {
            hasError = true
            return
        }
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            afd.use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ ->
                hasError = true
                stop()
                true
            }
            mp.prepare() // berkas lokal ~20 KB: siap seketika
            isPlaying = true
            mp.start()
        } catch (e: Exception) {
            hasError = true
            stop()
        }
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        isPlaying = false
    }
}

@Composable
fun rememberHintPlayer(): HintPlayer {
    val context = LocalContext.current.applicationContext
    val hp = remember { HintPlayer(context) }
    DisposableEffect(Unit) { onDispose { hp.stop() } }
    return hp
}

// ===========================================================================
// Layar flashcard: depan (memory trigger) <-> belakang (halaman penuh + pelabelan)
// ===========================================================================

@Composable
fun FlashcardScreen(vm: MutqinViewModel) {
    val meta = remember(vm.currentPage) { QuranData.pageMeta(vm.currentPage) }
    val box = vm.progress[vm.currentPage]?.box ?: 1
    val mainPage = remember(vm.progress, vm.settings.activeScopePages, vm.settings.scopeQueueOrder, vm.currentPage) {
        vm.mainCardPage()
    }
    val showBackBar = vm.isManualPick && vm.currentPage != mainPage
    var confirmBack by remember { mutableStateOf(false) }
    val hasUnsavedLabels = vm.cardSide == CardSide.BACK && vm.labels.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        if (showBackBar) {
            ManualPickBar(
                currentPage = vm.currentPage,
                mainPage = mainPage,
                onBack = { if (hasUnsavedLabels) confirmBack = true else vm.returnToMainCard() }
            )
        } else {
            val reason = remember(vm.progress, vm.settings.activeScopePages, vm.settings.scopeQueueOrder, vm.currentPage) {
                vm.queueReason()
            }
            if (reason != null) {
                Text(
                    "Kartu antrean: $reason",
                    color = LocalPalette.current.textMuted, fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }
        }
        Box(Modifier.weight(1f)) {
            Crossfade(targetState = vm.cardSide, label = "card-side") { side ->
                when (side) {
                    CardSide.FRONT -> FlashcardFront(
                        meta = meta,
                        box = box,
                        onFlip = vm::flipToBack
                    )
                    CardSide.BACK -> FlashcardBack(vm = vm, meta = meta)
                }
            }
        }
    }

    if (confirmBack) {
        AlertDialog(
            onDismissRequest = { confirmBack = false },
            title = { Text("Kembali ke flashcard utama?") },
            text = { Text("Label yang sudah kamu beri pada halaman ${vm.currentPage} belum disimpan dan akan hilang.") },
            confirmButton = {
                TextButton(onClick = { confirmBack = false; vm.returnToMainCard() }) { Text("Ya, kembali") }
            },
            dismissButton = { TextButton(onClick = { confirmBack = false }) { Text("Batal") } }
        )
    }
}

/** Muncul saat halaman dibuka manual (dari daftar halaman/statistik): jalan pintas kembali ke kartu antrean. */
@Composable
private fun ManualPickBar(currentPage: Int, mainPage: Int, onBack: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.surfaceAlt)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Kamu membuka halaman $currentPage secara manual, di luar antrean murajaah.",
            color = p.textMuted, fontSize = 12.sp
        )
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("Kembali ke flashcard utama (halaman $mainPage)", fontSize = 12.sp)
        }
    }
}

private fun formatTime(secs: Int): String = "${secs / 60}:${(secs % 60).toString().padStart(2, '0')}"

@Composable
fun FlashcardFront(
    meta: QuranPageMeta,
    box: Int,
    onFlip: (Int) -> Unit
) {
    val p = LocalPalette.current
    var elapsed by remember(meta.pageNumber) { mutableIntStateOf(0) }
    LaunchedEffect(meta.pageNumber) {
        elapsed = 0
        while (true) {
            delay(1000)
            elapsed++
        }
    }

    val hint = rememberHintPlayer()
    DisposableEffect(meta.pageNumber) { onDispose { hint.stop() } }

    val (startSurah, startAyah) = remember(meta.pageNumber) { QuranData.startAyah(meta.pageNumber) }
    val startSurahName = QuranData.surahByNumber(startSurah)?.nameLatin ?: meta.surahNameLatin

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(FrontCardTop, FrontCardMid, FrontCardBottom)))
                .border(2.dp, p.gold.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            // Lencana: halaman, juz, lembar, box
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Pill("Halaman ${meta.pageNumber}", p.gold, textSize = 13)
                Pill("Juz ${meta.juz}", p.emerald, textSize = 12)
                Pill("Juz ${meta.juz} Lembar ${meta.lembar} ${meta.side.id}", p.goldSoft, textSize = 12)
                Pill("Box $box", FrontMuted, textSize = 12)
            }
            Spacer(Modifier.height(8.dp))
            Text(meta.positionLabelLong, color = FrontMuted.copy(alpha = 0.9f), fontSize = 11.sp)

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meta.surahNameLatin, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.size(6.dp))
                Text("(${meta.surahNameArabic})", color = Color(0xFF94A3B8), fontSize = 13.sp)
            }
            if (meta.ayahRange != meta.surahNameLatin) {
                Text(meta.ayahRange, color = Color(0xFF94A3B8), fontSize = 12.sp)
            }

            // Memory trigger
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(FrontCardBottom.copy(alpha = 0.6f))
                    .border(1.dp, FrontCardTop, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "MEMORY TRIGGER (POTONGAN AWAL HALAMAN)",
                    color = p.gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        meta.memoryTriggerArabic,
                        fontFamily = MutqinFonts.trigger,
                        fontSize = 32.sp,
                        lineHeight = 52.sp,
                        color = Color(0xFFFEF3C7),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "\"Ingat peta visual dan bacakan seluruh halaman ini dari awal sampai akhir sebelum membalik kartu.\"",
                    color = FrontMuted.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))
                val playing = hint.isPlaying
                Button(
                    onClick = { hint.toggle(meta.pageNumber) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (playing) p.gold else FrontCardTop,
                        contentColor = if (playing) Color(0xFF020617) else FrontMuted
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        if (playing) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (playing) "Memutar potongan awal… (ketuk untuk berhenti)"
                        else "Dengar Potongan Awal (3,5 dtk) • $startSurahName:$startAyah",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                if (hint.hasError) {
                    Spacer(Modifier.height(6.dp))
                    Text("Audio halaman ini tidak ditemukan di dalam aplikasi.", color = p.gold.copy(alpha = 0.85f), fontSize = 11.sp)
                }
            }

            // Timer + tombol balik kartu
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(FrontCardBottom.copy(alpha = 0.8f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Waktu Mengingat: ", color = FrontMuted, fontSize = 12.sp)
                    Text(formatTime(elapsed), color = p.goldSoft, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    hint.stop()
                    onFlip(elapsed)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.gold, contentColor = Color(0xFF020617))
            ) {
                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Buka Halaman (Periksa Hafalan)", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Tekan tombol di atas untuk melihat halaman utuh dan memberi label kata/ayat.",
            color = p.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center
        )
    }
}

// ===========================================================================
// Sisi belakang: halaman penuh, alat tandai, penilaian
// ===========================================================================

private data class RatingOption(val value: Int, val label: String, val desc: String, val color: Color)

private val RATING_OPTIONS = listOf(
    RatingOption(1, "Perlu Diulang", "Banyak lupa/tersendat berat", Color(0xFFDC2626)),
    RatingOption(2, "Kurang Lancar", "Sering tersendat", Color(0xFFEA580C)),
    RatingOption(3, "Cukup", "Ada ragu di beberapa titik", Color(0xFFD97706)),
    RatingOption(4, "Lancar", "Hanya jeda minor", Color(0xFF0D9488)),
    RatingOption(5, "Sangat Lancar", "Mutqin sempurna tanpa ragu", Color(0xFF16A34A))
)

private data class ToolOption(val label: ErrorLabel, val name: String)

private val TOOL_OPTIONS = listOf(
    ToolOption(ErrorLabel.MISTAKE, "Salah / Lupa"),
    ToolOption(ErrorLabel.HESITATION, "Ragu / Tersendat"),
    ToolOption(ErrorLabel.SKIPPED, "Terlewat"),
    ToolOption(ErrorLabel.TAJWEED, "Harakat / Tajweed"),
    ToolOption(ErrorLabel.NONE, "Hapus Label")
)

@Composable
fun FlashcardBack(vm: MutqinViewModel, meta: QuranPageMeta) {
    val p = LocalPalette.current
    var activeTool by rememberSaveable { mutableStateOf(ErrorLabel.MISTAKE) }
    var rating by remember(meta.pageNumber) { mutableStateOf<Int?>(null) }
    var menuAyah by remember(meta.pageNumber) { mutableStateOf<Int?>(null) }

    val text = vm.pageText
    val counts: LabelCounts = remember(text, vm.labels) {
        if (text == null) LabelCounts() else countLabels(text.ayahs, vm.labels)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = vm::backToFront, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Trigger", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Hal ${meta.pageNumber}", p.gold)
                Pill("Juz ${meta.juz} L${meta.lembar} ${meta.side.id}", p.emerald)
            }
        }

        // Alat tandai
        MCard(accent = p.emerald) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TOOL_OPTIONS.forEach { opt ->
                    val c = opt.label.color(p)
                    val selected = activeTool == opt.label
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) c else c.copy(alpha = 0.16f))
                            .border(1.dp, c.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .clickable { activeTool = opt.label }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            opt.name,
                            color = if (selected) Color.White else c,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${counts.mistake} 🔴", color = p.danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${counts.hesitation} 🟡", color = p.warning, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${counts.skipped} 🟣", color = p.purple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${counts.tajweed} 🔵", color = p.blue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                if (counts.total > 0) {
                    TextButton(onClick = vm::clearAllLabels) { Text("Reset Semua", fontSize = 12.sp) }
                }
            }
        }

        // Halaman mushaf
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(p.mushafPaper)
                .border(3.dp, p.gold.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${meta.surahNameLatin} (${meta.surahNameArabic})", color = p.goldSoft, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Juz ${meta.juz} • Hal ${meta.pageNumber}", color = p.goldSoft, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "💡 Ketuk kata di halaman untuk menandai dengan alat aktif. Ketuk nomor ayat untuk menandai seluruh ayat; tahan lama untuk pilihan lain.",
                color = p.textMuted, fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            if (vm.previousLabels.isNotEmpty() && !vm.isLoadingText) {
                PreviousLabelsBanner(
                    labels = vm.previousLabels,
                    reviewedAt = vm.previousReviewAt,
                    visible = vm.showPreviousLabels,
                    onToggle = vm::togglePreviousLabels
                )
                Spacer(Modifier.height(10.dp))
            }

            when {
                vm.isLoadingText -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = p.emerald)
                    }
                }
                text == null -> {
                    Text(
                        "Data teks halaman ini tidak ditemukan di dalam aplikasi. " +
                            "Pasang ulang APK versi lengkap (workflow Generate Assets harus dijalankan sebelum build).",
                        color = p.danger, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    )
                }
                vm.pageImage != null -> {
                    // Gambar halaman utuh dari sumber mushaf cetak + kotak transparan per kata dan per nomor ayat
                    PageImageView(
                        image = vm.pageImage!!,
                        text = text,
                        labelOf = { vm.labels[it.id] ?: ErrorLabel.NONE },
                        previousOf = { if (vm.showPreviousLabels) vm.previousLabels[it.id] ?: ErrorLabel.NONE else ErrorLabel.NONE },
                        onWordClick = { vm.toggleWordLabel(it.id, activeTool) },
                        menuAyah = menuAyah,
                        onAyahTap = { vm.toggleAyahLabel(it, activeTool); menuAyah = null },
                        onAyahLongPress = { menuAyah = it.ayahNumber },
                        onDismissMenu = { menuAyah = null },
                        onSetAyahLabel = { ayah, label -> vm.setAyahLabel(ayah, label); menuAyah = null }
                    )
                }
                // Cadangan: gambar halaman belum ada di APK -> tata letak teks buatan
                else -> {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        val mushafLayout = remember(text) { MushafLayout.build(text.ayahs) }
                        if (mushafLayout != null) {
                            MushafPageView(
                                layout = mushafLayout,
                                labelOf = { vm.labels[it.id] ?: ErrorLabel.NONE },
                                previousOf = { if (vm.showPreviousLabels) vm.previousLabels[it.id] ?: ErrorLabel.NONE else ErrorLabel.NONE },
                                onWordClick = { vm.toggleWordLabel(it.id, activeTool) },
                                menuAyah = menuAyah,
                                onAyahTap = { vm.toggleAyahLabel(it, activeTool); menuAyah = null },
                                onAyahLongPress = { menuAyah = it.ayahNumber },
                                onDismissMenu = { menuAyah = null },
                                onSetAyahLabel = { ayah, label -> vm.setAyahLabel(ayah, label); menuAyah = null }
                            )
                        } else {
                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                text.ayahs.forEach { ayah ->
                                    ayah.words.forEach { word ->
                                        WordChip(
                                            word = word,
                                            label = vm.labels[word.id] ?: ErrorLabel.NONE,
                                            previousLabel = if (vm.showPreviousLabels) vm.previousLabels[word.id] ?: ErrorLabel.NONE else ErrorLabel.NONE,
                                            onClick = { vm.toggleWordLabel(word.id, activeTool) }
                                        )
                                    }
                                    AyahBadge(
                                        ayah = ayah,
                                        menuOpen = menuAyah == ayah.ayahNumber,
                                        onTap = {
                                            vm.toggleAyahLabel(ayah, activeTool)
                                            menuAyah = null
                                        },
                                        onLongPress = { menuAyah = ayah.ayahNumber },
                                        onDismissMenu = { menuAyah = null },
                                        onSetLabel = { label ->
                                            vm.setAyahLabel(ayah, label)
                                            menuAyah = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(meta.positionLabel, color = p.goldSoft, fontSize = 11.sp)
                Text("${meta.pageNumber}", color = p.goldSoft, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(meta.juzLembarLabel, color = p.goldSoft, fontSize = 11.sp)
            }
        }

        // Penilaian
        MCard(accent = p.emerald) {
            Text("Penilaian Pribadi & Spaced Repetition (Dr. Ali Rajabi)", color = p.emerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Pilih kelancaran hafalan Anda secara keseluruhan. Skor digabung dengan bobot kesalahan " +
                    "(${counts.mistake} salah, ${counts.hesitation} ragu, ${counts.skipped} terlewat) untuk menentukan " +
                    "skor retensi dan jadwal murajaah berikutnya.",
                color = p.textMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))

            RATING_OPTIONS.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { opt ->
                        val selected = rating == opt.value
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) opt.color else p.surfaceAlt)
                                .border(1.dp, if (selected) p.goldSoft else p.border, RoundedCornerShape(16.dp))
                                .clickable { rating = opt.value }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("${opt.value}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = if (selected) Color.White else p.textPrimary)
                            Text(opt.label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selected) Color.White else p.textPrimary)
                            Text(
                                opt.desc, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2,
                                color = if (selected) Color.White.copy(alpha = 0.85f) else p.textMuted
                            )
                        }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Text("Durasi mengingat: ${vm.elapsedSeconds} detik", color = p.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { rating?.let { vm.finishReview(it) } },
                enabled = rating != null && !vm.isLoadingText && vm.pageText != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.emerald, contentColor = Color(0xFF022C22))
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Hitung Skor & Jadwalkan SRS", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Satu kata pada halaman. Dibuat seringan mungkin karena satu halaman memuat ±100–250 kata:
 * tanpa clip (menghindari layer grafis per kata), tanpa ripple, dan latar/garis hanya digambar saat kata ditandai.
 */
@Composable
private fun WordChip(
    word: QuranWord,
    label: ErrorLabel,
    previousLabel: ErrorLabel,
    onClick: () -> Unit,
    fontSize: TextUnit = 28.sp,
    /** Skala tampilan mushaf (padding ikut mengecil agar lebar baris tetap linear terhadap ukuran huruf). */
    padScale: Float = 1f
) {
    val p = LocalPalette.current
    val currentOnClick by rememberUpdatedState(onClick)
    val marked = label != ErrorLabel.NONE
    val showPrevious = !marked && previousLabel != ErrorLabel.NONE
    val markModifier = if (marked) {
        val c = label.color(p)
        Modifier
            .background(c.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(c, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            }
    } else if (showPrevious) {
        // Label murajaah SEBELUMNYA: lebih pudar dan bergaris putus-putus agar tidak tertukar dengan label sesi ini
        val c = previousLabel.color(p)
        Modifier
            .background(c.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    c.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))
                )
            }
    } else {
        Modifier
    }
    Text(
        text = word.text,
        fontFamily = MutqinFonts.quran,
        fontSize = fontSize,
        color = p.mushafInk,
        fontWeight = if (label == ErrorLabel.MISTAKE) FontWeight.Bold else FontWeight.Normal,
        textDecoration = if (label == ErrorLabel.SKIPPED) TextDecoration.LineThrough else null,
        modifier = markModifier
            .pointerInput(Unit) { detectTapGestures(onTap = { currentOnClick() }) }
            .padding(horizontal = (3 * padScale).dp, vertical = (2 * padScale).dp),
        softWrap = false,
        maxLines = 1
    )
}

/** Penjelasan label murajaah sebelumnya: tampil pudar & putus-putus; label baru menimpanya. */
@Composable
private fun PreviousLabelsBanner(
    labels: Map<String, ErrorLabel>,
    reviewedAt: String?,
    visible: Boolean,
    onToggle: () -> Unit
) {
    val p = LocalPalette.current
    val counts = remember(labels) { PreviousLabels.counts(labels) }
    val dateText = remember(reviewedAt) {
        reviewedAt?.let {
            runCatching {
                Time.parse(it).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale("id", "ID")))
            }.getOrNull()
        }
    }
    val summary = listOf(
        ErrorLabel.MISTAKE to "salah",
        ErrorLabel.HESITATION to "ragu",
        ErrorLabel.SKIPPED to "terlewat",
        ErrorLabel.TAJWEED to "tajwid"
    ).filter { (counts[it.first] ?: 0) > 0 }.joinToString(" • ") { "${counts[it.first]} ${it.second}" }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.surfaceAlt)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Label murajaah sebelumnya" + (dateText?.let { " ($it)" } ?: ""),
            color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp
        )
        Text(summary, color = p.textMuted, fontSize = 11.sp)
        Text(
            "Ditampilkan PUDAR dengan garis putus-putus. Jadikan bahan evaluasi, tetapi nilai hafalanmu kali ini apa adanya: " +
                "kata mana pun tetap bisa diberi label baru (warna tegas), dan label baru itulah yang dihitung.",
            color = p.textMuted, fontSize = 11.sp
        )
        TextButton(onClick = onToggle, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
            Text(if (visible) "Sembunyikan label sebelumnya" else "Tampilkan label sebelumnya", fontSize = 11.sp)
        }
    }
}

/**
 * Halaman seperti cetakan mushaf Madinah: tiap baris berisi kata-kata persis seperti di mushaf dan diratakan lebar penuh;
 * ukuran huruf disesuaikan agar baris terpanjang muat di layar.
 */
@Composable
private fun MushafPageView(
    layout: List<PageLine>,
    labelOf: (QuranWord) -> ErrorLabel,
    previousOf: (QuranWord) -> ErrorLabel,
    onWordClick: (QuranWord) -> Unit,
    menuAyah: Int?,
    onAyahTap: (QuranAyah) -> Unit,
    onAyahLongPress: (QuranAyah) -> Unit,
    onDismissMenu: () -> Unit,
    onSetAyahLabel: (QuranAyah, ErrorLabel) -> Unit
) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availablePx = with(density) { maxWidth.toPx() }
        val baseSp = 28f
        val metrics = remember(layout, availablePx) {
            val chipPad = with(density) { 6.dp.toPx() }
            val badgePx = with(density) { 42.dp.toPx() }
            val naturals = layout.map { line ->
                if (line is TextLine) {
                    line.items.sumOf { item ->
                        when (item) {
                            is LineItem.Word -> (measurer.measure(
                                AnnotatedString(item.word.text),
                                TextStyle(fontFamily = MutqinFonts.quran, fontSize = baseSp.sp)
                            ).size.width + chipPad).toDouble()
                            is LineItem.AyahEnd -> badgePx.toDouble()
                        }
                    }.toFloat()
                } else 0f
            }
            val widest = naturals.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            Pair(minOf(1f, availablePx * 0.95f / widest), naturals)
        }
        val scale = metrics.first
        val fontSize = (baseSp * scale).coerceAtLeast(12f).sp

        Column(verticalArrangement = Arrangement.spacedBy((6 * scale).coerceAtLeast(3f).dp)) {
            layout.forEachIndexed { index, line ->
                when (line) {
                    is SurahBannerLine -> {
                        val name = QuranData.surahs.firstOrNull { it.number == line.surahNumber }?.nameArabic.orEmpty()
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, p.gold.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                .background(p.gold.copy(alpha = 0.10f))
                                .padding(vertical = (6 * scale).coerceAtLeast(3f).dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "سورة $name",
                                fontFamily = MutqinFonts.quran, fontSize = (24 * scale).coerceAtLeast(14f).sp,
                                color = p.goldSoft, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    is BasmalahLine -> Text(
                        "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
                        fontFamily = MutqinFonts.quran, fontSize = fontSize, color = p.mushafInk,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), softWrap = false, maxLines = 1
                    )
                    is TextLine -> {
                        val justify = metrics.second[index] * scale >= availablePx * 0.75f
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (justify) Arrangement.SpaceBetween else Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            line.items.forEach { item ->
                                when (item) {
                                    is LineItem.Word -> WordChip(
                                        word = item.word,
                                        label = labelOf(item.word),
                                        previousLabel = previousOf(item.word),
                                        onClick = { onWordClick(item.word) },
                                        fontSize = fontSize,
                                        padScale = scale
                                    )
                                    is LineItem.AyahEnd -> AyahBadge(
                                        ayah = item.ayah,
                                        menuOpen = menuAyah == item.ayah.ayahNumber,
                                        onTap = { onAyahTap(item.ayah) },
                                        onLongPress = { onAyahLongPress(item.ayah) },
                                        onDismissMenu = onDismissMenu,
                                        onSetLabel = { onSetAyahLabel(item.ayah, it) },
                                        scale = scale
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AyahBadge(
    ayah: QuranAyah,
    menuOpen: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onSetLabel: (ErrorLabel) -> Unit,
    scale: Float = 1f
) {
    val p = LocalPalette.current
    Box(Modifier.padding(horizontal = (4 * scale).dp, vertical = (8 * scale).dp)) {
        Box(
            Modifier
                .size((34 * scale).coerceAtLeast(22f).dp)
                .clip(CircleShape)
                .background(p.gold.copy(alpha = 0.18f))
                .border(2.dp, p.gold.copy(alpha = 0.85f), CircleShape)
                .combinedClickable(onClick = onTap, onLongClick = onLongPress),
            contentAlignment = Alignment.Center
        ) {
            Text("${ayah.ayahNumber}", color = p.goldSoft, fontWeight = FontWeight.Bold, fontSize = (12 * scale).coerceAtLeast(9f).sp)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                DropdownMenuItem(text = { Text("Ayat ${ayah.ayahNumber}", fontWeight = FontWeight.Bold) }, onClick = onDismissMenu)
                DropdownMenuItem(text = { Text("🔴 Tandai Semua Salah") }, onClick = { onSetLabel(ErrorLabel.MISTAKE) })
                DropdownMenuItem(text = { Text("🟡 Tandai Semua Ragu") }, onClick = { onSetLabel(ErrorLabel.HESITATION) })
                DropdownMenuItem(text = { Text("🟣 Tandai Terlewat") }, onClick = { onSetLabel(ErrorLabel.SKIPPED) })
                DropdownMenuItem(text = { Text("🔵 Tandai Harakat/Tajweed") }, onClick = { onSetLabel(ErrorLabel.TAJWEED) })
                DropdownMenuItem(text = { Text("⚪ Bersihkan Ayat Ini") }, onClick = { onSetLabel(ErrorLabel.NONE) })
            }
        }
    }
}

// ===========================================================================
// Dialog hasil penilaian + confetti
// ===========================================================================

private fun scoreColor(score: Int, p: MutqinPalette): Color = when {
    score >= 88 -> p.emerald
    score >= 72 -> p.teal
    score >= 52 -> p.warning
    score >= 35 -> Color(0xFFF97316)
    else -> p.danger
}

@Composable
fun GradingDialog(ui: GradingUi, meta: QuranPageMeta, onReview: () -> Unit, onNext: () -> Unit) {
    val p = LocalPalette.current
    val r = ui.result
    Dialog(onDismissRequest = onReview, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(16.dp).fillMaxWidth().widthIn(max = 560.dp),
            shape = RoundedCornerShape(28.dp),
            color = p.surface,
            border = androidx.compose.foundation.BorderStroke(2.dp, p.emerald.copy(alpha = 0.5f))
        ) {
            Box {
                Column(
                    Modifier
                        .heightIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Pill("HASIL PENILAIAN & JADWAL MURAJAAH", p.gold, textSize = 10)
                    Spacer(Modifier.height(8.dp))
                    Text("Halaman ${meta.pageNumber} (${meta.surahNameLatin})", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, textAlign = TextAlign.Center)
                    Text(meta.juzLembarLabel, color = p.textMuted, fontSize = 12.sp)

                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(p.background)
                            .border(1.dp, p.border, RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val sc = scoreColor(r.compositeScore, p)
                        Column(
                            Modifier
                                .size(78.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(sc.copy(alpha = 0.15f))
                                .border(2.dp, sc.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("${r.compositeScore}", color = sc, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                            Text("/ 100", color = p.textMuted, fontSize = 10.sp)
                        }
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Predikat Kelancaran:", color = p.textMuted, fontSize = 11.sp)
                            Text(r.aiGrade, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                "Penilaian Anda: ${ui.userRating}/5 • ${ui.counts.mistake} salah • ${ui.counts.hesitation} ragu • ${ui.counts.skipped} lewat",
                                color = p.textMuted, fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoTile("Leitner Box Berikutnya", "Box ${r.recommendedBox}", Modifier.weight(1f))
                        InfoTile("Jadwal Murajaah", r.intervalLabel, Modifier.weight(1f))
                    }
                    Text(
                        Leitner.BOX_DESCRIPTIONS[r.recommendedBox].orEmpty(),
                        color = p.textMuted, fontSize = 11.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.emerald, contentColor = Color(0xFF022C22))
                    ) { Text("Simpan & Lanjut Halaman Berikutnya", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = onReview) { Text("Tinjau Ulang Halaman") }
                }
                if (r.compositeScore >= 75) ConfettiBurst(Modifier.matchParentSize())
            }
        }
    }
}

@Composable
private fun InfoTile(title: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(p.surfaceAlt)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = p.textMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
        Text(value, color = p.goldSoft, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

private class Particle(
    val angle: Float, val speed: Float, val size: Float, val color: Color, val spin: Float
)

@Composable
fun ConfettiBurst(modifier: Modifier = Modifier) {
    val colors = remember {
        listOf(Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFFCD34D), Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFF472B6))
    }
    val particles = remember {
        val rnd = Random(System.nanoTime())
        List(55) {
            Particle(
                angle = Math.toRadians((-150 + rnd.nextInt(120)).toDouble()).toFloat(),
                speed = 420f + rnd.nextInt(520),
                size = 6f + rnd.nextInt(7),
                color = colors[rnd.nextInt(colors.size)],
                spin = rnd.nextFloat()
            )
        }
    }
    var t by remember { mutableFloatStateOf(0f) }
    val duration = 1.9f
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (t < duration) {
            val now = withFrameNanos { it }
            t = (now - start) / 1_000_000_000f
        }
    }
    Canvas(modifier) {
        if (t >= duration) return@Canvas
        val ox = size.width / 2f
        val oy = size.height * 0.30f
        val gravity = 1100f
        val fade = (1f - ((t - 1.2f) / (duration - 1.2f)).coerceIn(0f, 1f))
        particles.forEach { pt ->
            val x = ox + cos(pt.angle) * pt.speed * t
            val y = oy + sin(pt.angle) * pt.speed * t + 0.5f * gravity * t * t
            drawRect(
                color = pt.color.copy(alpha = fade),
                topLeft = Offset(x, y),
                size = Size(pt.size * (0.6f + 0.4f * sin(t * 12f * (0.5f + pt.spin))), pt.size)
            )
        }
    }
}
