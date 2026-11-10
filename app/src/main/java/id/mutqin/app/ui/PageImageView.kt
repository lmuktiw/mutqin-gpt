@file:OptIn(ExperimentalFoundationApi::class)

package id.mutqin.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import id.mutqin.app.ErrorLabel
import id.mutqin.app.PageHit
import id.mutqin.app.PageHitTest
import id.mutqin.app.PageImage
import id.mutqin.app.PageText
import id.mutqin.app.QuranAyah
import id.mutqin.app.QuranWord

/**
 * Halaman Al-Qur'an sebagai GAMBAR utuh (tata letak persis mushaf cetak) dengan kotak transparan di atas tiap kata dan
 * tiap nomor ayat. Kotak tidak terlihat sama sekali sampai disentuh: ketuk kata/nomor ayat = beri/hapus label dengan alat aktif
 * (warna, garis bawah, coretan "terlewat", dan label murajaah sebelumnya yang pudar-putus-putus sama seperti tampilan teks lama);
 * tahan lama nomor ayat = menu tandai seluruh ayat.
 */
@Composable
fun PageImageView(
    image: PageImage,
    text: PageText,
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
    val density = LocalDensity.current
    val wordById = remember(text) { text.ayahs.flatMap { it.words }.associateBy { it.id } }
    val ayahByKey = remember(text) { text.ayahs.associateBy { it.surahNumber to it.ayahNumber } }
    val labelNow by rememberUpdatedState(labelOf)
    val previousNow by rememberUpdatedState(previousOf)
    val wordClickNow by rememberUpdatedState(onWordClick)
    val ayahTapNow by rememberUpdatedState(onAyahTap)
    val ayahLongNow by rememberUpdatedState(onAyahLongPress)
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    // Gambar hitam-putih dipetakan ke warna kertas/tinta tema aktif (sama seperti teks pada tampilan lama).
    val tint = remember(p.mushafPaper, p.mushafInk) {
        val ink = p.mushafInk
        val paper = p.mushafPaper
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    paper.red - ink.red, 0f, 0f, 0f, ink.red * 255f,
                    paper.green - ink.green, 0f, 0f, 0f, ink.green * 255f,
                    paper.blue - ink.blue, 0f, 0f, 0f, ink.blue * 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    // Seluruh komposisi dikunci LTR: koordinat kotak adalah koordinat gambar (kiri-ke-kanan) apa pun arah teks di sekitarnya.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(image.aspect)
                .clip(RoundedCornerShape(6.dp))
                .background(p.mushafPaper)
                .onSizeChanged { sizePx = it }
        ) {
            Image(
                bitmap = image.bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                colorFilter = tint,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(image, text) {
                        val padX = 3.dp.toPx() / size.width
                        val padY = 3.dp.toPx() / size.height
                        detectTapGestures(
                            onTap = { o ->
                                when (val hit = PageHitTest.find(image.boxes, o.x / size.width, o.y / size.height, padX, padY)) {
                                    is PageHit.Word -> wordById[hit.box.wordId]?.let { wordClickNow(it) }
                                    is PageHit.Mark -> ayahByKey[hit.box.surahNumber to hit.box.ayahNumber]?.let { ayahTapNow(it) }
                                    null -> Unit
                                }
                            },
                            onLongPress = { o ->
                                val hit = PageHitTest.find(image.boxes, o.x / size.width, o.y / size.height, padX, padY)
                                if (hit is PageHit.Mark) ayahByKey[hit.box.surahNumber to hit.box.ayahNumber]?.let { ayahLongNow(it) }
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height
                val radius = CornerRadius(6.dp.toPx())
                for (box in image.boxes.words) {
                    val word = wordById[box.wordId] ?: continue
                    val label = labelNow(word)
                    val marked = label != ErrorLabel.NONE
                    val previous = if (marked) ErrorLabel.NONE else previousNow(word)
                    if (!marked && previous == ErrorLabel.NONE) continue // tidak disentuh = tidak tergambar apa pun

                    val vInset = (box.y1 - box.y0) * h * 0.05f
                    val left = box.x0 * w
                    val right = box.x1 * w
                    val top = box.y0 * h + vInset
                    val bottom = box.y1 * h - vInset
                    val topLeft = Offset(left, top)
                    val sz = Size(right - left, bottom - top)
                    if (marked) {
                        val c = label.color(p)
                        drawRoundRect(c.copy(alpha = 0.28f), topLeft, sz, radius)
                        val y = bottom - 1.dp.toPx()
                        drawLine(c, Offset(left, y), Offset(right, y), strokeWidth = 2.dp.toPx())
                        if (label == ErrorLabel.SKIPPED) {
                            val cy = (top + bottom) / 2f
                            drawLine(c, Offset(left, cy), Offset(right, cy), strokeWidth = 2.dp.toPx())
                        }
                    } else {
                        // Label murajaah SEBELUMNYA: lebih pudar dan bergaris putus-putus agar tidak tertukar dengan label sesi ini
                        val c = previous.color(p)
                        drawRoundRect(c.copy(alpha = 0.10f), topLeft, sz, radius)
                        val y = bottom - 1.dp.toPx()
                        drawLine(
                            c.copy(alpha = 0.6f), Offset(left, y), Offset(right, y),
                            strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))
                        )
                    }
                }
            }

            // Menu tahan-lama nomor ayat: jangkar di tengah penanda ayat
            val mark = menuAyah?.let { a -> image.boxes.marks.firstOrNull { it.ayahNumber == a } }
            val menuTarget = mark?.let { ayahByKey[it.surahNumber to it.ayahNumber] }
            if (mark != null && menuTarget != null && sizePx.width > 0) {
                val ax = with(density) { (((mark.x0 + mark.x1) / 2f) * sizePx.width).toInt() }
                val ay = with(density) { (((mark.y0 + mark.y1) / 2f) * sizePx.height).toInt() }
                Box(Modifier.absoluteOffset(x = with(density) { ax.toDp() }, y = with(density) { ay.toDp() }).size(1.dp)) {
                    DropdownMenu(expanded = true, onDismissRequest = onDismissMenu) {
                        DropdownMenuItem(text = { Text("Ayat ${menuTarget.ayahNumber}", fontWeight = FontWeight.Bold) }, onClick = onDismissMenu)
                        DropdownMenuItem(text = { Text("🔴 Tandai Semua Salah") }, onClick = { onSetAyahLabel(menuTarget, ErrorLabel.MISTAKE) })
                        DropdownMenuItem(text = { Text("🟡 Tandai Semua Ragu") }, onClick = { onSetAyahLabel(menuTarget, ErrorLabel.HESITATION) })
                        DropdownMenuItem(text = { Text("🟣 Tandai Terlewat") }, onClick = { onSetAyahLabel(menuTarget, ErrorLabel.SKIPPED) })
                        DropdownMenuItem(text = { Text("🔵 Tandai Harakat/Tajweed") }, onClick = { onSetAyahLabel(menuTarget, ErrorLabel.TAJWEED) })
                        DropdownMenuItem(text = { Text("⚪ Bersihkan Ayat Ini") }, onClick = { onSetAyahLabel(menuTarget, ErrorLabel.NONE) })
                    }
                }
            }
        }
    }
}
