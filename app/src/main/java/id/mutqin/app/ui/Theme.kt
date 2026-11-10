package id.mutqin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import id.mutqin.app.ErrorLabel
import id.mutqin.app.R

@Immutable
data class MutqinPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val emerald: Color,
    val emeraldDeep: Color,
    val gold: Color,
    val goldSoft: Color,
    val headerBg: Color,
    val mushafPaper: Color,
    val mushafInk: Color,
    val danger: Color,
    val warning: Color,
    val purple: Color,
    val blue: Color,
    val teal: Color
)

val DarkPalette = MutqinPalette(
    isDark = true,
    background = Color(0xFF020617),
    surface = Color(0xFF0F172A),
    surfaceAlt = Color(0xFF1E293B),
    border = Color(0xFF1F3B36),
    textPrimary = Color(0xFFF1F5F9),
    textMuted = Color(0xFF94A3B8),
    emerald = Color(0xFF10B981),
    emeraldDeep = Color(0xFF064E3B),
    gold = Color(0xFFF59E0B),
    goldSoft = Color(0xFFFCD34D),
    headerBg = Color(0xFF022C22),
    mushafPaper = Color(0xFF081813),
    mushafInk = Color(0xFFFEF3C7),
    danger = Color(0xFFEF4444),
    warning = Color(0xFFF59E0B),
    purple = Color(0xFFA855F7),
    blue = Color(0xFF3B82F6),
    teal = Color(0xFF2DD4BF)
)

val LightPalette = MutqinPalette(
    isDark = false,
    background = Color(0xFFFAF8F2),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF1EFE6),
    border = Color(0xFFE2DCC8),
    textPrimary = Color(0xFF0F172A),
    textMuted = Color(0xFF64748B),
    emerald = Color(0xFF059669),
    emeraldDeep = Color(0xFFD1FAE5),
    gold = Color(0xFFD97706),
    goldSoft = Color(0xFFB45309),
    headerBg = Color(0xFF022C22),
    mushafPaper = Color(0xFFFCFBF7),
    mushafInk = Color(0xFF0F172A),
    danger = Color(0xFFDC2626),
    warning = Color(0xFFD97706),
    purple = Color(0xFF9333EA),
    blue = Color(0xFF2563EB),
    teal = Color(0xFF0D9488)
)

/** Tema "Zamrud": hijau zamrud tua dengan aksen emas (Emerald Deep Green #064E3B). */
val EmeraldPalette = MutqinPalette(
    isDark = true,
    background = Color(0xFF04382B),
    surface = Color(0xFF064E3B),
    surfaceAlt = Color(0xFF0B6149),
    border = Color(0xFF1F7A5E),
    textPrimary = Color(0xFFECFDF5),
    textMuted = Color(0xFFA7F3D0),
    emerald = Color(0xFF34D399),
    emeraldDeep = Color(0xFF065F46),
    gold = Color(0xFFFBBF24),
    goldSoft = Color(0xFFFCD34D),
    headerBg = Color(0xFF022C22),
    mushafPaper = Color(0xFF053D2F),
    mushafInk = Color(0xFFFEF3C7),
    danger = Color(0xFFF87171),
    warning = Color(0xFFFBBF24),
    purple = Color(0xFFC084FC),
    blue = Color(0xFF60A5FA),
    teal = Color(0xFF5EEAD4)
)

/** Kunci tema yang tersedia: "mushaf" (krem terang), "emerald" (zamrud), "dark" (gelap). */
val THEME_OPTIONS = listOf(
    "mushaf" to "Mushaf (krem terang)",
    "emerald" to "Zamrud",
    "dark" to "Gelap"
)

fun paletteFor(themeKey: String): MutqinPalette = when (themeKey) {
    "emerald" -> EmeraldPalette
    "dark" -> DarkPalette
    else -> LightPalette
}

val LocalPalette = staticCompositionLocalOf { DarkPalette }

object MutqinFonts {
    /** Naskh/Uthmani untuk teks halaman mushaf. */
    val quran = FontFamily(Font(R.font.scheherazade_new_regular, FontWeight.Normal))
    /** Amiri Bold untuk memory trigger. */
    val trigger = FontFamily(Font(R.font.amiri_bold, FontWeight.Bold))
}

fun ErrorLabel.color(p: MutqinPalette): Color = when (this) {
    ErrorLabel.MISTAKE -> p.danger
    ErrorLabel.HESITATION -> p.warning
    ErrorLabel.SKIPPED -> p.purple
    ErrorLabel.TAJWEED -> p.blue
    ErrorLabel.NONE -> p.textMuted
}

@Composable
fun MutqinTheme(themeKey: String, content: @Composable () -> Unit) {
    val p = paletteFor(themeKey)
    val scheme = if (p.isDark) {
        darkColorScheme(
            primary = p.emerald, onPrimary = Color(0xFF022C22),
            secondary = p.gold, onSecondary = Color(0xFF1C1300),
            background = p.background, onBackground = p.textPrimary,
            surface = p.surface, onSurface = p.textPrimary,
            surfaceVariant = p.surfaceAlt, onSurfaceVariant = p.textMuted,
            outline = p.border, error = p.danger
        )
    } else {
        lightColorScheme(
            primary = p.emerald, onPrimary = Color.White,
            secondary = p.gold, onSecondary = Color.White,
            background = p.background, onBackground = p.textPrimary,
            surface = p.surface, onSurface = p.textPrimary,
            surfaceVariant = p.surfaceAlt, onSurfaceVariant = p.textMuted,
            outline = p.border, error = p.danger
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

// ---------------------------------------------------------------------------
// Komponen kecil yang dipakai di banyak layar
// ---------------------------------------------------------------------------

/** Kartu dengan border tipis; [accent] mewarnai border (mis. emas / zamrud). */
@Composable
fun MCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalPalette.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = p.surface,
        border = BorderStroke(1.dp, (accent ?: p.border).copy(alpha = if (accent != null) 0.55f else 1f))
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    textSize: Int = 11
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (filled) 1f else 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            color = if (filled) Color(0xFF020617) else color,
            fontSize = textSize.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (subtitle != null) Text(subtitle, color = p.textMuted, fontSize = 12.sp)
    }
}
