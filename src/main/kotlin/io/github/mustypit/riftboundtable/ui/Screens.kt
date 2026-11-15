package io.github.mustypit.riftboundtable.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mustypit.riftboundtable.AppState
import io.github.mustypit.riftboundtable.Screen
import io.github.mustypit.riftboundtable.game.Cards
import io.github.mustypit.riftboundtable.game.Deck
import io.github.mustypit.riftboundtable.game.DeckCodeFormat
import io.github.mustypit.riftboundtable.game.DeckCodes
import io.github.mustypit.riftboundtable.game.DeckRules
import io.github.mustypit.riftboundtable.game.Domain
import io.github.mustypit.riftboundtable.game.Legends
import io.github.mustypit.riftboundtable.game.Side

@Composable
private fun Page(title: String, back: (() -> Unit)?, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(C.Paper)) {
        Row(Modifier.fillMaxWidth().background(C.Black).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) Text("‹", Modifier.clickable(onClick = back).padding(end = 14.dp).testTag("back"), color = C.Ink, fontSize = 26.sp)
            Text(title, color = C.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            action?.invoke()
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
fun BigButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (enabled) C.Accent else C.Soft).clickable(enabled = enabled, onClick = onClick).padding(14.dp),
        contentAlignment = Alignment.Center) { Text(text, color = if (enabled) C.OnAccent else C.Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SmallButton(text: String, accent: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(if (accent) C.Accent else C.Soft).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) { Text(text, color = if (accent) C.OnAccent else C.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Chip(text: String, color: Color = C.Soft, ink: Color = C.Ink) =
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 8.dp, vertical = 3.dp), color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)

private fun legendArt(deck: Deck) = DeckRules.legendOf(deck)?.let { l -> DeckRules.championTag(l)?.let { Images.legendArt(Legends.idOf(it)) } ?: Images.card(l.slug) }

@Composable
private fun DeckSummary(deck: Deck, title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(C.Card).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)).background(C.Soft)) {
            legendArt(deck)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = C.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            val legend = DeckRules.legendOf(deck)
            Text(subtitle ?: listOfNotNull(legend?.tags?.firstOrNull(), legend?.name).joinToString(" · "), color = C.Muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val problems = DeckRules.problems(deck)
                if (problems.isEmpty()) Chip("${deck.mainSize}/40") else problems.take(3).forEach { Chip(it, if (it == "Banned") C.Red else C.Soft, if (it == "Banned") Color.White else C.Ink) }
            }
        }
    }
}

