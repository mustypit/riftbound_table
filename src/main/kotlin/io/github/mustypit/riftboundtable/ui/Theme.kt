package io.github.mustypit.riftboundtable.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.mustypit.riftboundtable.game.Card
import io.github.mustypit.riftboundtable.game.Domain
import kotlinx.serialization.Serializable

/** App colours: Graphite dark (RiftTracker's dark palette) with the Butter accent. */
object C {
    val Paper = Color(0xFF121314)
    val Card = Color(0xFF1D1E20)
    val Soft = Color(0xFF242629)
    val Line = Color(0xFF2F3134)
    val Ink = Color(0xFFEEEFF1)
    val Muted = Color(0xFF9C9EA3)
    val Black = Color(0xFF000000)
    val Accent = Color(0xFFFCEA92)
    val OnAccent = Color(0xFF111111)
    val Red = Color(0xFFE2483A)
    val Green = Color(0xFF3FAE63)

    fun domain(d: Domain) = Color(d.argb)
    fun inkOn(c: Color) = if (c.luminance() > 0.4f) Color(0xFF111111) else Color.White
}

/** The table's surface colours (Settings → Table). */
@Serializable
enum class TableTheme(val label: String) { GRAPHITE("Graphite"), NAVY("Navy") }

data class Felt(val felt: Color, val zone: Color, val edge: Color, val text: Color, val dim: Color, val panel: Color, val panel2: Color)

fun feltOf(t: TableTheme) = when (t) {
    TableTheme.GRAPHITE -> Felt(Color(0xFF161719), Color(0xFF1D1E20), Color(0xFF34363A), Color(0xFFEEEFF1), Color(0xFF8D9095), Color(0xFF1D1E20), Color(0xFF121314))
    TableTheme.NAVY -> Felt(Color(0xFF14233D), Color(0xFF182B4A), Color(0xFF3A5277), Color(0xFFE9EEF7), Color(0xFF8EA3C4), Color(0xFF13213A), Color(0xFF0F1A2F))
}

val LocalFelt = staticCompositionLocalOf { feltOf(TableTheme.GRAPHITE) }

/** A seat's two colours: its legend's domains (or grey). */
fun seatColors(legend: Card?): Pair<Color, Color> {
    val d = legend?.domainList.orEmpty()
    val a = d.getOrNull(0)?.let(C::domain) ?: C.Muted
    return a to (d.getOrNull(1)?.let(C::domain) ?: a)
}

@Composable
fun AppTheme(table: TableTheme, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent, onPrimary = C.OnAccent, background = C.Paper, surface = C.Card, onSurface = C.Ink,
            onBackground = C.Ink, surfaceVariant = C.Soft, outline = C.Line, secondary = C.Accent,
        ),
    ) { CompositionLocalProvider(LocalFelt provides feltOf(table), content = content) }
}
