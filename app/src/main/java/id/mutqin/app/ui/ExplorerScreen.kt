@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package id.mutqin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mutqin.app.CardProgress
import id.mutqin.app.MutqinViewModel
import id.mutqin.app.QuranData
import id.mutqin.app.QuranPageMeta
import id.mutqin.app.Time
import java.time.Instant

private val STATUS_LABELS = listOf("Semua Halaman", "⏰ Jatuh Tempo", "⭐ Mutqin (Box 5-7)", "⚪ Belum Direview")

@Composable
fun ExplorerScreen(vm: MutqinViewModel) {
    val p = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var juz by rememberSaveable { mutableStateOf(0) }       // 0 = semua juz
    var status by rememberSaveable { mutableStateOf(0) }    // 0 semua, 1 jatuh tempo, 2 mutqin, 3 belum
    var juzMenu by remember { mutableStateOf(false) }

    val progress = vm.progress
    // Jatuh tempo & rentang aktif memakai sumber yang sama dengan statistik dan kartu flashcard.
    val dueSet = remember(vm.dueNowPages) { vm.dueNowPages.toHashSet() }
    val scopeSet = remember(vm.settings.activeScopePages) { vm.settings.activeScopePages.toHashSet() }
    val filtered = remember(query, juz, status, progress, dueSet) {
        val q = query.trim().lowercase()
        QuranData.allPages.filter { page ->
            if (juz != 0 && page.juz != juz) return@filter false
            if (q.isNotEmpty()) {
                val match = page.pageNumber.toString().contains(q) ||
                    page.surahNameLatin.lowercase().contains(q) ||
                    page.surahNameArabic.contains(q) ||
                    page.memoryTriggerArabic.contains(q)
                if (!match) return@filter false
            }
            val prog = progress[page.pageNumber]
            when (status) {
                1 -> page.pageNumber in dueSet
                2 -> prog != null && prog.box >= 5
                3 -> prog == null || prog.repetitions == 0
                else -> true
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            MCard(Modifier.fillMaxWidth(), accent = p.emerald) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Cari halaman (22) atau surat (Al-Baqarah)", fontSize = 13.sp) },
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Juz:", color = p.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.size(8.dp))
                    Box {
                        OutlinedButton(onClick = { juzMenu = true }, shape = RoundedCornerShape(12.dp)) {
                            Text(if (juz == 0) "Semua Juz (1-30)" else "Juz $juz", fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = juzMenu, onDismissRequest = { juzMenu = false }) {
                            DropdownMenuItem(text = { Text("Semua Juz (1-30)") }, onClick = { juz = 0; juzMenu = false })
                            (1..30).forEach { j ->
                                DropdownMenuItem(text = { Text("Juz $j") }, onClick = { juz = j; juzMenu = false })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    STATUS_LABELS.forEachIndexed { i, label ->
                        val selected = status == i
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) androidx.compose.ui.graphics.Color.White else p.textMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) p.emerald else p.surfaceAlt)
                                .clickable { status = i }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Menampilkan ${filtered.size} dari 604 halaman", color = p.textMuted, fontSize = 12.sp)
                Text("Mushaf Madinah (20 hal/juz)", color = p.textMuted, fontSize = 12.sp)
            }
        }
        items(filtered, key = { it.pageNumber }) { page ->
            PageCard(
                page = page,
                prog = progress[page.pageNumber],
                isCurrent = page.pageNumber == vm.currentPage,
                isDue = page.pageNumber in dueSet,
                inScope = page.pageNumber in scopeSet
            ) { vm.selectPage(page.pageNumber, manual = true) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PageCard(
    page: QuranPageMeta,
    prog: CardProgress?,
    isCurrent: Boolean,
    isDue: Boolean,
    inScope: Boolean,
    onClick: () -> Unit
) {
    val p = LocalPalette.current
    val reviewed = prog != null && prog.repetitions > 0
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isCurrent) p.emeraldDeep.copy(alpha = if (p.isDark) 0.7f else 1f) else p.surface)
            .border(1.dp, if (isCurrent) p.gold else p.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(p.gold.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) { Text("${page.pageNumber}", color = p.goldSoft, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(page.surahNameLatin, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Juz ${page.juz} • Lembar ${page.lembar} ${page.side.id}", color = p.textMuted, fontSize = 11.sp)
                if (!inScope) {
                    Text("Di luar target murajaah", color = p.warning, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (reviewed) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill("Box ${prog!!.box}", if (isDue) p.warning else p.emerald, textSize = 10)
                    val s = prog.lastCompositeScore ?: 0
                    Pill("Skor: $s%", if (s >= 80) p.emerald else if (s >= 60) p.warning else p.danger, textSize = 10)
                }
            } else {
                Pill("Belum Dimurajaah", p.textMuted, textSize = 10)
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(
                page.memoryTriggerArabic,
                fontFamily = MutqinFonts.quran,
                fontSize = 18.sp,
                color = p.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (reviewed) "Diulang: ${prog!!.repetitions}x • Siklus: ${prog.intervalDays} hari" else page.positionLabel,
                color = p.textMuted, fontSize = 11.sp
            )
            Text("Pilih & Murajaah ▶", color = p.emerald, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ===========================================================================
// Panduan metode Dr. Ali Rajabi
// ===========================================================================

@Composable
fun GuideScreen(onStart: () -> Unit) {
    val p = LocalPalette.current
    val sections = listOf(
        "1. Memory Trigger (Potongan Awal Halaman)" to
            "Bagian depan flashcard hanya menampilkan beberapa kata pertama halaman dalam aksara Uthmani. Otak dipaksa menarik memori secara mandiri (retrieval effort). Semakin keras usaha memanggil ingatan, semakin kuat jejak memorinya.",
        "2. Topografi Spasial Mushaf Madinah" to
            "Mengetahui posisi halaman (kanan/kiri), nomor lembar di dalam juz, dan sisi depan/belakang mengaktifkan memori visual-spasial. Anda tidak sekadar menghafal teks, tetapi juga \"peta\" letak ayat di lembaran mushaf.",
        "3. Kategorisasi Kesalahan Terperinci" to
            "Bukan sekadar \"hafal atau tidak\". Kesalahan dibedakan menjadi Salah/Lupa (mistake), Ragu/Tersendat (hesitation), Terlewat (skipped), dan Harakat/Tajweed. Tiap jenis diberi bobot penalti berbeda pada skor retensi.",
        "4. 7 Kotak Leitner & Kurva Lupa Ebbinghaus" to
            "Halaman yang mutqin diulang pada interval yang makin renggang: 1 → 3 → 7 → 14 → 30 → 60 → 120 hari. Jadwal ini mengulang tepat saat otak mulai melupakan, sehingga waktu murajaah lebih efisien."
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            "Metode Saintifik Hafalan Al-Qur'an",
            "Active Recall & Spaced Repetition berdasarkan riset Dr. Ali Rajabi"
        )
        Text(
            "Murajaah paling efektif bukan dengan membaca ulang secara pasif, melainkan dengan Active Recall (memanggil ingatan secara aktif) yang dipadukan dengan Spaced Repetition (pengulangan berjarak).",
            color = p.textMuted, fontSize = 13.sp
        )
        sections.forEach { (title, body) ->
            MCard(Modifier.fillMaxWidth(), accent = p.emerald) {
                Text(title, color = p.goldSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(body, color = p.textPrimary, fontSize = 13.sp)
            }
        }
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.gold, contentColor = androidx.compose.ui.graphics.Color(0xFF020617))
        ) { Text("Mulai Murajaah Sekarang", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(12.dp))
    }
}