@Composable
fun NewGameScreen(app: AppState) {
    Page("New game", null, action = { Text("⚙", Modifier.clickable { app.go(Screen.Settings) }.testTag("settings"), color = C.Ink, fontSize = 20.sp) }) {
        for (side in listOf(Side.ME, Side.OPP)) {
            val deck = app.deckOf(side)
            Box(Modifier.clickable { app.go(Screen.PickDeck(side)) }.testTag("seat-$side")) {
                if (deck != null) DeckSummary(deck, app.seatName(side), "${deck.name} · ${DeckRules.legendOf(deck)?.name ?: ""}")
                else Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(C.Card).padding(18.dp)) {
                    Text(app.seatName(side), color = C.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("Choose deck", color = C.Muted, fontSize = 13.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Chip("Hot-seat", C.Accent, C.OnAccent); Chip("vs Claude", C.Soft, C.Muted) }
        Spacer(Modifier.height(8.dp))
        BigButton("Roll", enabled = app.deckOf(Side.ME) != null && app.deckOf(Side.OPP) != null, modifier = Modifier.testTag("roll")) { app.roll() }
    }
}

@Composable
fun PickDeckScreen(app: AppState, seat: Side) {
    Page(app.seatName(seat), { app.go(Screen.NewGame) }, action = { SmallButton("Import", accent = true, modifier = Modifier.testTag("import")) { app.go(Screen.Import(seat)) } }) {
        if (app.decks.isEmpty()) Text("No decks", color = C.Muted)
        app.decks.sortedBy { it.name.lowercase() }.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).clickable { app.chooseDeck(seat, d); app.go(Screen.NewGame) }.testTag("deck-${d.name}")) {
                    DeckSummary(d, d.name, modifier = if (app.deckOf(seat)?.id == d.id) Modifier.border(2.dp, C.Accent, RoundedCornerShape(8.dp)) else Modifier)
                }
                Text("✕", Modifier.clickable { app.deleteDeck(d) }.padding(8.dp), color = C.Muted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun ImportScreen(app: AppState, seat: Side?) {
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val parsed = remember(text) {
        val t = text.trim()
        when {
            t.isEmpty() -> null
            DeckCodeFormat.looksLikeCode(t) -> runCatching { DeckCodes.toDeck(t, "") }.getOrElse { null to listOf(it.message ?: "Not a deck code") }
            else -> DeckRules.fromText(t, "")
        }
    }
    val deck = parsed?.first
    val defaultName = deck?.let { d -> DeckRules.legendOf(d)?.tags?.firstOrNull() ?: DeckRules.legendOf(d)?.name } ?: ""
    Page("Import deck", { app.go(seat?.let { Screen.PickDeck(it) } ?: Screen.NewGame) }) {
        Field(text, { text = it }, "Deck code or list", Modifier.height(140.dp), mono = true, tag = "code")
        if (deck != null) {
            Field(name, { name = it }, defaultName, tag = "name")
            DeckSummary(deck, name.ifBlank { defaultName })
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val shown = DeckRules.entries(deck).sortedBy { it.first.energy ?: 0 }.take(10)
                shown.forEach { (c, _) -> Images.card(c.slug)?.let { Image(it, null, Modifier.size(44.dp, 62.dp).clip(RoundedCornerShape(4.dp))) } }
            }
            parsed.second.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), color = C.Muted, fontSize = 12.sp) }
            BigButton("Save", modifier = Modifier.testTag("save")) { app.saveDeck(deck.copy(name = name.ifBlank { defaultName }), seat); app.go(Screen.NewGame) }
        } else if (parsed != null) {
            Text(parsed.second.firstOrNull() ?: "Not a deck", color = C.Red, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, mono: Boolean = false, tag: String = "") {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(C.Card).border(1.dp, C.Line, RoundedCornerShape(6.dp)).padding(10.dp)) {
        if (value.isEmpty()) Text(placeholder, color = C.Muted, fontSize = 13.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default)
        BasicTextField(value, onChange, Modifier.fillMaxWidth().testTag(tag), textStyle = TextStyle(color = C.Ink, fontSize = 13.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default),
            cursorBrush = SolidColor(C.Accent))
    }
}

@Composable
fun BattlefieldScreen(app: AppState, pick: Screen.Battlefields) {
    val deck = app.deckOf(pick.seat) ?: return
    Page(app.seatName(pick.seat), null) {
        Text("Battlefield", color = C.Muted, fontSize = 13.sp)
        AppState.battlefieldsOf(deck).forEach { name ->
            val card = DeckRules.battlefieldCard(name)!!
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(C.Card).clickable { app.pickBattlefield(pick, name) }.testTag("field-$name")) {
                Images.battlefieldArt(card.slug)?.let { Image(it, null, Modifier.fillMaxWidth().height(110.dp), contentScale = ContentScale.Crop) }
                Text(name, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = C.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SettingsScreen(app: AppState) {
    val s = app.settings
    Page("Settings", { app.go(Screen.NewGame) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Table", color = C.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(C.Card).padding(2.dp)) {
                TableTheme.entries.forEach { th ->
                    Text(th.label, Modifier.clip(RoundedCornerShape(4.dp)).background(if (s.table == th) C.Accent else Color.Transparent)
                        .clickable { app.updateSettings { it.copy(table = th) } }.padding(horizontal = 12.dp, vertical = 5.dp).testTag("theme-${th.name}"),
                        color = if (s.table == th) C.OnAccent else C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Card size", color = C.Ink, fontSize = 14.sp, modifier = Modifier.width(100.dp))
            Slider(s.cardScale.toFloat(), { v -> app.updateSettings { it.copy(cardScale = (v / 5).toInt() * 5) } }, Modifier.weight(1f), valueRange = 70f..130f,
                colors = SliderDefaults.colors(thumbColor = C.Accent, activeTrackColor = C.Accent, inactiveTrackColor = C.Soft))
            Text("${s.cardScale}%", color = C.Muted, fontSize = 13.sp, modifier = Modifier.width(48.dp))
        }
    }
}

/** A seat's colours from its deck's legend. */
fun deckColors(deck: Deck?): Pair<Color, Color> = seatColors(deck?.let { DeckRules.legendOf(it) })

