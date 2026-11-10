@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package id.mutqin.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.KeyboardType
import id.mutqin.app.CardProgress
import id.mutqin.app.ChartPoint
import id.mutqin.app.ChartRange
import id.mutqin.app.Leitner
import id.mutqin.app.NextReviewLabel
import id.mutqin.app.MutqinViewModel
import id.mutqin.app.QuranData
import id.mutqin.app.QuranStructure
import id.mutqin.app.StatsEngine
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun StatsScreen(vm: MutqinViewModel, onOpenBackup: () -> Unit) {
    val p = LocalPalette.current
    val stats = vm.stats
    val progress = vm.progress
    val settings = vm.settings
    val zone = remember { ZoneId.systemDefault() }

    var editingTarget by remember { mutableStateOf(false) }
    var targetInput by remember(settings.dailyTargetPages) { mutableStateOf(settings.dailyTargetPages.toString()) }
    var range by rememberSaveable { mutableStateOf(ChartRange.WEEK) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var selectedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var juzExpanded by rememberSaveable { mutableStateOf(false) }
    var troubleExpanded by rememberSaveable { mutableStateOf(false) }
    var juzModal by remember { mutableStateOf<Int?>(null) }
    var boxModal by remember { mutableStateOf<Int?>(null) }
    var dueModal by remember { mutableStateOf(false) }

    val byDate = remember(progress) { StatsEngine.reviewsByDate(progress.values, zone) }
    val chart = remember(range, byDate) { StatsEngine.consistencyChart(range, byDate, LocalDate.now(zone)) }
    val juzStats = remember(progress) { StatsEngine.juzStats(progress) }
    val unreviewed = remember(progress) { StatsEngine.unreviewedCount(progress) }
    val completedJuz = juzStats.count { it.isComplete }
    val percentTarget = ((stats.reviewedTodayCount * 100.0) / settings.dailyTargetPages).roundToInt().coerceIn(0, 100)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Kartu ringkasan 2x2
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard("STREAK HAFALAN", p.gold, Modifier.weight(1f)) {
                        Text("🔥 ${stats.streakDays} Hari", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                        Text("Konsistensi murajaah harian", color = p.textMuted, fontSize = 11.sp)
                    }
                    SummaryCard("HALAMAN HARI INI", p.emerald, Modifier.weight(1f)) {
                        if (editingTarget) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = targetInput,
                                    onValueChange = { targetInput = it.filter(Char::isDigit).take(3) },
                                    modifier = Modifier.width(76.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                )
                                IconButton(onClick = {
                                    vm.updateDailyTarget(targetInput.toIntOrNull() ?: settings.dailyTargetPages)
                                    editingTarget = false
                                }) { Icon(Icons.Filled.Check, contentDescription = "Simpan target", tint = p.emerald) }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${stats.reviewedTodayCount} / ${settings.dailyTargetPages} hal",
                                    color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp
                                )
                                IconButton(onClick = { editingTarget = true }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Ubah target", tint = p.textMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        ProgressBar(percentTarget / 100f, p.emerald)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard("JATUH TEMPO (SRS)", p.teal, Modifier.weight(1f), onClick = { dueModal = true }) {
                        Text("${stats.dueTodayCount} hal", color = p.teal, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                        Text("Siap diulang hari ini • ketuk untuk daftar", color = p.textMuted, fontSize = 11.sp)
                    }
                    SummaryCard("SKOR RATA-RATA", p.blue, Modifier.weight(1f)) {
                        Text(
                            if (stats.averageScoreToday > 0) "${stats.averageScoreToday}%" else "—",
                            color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp
                        )
                        Text("Retensi murajaah hari ini", color = p.textMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        // ---- Grafik garis konsistensi
        item {
            MCard(Modifier.fillMaxWidth(), accent = p.emerald) {
                SectionHeader("Grafik Konsistensi Murajaah", "Tren naik-turun jumlah halaman per periode")
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChartRange.entries.forEach { r ->
                        val sel = r == range
                        Text(
                            r.label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (sel) Color.White else p.textMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) p.emerald else p.surfaceAlt)
                                .clickable { range = r }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                ConsistencyChart(points = chart, key = range)
            }
        }

        // ---- Kalender riwayat
        item {
            MCard(Modifier.fillMaxWidth(), accent = p.emerald) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Kalender Riwayat Murajaah", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Ketuk tanggal untuk melihat halaman & skor.", color = p.textMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Bulan sebelumnya") }
                    Text(
                        "${MONTH_NAMES[month.monthValue - 1]} ${month.year}",
                        color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Bulan berikutnya") }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab").forEach {
                        Text(it, Modifier.weight(1f), color = p.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(4.dp))
                val firstIndex = month.atDay(1).dayOfWeek.value % 7
                val cells: List<LocalDate?> = List(firstIndex) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0 until 7).forEach { i ->
                            val d = week.getOrNull(i)
                            if (d == null) {
                                Spacer(Modifier.weight(1f).height(46.dp))
                            } else {
                                val count = byDate[d]?.size ?: 0
                                val sel = d == selectedDate
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) p.gold.copy(alpha = 0.22f) else if (count > 0) p.emeraldDeep.copy(alpha = if (p.isDark) 0.6f else 1f) else p.surfaceAlt.copy(alpha = 0.5f))
                                        .border(1.dp, if (sel) p.gold else if (count > 0) p.emerald.copy(alpha = 0.6f) else p.border, RoundedCornerShape(10.dp))
                                        .clickable { selectedDate = d },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text("${d.dayOfMonth}", color = p.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    if (count > 0) Text("$count hal", color = p.emerald, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
                selectedDate?.let { d ->
                    val records = byDate[d].orEmpty()
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.background)
                            .border(1.dp, p.gold.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Text("Riwayat $d • ${records.size} halaman", color = p.goldSoft, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        if (records.isEmpty()) {
                            Text("Tidak ada catatan murajaah pada tanggal ini.", color = p.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        } else {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                records.sortedBy { it.timestamp }.forEach { r ->
                                    val meta = QuranData.pageMeta(r.pageNumber)
                                    Row(
                                        Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(p.surface)
                                            .border(1.dp, p.border, RoundedCornerShape(12.dp))
                                            .clickable { vm.selectPage(r.pageNumber, manual = true) }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${r.pageNumber}", color = p.emerald, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                        Spacer(Modifier.size(6.dp))
                                        Column {
                                            Text(meta.surahNameLatin, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Text("Juz ${meta.juz} • Skor: ${r.score ?: 0}%", color = p.textMuted, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Skor rata-rata per juz
        item {
            MCard(Modifier.fillMaxWidth(), accent = p.emerald) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Skor Rata-Rata Per Juz", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Skor muncul jika seluruh halaman juz sudah dimurajaah. Ketuk juz untuk detail.", color = p.textMuted, fontSize = 11.sp)
                    }
                    Pill("$completedJuz/30 Juz", p.emerald)
                }
                Spacer(Modifier.height(10.dp))
                (if (juzExpanded) juzStats else juzStats.take(6)).chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { j ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(p.background)
                                    .border(1.dp, if (j.isComplete) p.emerald.copy(alpha = 0.6f) else p.border, RoundedCornerShape(14.dp))
                                    .clickable { juzModal = j.juz }
                                    .padding(10.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Juz ${j.juz}", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (j.isComplete) Text("${j.averageScore}%", color = p.emerald, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    else Text("${j.reviewedCount}/${j.totalPages}", color = p.warning, fontSize = 11.sp)
                                }
                                Spacer(Modifier.height(6.dp))
                                ProgressBar(
                                    if (j.isComplete) (j.averageScore ?: 0) / 100f else j.progressPercent / 100f,
                                    if (j.isComplete) p.emerald else p.warning
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                TextButton(onClick = { juzExpanded = !juzExpanded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (juzExpanded) "Tampilkan Lebih Sedikit" else "Tampilkan Selengkapnya (30 Juz)")
                    Icon(if (juzExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                }
            }
        }

        // ---- Distribusi 7 kotak Leitner
        item {
            MCard(Modifier.fillMaxWidth(), accent = p.emerald) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Distribusi 7 Kotak Leitner", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Naik kotak saat lancar, kembali ke Kotak 1 jika banyak salah.", color = p.textMuted, fontSize = 11.sp)
                    }
                    Pill("${stats.totalCardsActive} hal", p.emerald)
                }
                Spacer(Modifier.height(10.dp))
                (1..7).toList().chunked(4).forEach { rowBoxes ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowBoxes.forEach { b ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(p.background)
                                    .border(1.dp, p.border, RoundedCornerShape(14.dp))
                                    .clickable { boxModal = b }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Box $b", color = p.goldSoft, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("${Leitner.intervalFor(b)} hari", color = p.textMuted, fontSize = 10.sp)
                                Text("${stats.boxDistribution[b] ?: 0}", color = p.textPrimary, fontWeight = FontWeight.Black, fontSize = 22.sp)
                            }
                        }
                        repeat(4 - rowBoxes.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Belum pernah dimurajaah: $unreviewed hal", color = p.textMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { boxModal = 0 }, shape = RoundedCornerShape(12.dp)) { Text("Lihat", fontSize = 12.sp) }
                }
            }
        }

        // ---- Halaman rawan lupa
        item {
            MCard(Modifier.fillMaxWidth(), accent = p.danger) {
                Text("Halaman Rawan Lupa", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Halaman dengan kesalahan terbanyak yang butuh active recall intensif.", color = p.textMuted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                if (stats.troublePages.isEmpty()) {
                    Text(
                        "Alhamdulillah! Belum ada halaman dengan catatan kesalahan tinggi.",
                        color = p.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                } else {
                    (if (troubleExpanded) stats.troublePages else stats.troublePages.take(4)).forEach { tp ->
                        val meta = QuranData.pageMeta(tp.pageNumber)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(p.background)
                                .border(1.dp, p.danger.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                .clickable { vm.selectPage(tp.pageNumber, manual = true) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Halaman ${tp.pageNumber}", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${meta.surahNameLatin} • Juz ${meta.juz}", color = p.textMuted, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${tp.totalMistakes} kesalahan", color = p.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Skor terakhir ${tp.lastScore}%", color = p.textMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    if (stats.troublePages.size > 4) {
                        TextButton(onClick = { troubleExpanded = !troubleExpanded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(if (troubleExpanded) "Tampilkan Lebih Sedikit" else "Tampilkan Selengkapnya")
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Cadangkan / Pulihkan Data")
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    juzModal?.let { j ->
        PageListDialog(
            title = "Juz $j",
            subtitle = "Statistik tiap halaman",
            pages = QuranStructure.pagesInJuz(j),
            progress = progress,
            showFilter = true,
            onPick = { juzModal = null; vm.selectPage(it, manual = true) },
            onClose = { juzModal = null }
        )
    }
    boxModal?.let { b ->
        val pages = (1..QuranStructure.TOTAL_PAGES).filter { page ->
            val prog = progress[page]
            if (b == 0) prog == null || prog.repetitions == 0 else prog != null && prog.repetitions > 0 && prog.box == b
        }
        PageListDialog(
            title = if (b == 0) "Belum Dimurajaah" else "Box $b",
            subtitle = if (b == 0) "Halaman yang belum pernah dimurajaah" else "Interval ulangan: setiap ${Leitner.intervalFor(b)} hari",
            pages = pages,
            progress = progress,
            showFilter = false,
            onPick = { boxModal = null; vm.selectPage(it, manual = true) },
            onClose = { boxModal = null }
        )
    }
    if (dueModal) {
        PageListDialog(
            title = "Halaman Jatuh Tempo",
            subtitle = "Urutan: utang dulu, lalu jatuh tempo hari ini",
            pages = vm.dueNowPages,
            progress = progress,
            showFilter = false,
            onPick = { dueModal = false; vm.selectPage(it, manual = true) },
            onClose = { dueModal = false }
        )
    }
}

private val MONTH_NAMES = arrayOf(
    "Januari", "Februari", "Maret", "April", "Mei", "Juni",
    "Juli", "Agustus", "September", "Oktober", "November", "Desember"
)

@Composable
private fun SummaryCard(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val p = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(p.surface)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(p.surfaceAlt)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(50)).background(color))
    }
}

// ===========================================================================
// Grafik garis (Canvas): kurva halus + area gradien, sentuh/geser untuk detail
// ===========================================================================

@Composable
private fun ConsistencyChart(points: List<ChartPoint>, key: Any) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    var selected by remember(key) { mutableIntStateOf(-1) }
    val maxVal = max(5, points.maxOfOrNull { it.count } ?: 0)
    val labelStyle = TextStyle(color = p.textMuted, fontSize = 9.sp)

    Column {
        val sel = points.getOrNull(selected)
        Text(
            if (sel != null) "${sel.count} halaman • ${sel.date}" else "Sentuh atau geser pada grafik untuk melihat detail",
            color = if (sel != null) p.goldSoft else p.textMuted,
            fontSize = 12.sp, fontWeight = if (sel != null) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.height(6.dp))

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .pointerInput(points) {
                    detectTapGestures { o ->
                        selected = nearestIndex(o.x, size.width.toFloat(), points.size, 30.dp.toPx(), 12.dp.toPx())
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> selected = nearestIndex(o.x, size.width.toFloat(), points.size, 30.dp.toPx(), 12.dp.toPx()) },
                        onHorizontalDrag = { change, _ ->
                            selected = nearestIndex(change.position.x, size.width.toFloat(), points.size, 30.dp.toPx(), 12.dp.toPx())
                        }
                    )
                }
        ) {
            val padL = 30.dp.toPx()
            val padR = 12.dp.toPx()
            val padT = 12.dp.toPx()
            val padB = 24.dp.toPx()
            val w = size.width
            val h = size.height
            val chartH = h - padT - padB
            val n = points.size

            fun xAt(i: Int): Float =
                if (n <= 1) (padL + (w - padR)) / 2f else padL + i.toFloat() / (n - 1) * (w - padL - padR)
            fun yAt(v: Int): Float = h - padB - v.toFloat() / maxVal * chartH

            // Garis grid + label sumbu Y
            for (pct in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val y = h - padB - pct * chartH
                drawLine(
                    p.border, Offset(padL, y), Offset(w - padR, y), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
                val t = measurer.measure((pct * maxVal).roundToInt().toString(), labelStyle)
                drawText(t, topLeft = Offset(padL - 6.dp.toPx() - t.size.width, y - t.size.height / 2f))
            }
            if (n == 0) return@Canvas

            // Kurva halus
            val line = Path()
            points.forEachIndexed { i, pt ->
                val x = xAt(i); val y = yAt(pt.count)
                if (i == 0) line.moveTo(x, y) else {
                    val px = xAt(i - 1); val py = yAt(points[i - 1].count)
                    val cx = (px + x) / 2f
                    line.cubicTo(cx, py, cx, y, x, y)
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(xAt(n - 1), h - padB)
                lineTo(xAt(0), h - padB)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(p.emerald.copy(alpha = 0.40f), p.emerald.copy(alpha = 0f)), startY = padT, endY = h - padB))
            drawPath(
                line,
                Brush.horizontalGradient(listOf(p.emerald, Color(0xFF34D399), p.gold), startX = padL, endX = w - padR),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Titik data
            points.forEachIndexed { i, pt ->
                val x = xAt(i); val y = yAt(pt.count)
                val isPeak = pt.count > 0 && pt.count == maxVal
                if (i == selected) {
                    drawLine(p.gold.copy(alpha = 0.5f), Offset(x, padT), Offset(x, h - padB), strokeWidth = 1.dp.toPx())
                    drawCircle(p.gold.copy(alpha = 0.3f), radius = 10.dp.toPx(), center = Offset(x, y))
                }
                val r = if (i == selected) 5.5.dp.toPx() else if (pt.count > 0) 4.dp.toPx() else 2.5.dp.toPx()
                drawCircle(p.surface, radius = r + 2.dp.toPx(), center = Offset(x, y))
                drawCircle(if (isPeak) p.gold else if (pt.count > 0) p.emerald else p.textMuted, radius = r, center = Offset(x, y))
            }

            // Label sumbu X
            val step = ceil(n / 8f).toInt().coerceAtLeast(1)
            points.forEachIndexed { i, pt ->
                if (n <= 8 || i % step == 0 || i == n - 1) {
                    val t = measurer.measure(pt.label, labelStyle)
                    val x = (xAt(i) - t.size.width / 2f).coerceIn(0f, w - t.size.width)
                    drawText(t, topLeft = Offset(x, h - padB + 6.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("● Titik murajaah", color = p.emerald, fontSize = 11.sp)
            Text("● Puncak (${maxVal} hal)", color = p.gold, fontSize = 11.sp)
        }
    }
}

private fun nearestIndex(x: Float, width: Float, n: Int, padL: Float, padR: Float): Int {
    if (n <= 0) return -1
    if (n == 1) return 0
    var best = 0
    var bestDist = Float.MAX_VALUE
    for (i in 0 until n) {
        val xi = padL + i.toFloat() / (n - 1) * (width - padL - padR)
        val d = abs(xi - x)
        if (d < bestDist) { bestDist = d; best = i }
    }
    return best
}

// ===========================================================================
// Dialog daftar halaman (detail Juz / isi sebuah Box Leitner)
// ===========================================================================

@Composable
private fun PageListDialog(
    title: String,
    subtitle: String,
    pages: List<Int>,
    progress: Map<Int, CardProgress>,
    showFilter: Boolean,
    onPick: (Int) -> Unit,
    onClose: () -> Unit
) {
    val p = LocalPalette.current
    var filter by remember { mutableIntStateOf(0) } // 0 semua, 1 sudah, 2 belum
    var search by remember { mutableStateOf("") }

    val shown = remember(pages, filter, search, progress) {
        val q = search.trim().lowercase()
        pages.filter { page ->
            val reviewed = (progress[page]?.repetitions ?: 0) > 0
            val passFilter = when (filter) { 1 -> reviewed; 2 -> !reviewed; else -> true }
            val meta = QuranData.pageMeta(page)
            val passSearch = q.isEmpty() || page.toString().contains(q) ||
                meta.surahNameLatin.lowercase().contains(q) || meta.juz.toString() == q
            passFilter && passSearch
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(12.dp).fillMaxWidth().fillMaxSize(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = p.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, p.emerald.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text("$subtitle • ${shown.size} halaman", color = p.textMuted, fontSize = 12.sp)
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Tutup") }
                }
                Spacer(Modifier.height(8.dp))
                if (showFilter) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Semua", "Sudah", "Belum").forEachIndexed { i, label ->
                            val sel = filter == i
                            Text(
                                label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (sel) Color.White else p.textMuted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (sel) p.emerald else p.surfaceAlt)
                                    .clickable { filter = i }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Cari halaman / surat", fontSize = 13.sp) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it }) { page ->
                        val meta = QuranData.pageMeta(page)
                        val prog = progress[page]
                        val reviewed = prog != null && prog.repetitions > 0
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(p.background)
                                .border(1.dp, p.border, RoundedCornerShape(14.dp))
                                .clickable { onPick(page) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Halaman $page • ${meta.surahNameLatin}", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    if (reviewed) "Diulang ${prog!!.repetitions}x • Siklus ${prog.intervalDays} hari"
                                    else "Belum dimurajaah • Lembar ${meta.lembar} ${meta.side.id}",
                                    color = p.textMuted, fontSize = 11.sp
                                )
                                if (reviewed) {
                                    val next = remember(prog!!.nextReviewDate) { NextReviewLabel.describe(prog.nextReviewDate) }
                                    Text(
                                        "Murajaah berikutnya: ${next.text}",
                                        color = if (next.urgent) p.danger else p.textMuted,
                                        fontWeight = if (next.urgent) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if (reviewed) {
                                val s = prog!!.lastCompositeScore ?: 0
                                Column(horizontalAlignment = Alignment.End) {
                                    Pill("Box ${prog.box}", p.emerald, textSize = 10)
                                    Text("$s%", color = if (s >= 80) p.emerald else if (s >= 60) p.warning else p.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
