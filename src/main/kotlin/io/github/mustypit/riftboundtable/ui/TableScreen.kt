@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)

package io.github.mustypit.riftboundtable.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mustypit.riftboundtable.AppState
import io.github.mustypit.riftboundtable.game.Action
import io.github.mustypit.riftboundtable.game.CardInst
import io.github.mustypit.riftboundtable.game.Cards
import io.github.mustypit.riftboundtable.game.Phase
import io.github.mustypit.riftboundtable.game.Pool
import io.github.mustypit.riftboundtable.game.Side
import io.github.mustypit.riftboundtable.game.TableState
import io.github.mustypit.riftboundtable.game.Zone
import io.github.mustypit.riftboundtable.game.ZoneKind
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class Drag(val id: Int, val pos: Offset, val grab: Offset)
private data class Menu(val id: Int?, val pile: Zone?, val bf: Int?, val at: Offset)

private val ARROW = Color(0xFF6DDFFF)

/** Screen-only state of the table: where zones and cards are, what's dragged, hovered or open. */
private class TableUi {
    val zoneBounds = HashMap<Zone, Rect>()
    /** Last known bounds (root coordinates) and zone of each card, for slides, previews and arrows. */
    val cardRect = mutableStateMapOf<Int, Rect>()
    val lastZone = HashMap<Int, Zone>()
    var drag by mutableStateOf<Drag?>(null)
    var menu by mutableStateOf<Menu?>(null)
    var browse by mutableStateOf<Zone?>(null)
    var dragOver by mutableStateOf<Zone?>(null)
    var hoverBf by mutableStateOf<Int?>(null)
    val bfRect = mutableStateMapOf<Int, Rect>()
    var rootSize by mutableStateOf(IntSize.Zero)

    fun zoneAt(p: Offset): Zone? = zoneBounds.entries.filter { it.value.contains(p) }
        .minByOrNull { it.value.width * it.value.height }?.key
}

/** Card sizes follow the window height (and Settings → card size), so the table fills the screen. */
private class Dims(heightDp: Float, scale: Float) {
    val cardH = (heightDp * 0.118f * scale).coerceIn(70f, 220f).dp
    val cardW = cardH / 1.4f
    val boardH = cardH + 18.dp
    val handH = cardH * 1.32f
    val handW = handH / 1.4f
    /** The hand peeks up from the bottom edge; hovering raises a card fully. */
    val handRowH = handH * 0.72f
    val backH = cardH * 0.56f
    val backW = backH / 1.4f
    val pileH = cardH * 0.9f
    val smallPileH = cardH * 0.56f
    val bfCardH = cardH * 1.15f
    val bfCardW = bfCardH * 1.4f
    val previewW = cardH * 2.0f
    val font = (cardH.value / 8.5f).coerceIn(11f, 16f).sp
}

/** Whether [viewer] may see the face of card [c] in zone [z]. */
fun visibleTo(c: CardInst, z: Zone, viewer: Side): Boolean = when {
    z.kind == ZoneKind.HAND -> z.side == viewer
    z.kind.hidden -> false
    c.faceDown -> c.owner == viewer
    else -> true
}

private fun typeOf(c: CardInst) = Cards.bySlug(c.slug)?.type

/** Where a card goes when played from hand with a click: spells on the chain, everything else to base. */
private fun playTarget(c: CardInst): Zone = if (typeOf(c) == "spell") Zone.CHAIN else Zone.of(ZoneKind.BASE, c.owner)

@Composable
fun TableScreen(app: AppState) {
    val t = app.table ?: return
    val ui = remember { TableUi() }
    val felt = LocalFelt.current
    val viewer = app.viewer
    val top = viewer.other
    BoxWithConstraints(Modifier.fillMaxSize().background(felt.felt).onGloballyPositioned { ui.rootSize = it.size }.testTag("table")) {
        val dims = Dims(maxHeight.value, app.settings.cardScale / 100f)
        Row(Modifier.fillMaxSize()) {
            Board(app, t, ui, dims, viewer, top, Modifier.weight(1f).fillMaxHeight())
            SidePanel(app, t, ui, dims, viewer, Modifier.width((dims.cardH * 2.4f).coerceIn(300.dp, 420.dp)).fillMaxHeight())
        }
        TargetArrows(t, ui)
        HoverPreview(app, t, ui, dims, viewer)
        ui.drag?.let { d ->
            val c = t.cards[d.id]
            if (c != null) {
                val z = t.zoneOf(d.id)
                val tl = d.pos - d.grab
                Box(Modifier.offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }.graphicsLayer { shadowElevation = 24f; scaleX = 1.06f; scaleY = 1.06f }) {
                    CardFace(c, z != null && visibleTo(c, z, viewer), dims.cardW, dims.cardH)
                }
            }
        }
        app.targeting?.let { src -> TargetBanner(app, t, src) }
        ui.browse?.let { PileBrowser(app, t, ui, it, viewer, dims) }
        ui.menu?.let { m -> ContextMenu(app, t, ui, m, viewer) }
        app.editing?.let { id -> if (id in t.cards) EditPanel(app, t, ui, id) }
        t.winner()?.let { WinBanner(app, it) }
        app.cover?.let { CoverScreen(app, it) }
    }
}

/** A seat's colours from its legend on the table. */
private fun seatOf(t: TableState, side: Side): Pair<Color, Color> = seatColors(Cards.bySlug(t.cardsIn(Zone.of(ZoneKind.LEGEND, side)).firstOrNull()?.slug ?: ""))

// ---------- board ----------

@Composable
private fun Board(app: AppState, t: TableState, ui: TableUi, dims: Dims, viewer: Side, top: Side, modifier: Modifier) {
    Row(modifier.padding(top = 6.dp, start = 6.dp, end = 6.dp)) {
        Column(Modifier.width(48.dp).fillMaxHeight()) {
            Spacer(Modifier.weight(1f))
            ScoreTrack(app, t, viewer, upwards = true, Modifier.weight(1f).padding(bottom = 8.dp))
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EdgeRow(app, t, ui, dims, top, viewer, isTop = true)
            BoardRow(app, t, ui, dims, top, viewer, isTop = true)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in t.battlefields.indices) BattlefieldView(app, t, ui, dims, i, viewer, top, Modifier.weight(1f).fillMaxHeight())
            }
            BoardRow(app, t, ui, dims, viewer, viewer, isTop = false)
            EdgeRow(app, t, ui, dims, viewer, viewer, isTop = false)
        }
        Column(Modifier.width(48.dp).fillMaxHeight()) {
            ScoreTrack(app, t, top, upwards = false, Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScoreTrack(app: AppState, t: TableState, side: Side, upwards: Boolean, modifier: Modifier) {
    val felt = LocalFelt.current
    val (c1, c2) = seatOf(t, side)
    val score = t.score[side] ?: 0
    val pop = remember(side) { Animatable(1f) }
    LaunchedEffect(score) { pop.snapTo(1.25f); pop.animateTo(1f, tween(220)) }
    val values = (0..t.pointsToWin).toList().let { if (upwards) it.reversed() else it }
    Column(modifier.fillMaxWidth().testTag("track-$side"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        if (!upwards) Text(app.shortName(side), color = felt.dim, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        for (v in values) {
            val on = v == score
            Box(
                Modifier.size(34.dp, 26.dp).scale(if (on) pop.value else 1f).clip(RoundedCornerShape(8.dp))
                    .then(if (on) Modifier.background(Brush.linearGradient(listOf(c1, c2))) else Modifier.background(felt.zone).border(1.dp, felt.edge, RoundedCornerShape(8.dp)))
                    .clickable { app.act(Action.SetScore(side, v)) }.testTag("score-$side-$v"),
                contentAlignment = Alignment.Center,
            ) { Text("$v", color = if (on) Color.White else felt.dim, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
        if (upwards) Text(app.shortName(side), color = felt.dim, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** Edge row: rune deck and floating Energy / Power, the hand, then deck, trash and banishment. */
@Composable
private fun EdgeRow(app: AppState, t: TableState, ui: TableUi, dims: Dims, side: Side, viewer: Side, isTop: Boolean) {
    val hand = Zone.of(ZoneKind.HAND, side)
    val mine = side == viewer
    val h = if (mine) dims.handRowH else dims.backH + 6.dp
    Row(Modifier.fillMaxWidth().height(h), verticalAlignment = if (mine) Alignment.Top else Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Pile(app, t, ui, dims, Zone.of(ZoneKind.RUNE_DECK, side), viewer, if (isTop) dims.smallPileH else dims.pileH)
        PoolCounters(app, t, side, dims)
        DropZone(ui, hand, Modifier.weight(1f).fillMaxHeight(), framed = false) {
            val cards = t.cardsIn(hand)
            if (mine) {
                // The hand peeks up from the bottom edge, slightly fanned; the hovered card rises fully.
                Row(Modifier.align(Alignment.TopCenter).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(-dims.handW * 0.16f)) {
                    val mid = (cards.size - 1) / 2f
                    cards.forEachIndexed { i, c ->
                        TableCard(app, t, ui, c, hand, viewer, dims.handW, dims.handH, dims,
                            fan = (i - mid) * 2.5f, dip = kotlin.math.abs(i - mid) * 3f, rise = dims.handH - dims.handRowH + 4.dp)
                    }
                }
            } else {
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(-dims.backW * 0.1f)) {
                    cards.forEach { c -> TableCard(app, t, ui, c, hand, viewer, dims.backW, dims.backH, dims) }
                }
            }
        }
        val pile = if (isTop) dims.smallPileH else dims.pileH
        Pile(app, t, ui, dims, Zone.of(ZoneKind.DECK, side), viewer, pile)
        Pile(app, t, ui, dims, Zone.of(ZoneKind.TRASH, side), viewer, pile)
        Pile(app, t, ui, dims, Zone.of(ZoneKind.BANISH, side), viewer, dims.smallPileH, hideWhenEmpty = true)
    }
}

/** Board row: the rune box on the left, the base to its right (units centered), then champion and legend. */
@Composable
private fun BoardRow(app: AppState, t: TableState, ui: TableUi, dims: Dims, side: Side, viewer: Side, isTop: Boolean) {
    val felt = LocalFelt.current
    Row(Modifier.fillMaxWidth().height(dims.boardH), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val legend = Zone.of(ZoneKind.LEGEND, side); val champ = Zone.of(ZoneKind.CHAMPION, side); val base = Zone.of(ZoneKind.BASE, side)
        val runes = Zone.of(ZoneKind.RUNES, side)
        @Composable fun slot(z: Zone) = DropZone(ui, z, Modifier.size(dims.cardW + 10.dp, dims.cardH + 10.dp), framed = t.ids(z).isEmpty(), dashed = true) {
            t.cardsIn(z).firstOrNull()?.let { Box(Modifier.align(Alignment.Center)) { TableCard(app, t, ui, it, z, viewer, dims.cardW, dims.cardH, dims) } }
        }
        val all = t.cardsIn(runes)
        // Up to six runes in one row at nearly card size; more wrap onto a second, smaller row.
        val two = all.size > 6
        val runeH = if (two) (dims.boardH - 16.dp) / 2 else dims.cardH * 0.92f
        val runeW = runeH / 1.4f
        DropZone(ui, runes, Modifier.width((dims.cardH * 0.92f / 1.4f + 6.dp) * 6 + 34.dp).fillMaxHeight()) {
            FlowRow(Modifier.align(Alignment.CenterStart).padding(start = 8.dp, end = 26.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp), maxItemsInEachRow = if (two) 7 else 6) {
                all.forEach { TableCard(app, t, ui, it, runes, viewer, runeW, runeH, dims) }
            }
            if (all.isNotEmpty()) Text("${all.count { !it.exhausted }}/${all.size}", Modifier.align(Alignment.TopEnd).padding(5.dp), color = felt.dim, fontSize = dims.font, fontWeight = FontWeight.Bold)
        }
        DropZone(ui, base, Modifier.weight(1f).fillMaxHeight()) { CardRow(app, t, ui, base, viewer, dims, dims.cardW, Modifier.align(Alignment.Center)) }
        if (isTop) { slot(legend); slot(champ) } else { slot(champ); slot(legend) }
    }
}

@Composable
private fun PoolCounters(app: AppState, t: TableState, side: Side, dims: Dims) {
    val felt = LocalFelt.current
    val pool = t.pools[side] ?: Pool()
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((label, value) in listOf("Energy" to pool.energy, "Power" to pool.power)) {
            val set = { d: Int -> app.act(Action.SetPool(side, if (label == "Energy") pool.copy(energy = pool.energy + d) else pool.copy(power = pool.power + d))) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, color = felt.dim, fontSize = dims.font, modifier = Modifier.width(54.dp))
                IconStep(Icons.Default.Remove, "Less $label") { set(-1) }
                Text("$value", color = felt.text, fontSize = dims.font, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(22.dp).testTag("pool-$side-$label"))
                IconStep(Icons.Default.Add, "More $label") { set(1) }
            }
        }
    }
}

@Composable
private fun IconStep(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val felt = LocalFelt.current
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(felt.zone).border(1.dp, felt.edge, RoundedCornerShape(5.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(14.dp), tint = felt.text) }
}

/** A battlefield: art across the zone, the battlefield card in the middle, each side's units centered above and below. */
@Composable
private fun BattlefieldView(app: AppState, t: TableState, ui: TableUi, dims: Dims, i: Int, viewer: Side, top: Side, modifier: Modifier) {
    val felt = LocalFelt.current
    val sd = t.showdown == i
    val shape = RoundedCornerShape(10.dp)
    val slug = t.battlefields[i]
    Box(modifier.clip(shape).background(Color(0xFF191A1C)).border(if (sd) 2.dp else 1.dp, if (sd) C.Accent else felt.edge, shape).testTag("bf-$i")) {
        Images.battlefieldArt(slug)?.let { Image(it, null, Modifier.matchParentSize().alpha(0.28f), contentScale = ContentScale.Crop) }
        Column(Modifier.fillMaxSize()) {
            DropZone(ui, Zone.bf(top, i), Modifier.weight(1f).fillMaxWidth(), framed = false) {
                CardRow(app, t, ui, Zone.bf(top, i), viewer, dims, dims.cardW * 0.94f, Modifier.align(Alignment.Center))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // The battlefield card itself: hover shows it large, click opens or closes a showdown, right-click for more.
                Box(Modifier.size(dims.bfCardW, dims.bfCardH).clip(RoundedCornerShape(6.dp)).background(felt.panel2)
                    .border(if (sd) 2.dp else 1.dp, if (sd) C.Accent else Color.Black, RoundedCornerShape(6.dp))
                    .onGloballyPositioned { ui.bfRect[i] = it.boundsInRoot() }
                    .onPointerEvent(PointerEventType.Enter) { ui.hoverBf = i }.onPointerEvent(PointerEventType.Exit) { if (ui.hoverBf == i) ui.hoverBf = null }
                    .onPointerEvent(PointerEventType.Press) { e -> if (e.buttons.isSecondaryPressed) ui.menu = Menu(null, null, i, (ui.bfRect[i]?.topLeft ?: Offset.Zero) + e.changes.first().position) }
                    .clickable { app.act(Action.Showdown(if (sd) null else i)) }.testTag("bfcard-$i")) {
                    Images.card(slug)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                // Control chip, left of the card: click cycles nobody → bottom seat → top seat.
                val ctl = t.control.getOrNull(i)
                val ctlColors = ctl?.let { seatOf(t, it) }
                Text(ctl?.let { app.shortName(it) } ?: "—", Modifier.align(Alignment.CenterStart).padding(start = 10.dp).clip(RoundedCornerShape(5.dp))
                    .background(if (ctlColors != null) Brush.linearGradient(listOf(ctlColors.first, ctlColors.second)) else Brush.linearGradient(listOf(felt.panel2, felt.panel2)))
                    .border(1.dp, felt.edge, RoundedCornerShape(5.dp))
                    .clickable { app.act(Action.Control(i, when (ctl) { null -> viewer; viewer -> top; else -> null })) }
                    .padding(horizontal = 10.dp, vertical = 4.dp).testTag("control-$i"),
                    color = Color.White, fontSize = dims.font, fontWeight = FontWeight.ExtraBold)
                if (sd) Text("SHOWDOWN", Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).clip(RoundedCornerShape(5.dp)).background(C.Accent)
                    .clickable { app.act(Action.Showdown(null)) }.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = C.OnAccent, fontSize = (dims.font.value - 2).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
            }
            DropZone(ui, Zone.bf(viewer, i), Modifier.weight(1f).fillMaxWidth(), framed = false) {
                CardRow(app, t, ui, Zone.bf(viewer, i), viewer, dims, dims.cardW * 0.94f, Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun CardRow(app: AppState, t: TableState, ui: TableUi, z: Zone, viewer: Side, dims: Dims, w: Dp, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        t.cardsIn(z).forEach { TableCard(app, t, ui, it, z, viewer, w, w * 1.4f, dims) }
    }
}

/** A zone that takes dropped cards; highlights while a card is dragged over it. */
@Composable
private fun DropZone(ui: TableUi, zone: Zone, modifier: Modifier, framed: Boolean = true, dashed: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val felt = LocalFelt.current
    val over = ui.drag != null && ui.dragOver == zone
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.onGloballyPositioned { ui.zoneBounds[zone] = it.boundsInRoot() }
            .then(if (framed) Modifier.background(if (dashed) Color.Transparent else felt.zone.copy(alpha = 0.7f), shape).border(1.dp, felt.edge.copy(alpha = if (dashed) 0.6f else 1f), shape) else Modifier)
            .then(if (over) Modifier.border(2.dp, C.Accent, shape) else Modifier)
            .testTag("zone-${zone.kind}-${zone.side}${if (zone.bf >= 0) "-${zone.bf}" else ""}"),
        content = content,
    )
}

// ---------- cards ----------

@Composable
fun CardFace(c: CardInst, visible: Boolean, w: Dp, h: Dp, modifier: Modifier = Modifier) {
    val img = if (visible) Images.card(c.slug) else Images.back(c.slug)
    val shape = RoundedCornerShape(w * 0.06f)
    Box(modifier.size(w, h).clip(shape).background(Color(0xFF14161A))
        .then(if (c.faceDown) Modifier.border(2.dp, Color(0xFF9C9EA3), shape) else Modifier)) {
        img?.let { Image(it, null, Modifier.fillMaxSize().alpha(if (visible && c.faceDown) 0.55f else 1f), contentScale = ContentScale.Crop) }
        if (visible && img == null) Text(Cards.bySlug(c.slug)?.name ?: c.slug, Modifier.padding(4.dp), color = Color.White, fontSize = 9.sp)
    }
}

/**
 * A card on the table. Click: runes tap for energy (or ready), board cards exhaust, hand cards play.
 * Right-click: runes recycle for power, other cards open their menu. Drag to move. [fan], [dip] and
 * [rise] are drawn inside the card's own box, so hovering a hand card never moves what's under the pointer.
 */
@Composable
private fun TableCard(
    app: AppState, t: TableState, ui: TableUi, c: CardInst, z: Zone, viewer: Side, w: Dp, h: Dp, dims: Dims,
    fan: Float = 0f, dip: Float = 0f, rise: Dp = 0.dp,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val visible = visibleTo(c, z, viewer)
    val slide = remember(c.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val rot by animateFloatAsState(if (c.exhausted) 90f else 0f, tween(140))
    val hovered = app.hovered == c.id
    val lift by animateFloatAsState(if (hovered && rise > 0.dp) 1f else 0f, tween(110))
    val dragging = ui.drag?.id == c.id
    val targetable = app.targeting != null && z.kind != ZoneKind.HAND && !z.kind.hidden && z.kind != ZoneKind.CHAIN
    val slotW = if (c.exhausted) h else w
    val slotH = if (c.exhausted) w else h
    Box(
        Modifier.size(slotW, slotH)
            .onGloballyPositioned { lc ->
                // Slide in from where the card was, but only when it changed zone (no jitter on reflow or hover).
                val r = lc.boundsInRoot()
                val old = ui.cardRect[c.id]
                val oldZone = ui.lastZone[c.id]
                ui.cardRect[c.id] = r; ui.lastZone[c.id] = z
                if (oldZone != null && oldZone != z && app.hovered == c.id) app.hovered = null
                if (old != null && oldZone != null && oldZone != z && !dragging) {
                    scope.launch { slide.snapTo(old.topLeft - r.topLeft); slide.animateTo(Offset.Zero, tween(200)) }
                }
            }
            .graphicsLayer { translationX = slide.value.x; translationY = slide.value.y; alpha = if (dragging) 0.25f else 1f }
            .onPointerEvent(PointerEventType.Enter) { app.hovered = c.id }
            .onPointerEvent(PointerEventType.Exit) { if (app.hovered == c.id) app.hovered = null }
            .onPointerEvent(PointerEventType.Press) { e ->
                if (!e.buttons.isSecondaryPressed) return@onPointerEvent
                val at = (ui.cardRect[c.id]?.topLeft ?: Offset.Zero) + e.changes.first().position
                if (z.kind == ZoneKind.RUNES) app.act(Action.Recycle(c.id)) else ui.menu = Menu(c.id, null, null, at)
            }
            .pointerInput(c.id, z, app.targeting) {
                detectTapGestures(onTap = { p ->
                    // Read the card as it is now: this handler outlives the composition that created it.
                    val c = app.table?.cards?.get(c.id) ?: return@detectTapGestures
                    val src = app.targeting
                    if (src != null && targetable && src != c.id) { app.act(Action.Target(src, c.id)); app.targeting = null; return@detectTapGestures }
                    when (z.kind) {
                        ZoneKind.RUNES -> app.act(if (c.exhausted) Action.Exhaust(c.id, false) else Action.TapRune(c.id))
                        ZoneKind.BASE, ZoneKind.BATTLEFIELD, ZoneKind.LEGEND, ZoneKind.CHAMPION -> app.act(Action.Exhaust(c.id, !c.exhausted))
                        ZoneKind.HAND -> if (z.side == viewer) app.act(Action.Move(c.id, playTarget(c)))
                        else -> ui.menu = Menu(c.id, null, null, (ui.cardRect[c.id]?.topLeft ?: Offset.Zero) + p)
                    }
                })
            }
            .pointerInput(c.id, z) {
                detectDragGestures(
                    onDragStart = { o -> ui.menu = null; ui.drag = Drag(c.id, (ui.cardRect[c.id]?.topLeft ?: Offset.Zero) + o, o) },
                    onDrag = { change, amount -> change.consume(); ui.drag?.let { d -> val np = d.pos + amount; ui.drag = d.copy(pos = np); ui.dragOver = ui.zoneAt(np) } },
                    onDragEnd = { app.table?.let { drop(app, it, ui) } },
                    onDragCancel = { ui.drag = null; ui.dragOver = null },
                )
            }
            .testTag("card-${c.id}"),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer {
            rotationZ = fan * (1f - lift)
            translationY = (dip * density.density) * (1f - lift) - rise.toPx() * lift
        }, contentAlignment = Alignment.Center) {
            CardFace(c, visible, w, h, Modifier.requiredSize(w, h).rotate(rot)
                .then(if (targetable) Modifier.border(2.dp, ARROW.copy(alpha = 0.7f), RoundedCornerShape(w * 0.06f)) else Modifier))
            Badges(t, c, Modifier.size(slotW, slotH), dims)
        }
    }
}

@Composable
private fun Badges(t: TableState, c: CardInst, modifier: Modifier, dims: Dims) {
    Box(modifier) {
        if (c.damage > 0) Badge("${c.damage}", C.Red, Color.White, Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp), dims)
        val printed = Cards.bySlug(c.slug)?.might
        val might = (printed ?: 0) + (if (c.buff) 1 else 0) + c.mightMod
        if (c.buff || c.mightMod != 0) Badge(if (printed != null) "$might" else (if (might >= 0) "+$might" else "$might"), C.Accent, C.OnAccent, Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp), dims)
        if (c.xp > 0) Badge("${c.xp} XP", Color(0xFF3B8BEB), Color.White, Modifier.align(Alignment.BottomStart).offset((-4).dp, 4.dp), dims)
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color, modifier: Modifier, dims: Dims) =
    Text(text, modifier.clip(RoundedCornerShape(5.dp)).background(bg).padding(horizontal = 5.dp, vertical = 1.dp), color = fg, fontSize = dims.font, fontWeight = FontWeight.Bold)

private fun drop(app: AppState, t: TableState, ui: TableUi) {
    val d = ui.drag ?: return
    ui.drag = null; ui.dragOver = null
    val to = ui.zoneAt(d.pos) ?: return
    val from = t.zoneOf(d.id) ?: return
    if (to == from) return
    // Slide from where it was dropped.
    ui.cardRect[d.id]?.let { r -> ui.cardRect[d.id] = Rect(d.pos - d.grab, r.size) }
    val index = when (to.kind) { ZoneKind.DECK, ZoneKind.TRASH, ZoneKind.BANISH -> 0; else -> null }
    app.act(Action.Move(d.id, to, index))
}

// ---------- piles ----------

@Composable
private fun Pile(app: AppState, t: TableState, ui: TableUi, dims: Dims, z: Zone, viewer: Side, h: Dp, hideWhenEmpty: Boolean = false) {
    val felt = LocalFelt.current
    val ids = t.ids(z)
    if (hideWhenEmpty && ids.isEmpty()) { DropZone(ui, z, Modifier.size(1.dp), framed = false) {}; return }
    val w = h / 1.4f
    var rect by remember { mutableStateOf(Rect.Zero) }
    // Cards in a pile "are" at the pile, so a drawn card slides out of it.
    SideEffect { ids.forEach { ui.cardRect[it] = rect; ui.lastZone[it] = z } }
    DropZone(ui, z, Modifier.size(w + 10.dp, h + 10.dp).onGloballyPositioned { rect = it.boundsInRoot() }, framed = false) {
        val topCard = ids.firstOrNull()?.let { t.card(it) }
        Box(Modifier.align(Alignment.Center).size(w, h)
            .onPointerEvent(PointerEventType.Press) { e -> if (e.buttons.isSecondaryPressed) ui.menu = Menu(null, z, null, rect.topLeft + e.changes.first().position) }
            .onPointerEvent(PointerEventType.Enter) { if (topCard != null && !z.kind.hidden) app.hovered = topCard.id }
            .onPointerEvent(PointerEventType.Exit) { if (topCard != null && app.hovered == topCard.id) app.hovered = null }
            .clickable {
                when (z.kind) {
                    ZoneKind.DECK -> app.act(Action.Draw(z.side))
                    ZoneKind.RUNE_DECK -> app.act(Action.Channel(z.side, 1))
                    else -> ui.browse = z
                }
            }.testTag("pile-${z.kind}-${z.side}")) {
            if (topCard == null) Box(Modifier.fillMaxSize().border(1.dp, felt.edge, RoundedCornerShape(5.dp)))
            else CardFace(topCard, !z.kind.hidden, w, h)
            Text("${ids.size}", Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).clip(RoundedCornerShape(6.dp)).background(felt.panel2)
                .border(1.dp, felt.edge, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp), color = felt.text, fontSize = dims.font, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PileBrowser(app: AppState, t: TableState, ui: TableUi, z: Zone, viewer: Side, dims: Dims) {
    val felt = LocalFelt.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).pointerInput(Unit) { detectTapGestures { ui.browse = null } }) {
        Column(Modifier.align(Alignment.Center).widthIn(max = 1100.dp).clip(RoundedCornerShape(10.dp)).background(felt.panel)
            .pointerInput(Unit) { detectTapGestures { } }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val title = when (z.kind) { ZoneKind.TRASH -> "Trash"; ZoneKind.DECK -> "Deck"; ZoneKind.BANISH -> "Banishment"; ZoneKind.RUNE_DECK -> "Rune deck"; else -> "" }
            Text("$title · ${app.seatName(z.side)}", color = felt.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val cards = t.cardsIn(z)
            if (cards.isEmpty()) Text("Empty", color = felt.dim)
            FlowRow(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.forEach { c ->
                    var at by remember { mutableStateOf(Offset.Zero) }
                    CardFace(c, true, dims.cardW * 1.3f, dims.cardH * 1.3f, Modifier.onGloballyPositioned { at = it.boundsInRoot().topLeft }
                        .pointerInput(c.id) { detectTapGestures { o -> ui.menu = Menu(c.id, null, null, at + o) } })
                }
            }
        }
    }
}

// ---------- hover preview, chain arrows, targeting ----------

/** The hovered card shown large next to it (above it for the hand); battlefields show their whole card. */
@Composable
private fun HoverPreview(app: AppState, t: TableState, ui: TableUi, dims: Dims, viewer: Side) {
    if (ui.drag != null || ui.menu != null || app.editing != null) return
    val density = LocalDensity.current
    val root = ui.rootSize
    ui.hoverBf?.let { i ->
        val r = ui.bfRect[i] ?: return
        val w = with(density) { (dims.cardH * 3.0f).toPx() }; val h = w / 1.4f
        val x = (r.center.x - w / 2).coerceIn(0f, (root.width - w).coerceAtLeast(0f)); val y = (r.center.y - h / 2).coerceIn(0f, (root.height - h).coerceAtLeast(0f))
        Images.card(t.battlefields[i])?.let { img ->
            Image(img, null, Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .graphicsLayer { shadowElevation = 30f; shape = RoundedCornerShape(10.dp); clip = true }.testTag("bf-preview"), contentScale = ContentScale.Crop)
        }
        return
    }
    val id = app.hovered ?: return
    val c = t.cards[id] ?: return
    val z = t.zoneOf(id) ?: return
    if (!visibleTo(c, z, viewer) || z.kind == ZoneKind.CHAIN) return
    // While choosing a target, previews of hand cards would cover the board.
    if (app.targeting != null && z.kind == ZoneKind.HAND) return
    val r = ui.cardRect[id] ?: return
    val w = with(density) { dims.previewW.toPx() }; val h = w * 1.4f
    val gap = with(density) { 12.dp.toPx() }
    val (x, y) = if (z.kind == ZoneKind.HAND) {
        (r.center.x - w / 2) to (r.top - h - gap)
    } else {
        val right = r.right + gap
        (if (right + w <= root.width) right else r.left - gap - w) to (r.center.y - h / 2)
    }
    val px = x.coerceIn(0f, (root.width - w).coerceAtLeast(0f)); val py = y.coerceIn(0f, (root.height - h).coerceAtLeast(0f))
    Box(Modifier.offset { IntOffset(px.roundToInt(), py.roundToInt()) }.graphicsLayer { shadowElevation = 30f }.testTag("preview")) {
        CardFace(c.copy(faceDown = false), true, with(density) { w.toDp() }, with(density) { h.toDp() })
    }
}

/** Arrows from chain cards to their targets. */
@Composable
private fun TargetArrows(t: TableState, ui: TableUi) {
    val links = t.ids(Zone.CHAIN).flatMap { src -> t.card(src).targets.map { src to it } }
    if (links.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        for ((src, dst) in links) {
            val a = ui.cardRect[src]?.center ?: continue
            val b = ui.cardRect[dst]?.center ?: continue
            val mid = Offset((a.x + b.x) / 2, minOf(a.y, b.y) - 80f)
            val path = Path().apply { moveTo(a.x, a.y); quadraticTo(mid.x, mid.y, b.x, b.y) }
            drawPath(path, ARROW.copy(alpha = 0.35f), style = Stroke(10f, cap = StrokeCap.Round))
            drawPath(path, ARROW, style = Stroke(3.5f, cap = StrokeCap.Round))
            drawCircle(ARROW, 9f, b); drawCircle(Color.White, 4f, b)
        }
    }
}

@Composable
private fun TargetBanner(app: AppState, t: TableState, src: Int) {
    val felt = LocalFelt.current
    val name = t.cards[src]?.let { Cards.bySlug(it.slug)?.name } ?: return
    Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.clip(RoundedCornerShape(8.dp)).background(felt.panel).border(1.dp, ARROW, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 8.dp).testTag("target-banner"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Choose target for $name", color = felt.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Cancel", Modifier.clip(RoundedCornerShape(5.dp)).background(felt.zone).clickable { app.targeting = null }.padding(horizontal = 10.dp, vertical = 4.dp),
                color = felt.text, fontSize = 13.sp)
        }
    }
}

/** C on a card: set its might and damage directly. */
@Composable
private fun EditPanel(app: AppState, t: TableState, ui: TableUi, id: Int) {
    val felt = LocalFelt.current
    val c = t.card(id)
    val card = Cards.bySlug(c.slug)
    val density = LocalDensity.current
    val r = ui.cardRect[id]
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).pointerInput(Unit) { detectTapGestures { app.editing = null } }) {
        val w = with(density) { 260.dp.toPx() }
        val x = ((r?.right ?: 0f) + 12f).let { if (it + w > ui.rootSize.width) (r?.left ?: 0f) - 12f - w else it }.coerceAtLeast(0f)
        val y = (r?.top ?: 0f).coerceIn(0f, (ui.rootSize.height - with(density) { 240.dp.toPx() }).coerceAtLeast(0f))
        Column(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(260.dp).clip(RoundedCornerShape(10.dp)).background(felt.panel)
            .border(1.dp, felt.edge, RoundedCornerShape(10.dp)).pointerInput(Unit) { detectTapGestures { } }.padding(14.dp).testTag("edit"),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(card?.name ?: c.slug, color = felt.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val printed = card?.might ?: 0
            val might = printed + (if (c.buff) 1 else 0) + c.mightMod
            EditRow("Might", "$might") { d -> app.act(Action.SetMight(id, c.mightMod + d)) }
            EditRow("Damage", "${c.damage}") { d -> app.act(Action.SetDamage(id, c.damage + d)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Buff", color = felt.dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(if (c.buff) "On" else "Off", Modifier.clip(RoundedCornerShape(5.dp)).background(if (c.buff) C.Accent else felt.zone)
                    .clickable { app.act(Action.Buff(id, !c.buff)) }.padding(horizontal = 12.dp, vertical = 4.dp), color = if (c.buff) C.OnAccent else felt.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text("Done", Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(C.Accent).clickable { app.editing = null }.padding(8.dp).testTag("edit-done"),
                color = C.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun EditRow(label: String, value: String, change: (Int) -> Unit) {
    val felt = LocalFelt.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = felt.dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        IconStep(Icons.Default.Remove, "Less $label") { change(-1) }
        Text(value, color = felt.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(30.dp).testTag("edit-$label"))
        IconStep(Icons.Default.Add, "More $label") { change(1) }
    }
}

// ---------- menu ----------

private fun cardMenu(app: AppState, t: TableState, id: Int, viewer: Side): List<Pair<String, () -> Unit>> {
    val c = t.card(id)
    val z = t.zoneOf(id) ?: return emptyList()
    val owner = c.owner
    val move = { to: Zone, index: Int? -> app.act(Action.Move(id, to, index)) }
    val items = mutableListOf<Pair<String, () -> Unit>>()
    val bfName = { i: Int -> Cards.bySlug(t.battlefields[i])?.name ?: "Battlefield ${i + 1}" }
    val onBoard = z.kind in setOf(ZoneKind.BASE, ZoneKind.BATTLEFIELD, ZoneKind.LEGEND, ZoneKind.CHAMPION, ZoneKind.RUNES)
    if (z.kind == ZoneKind.HAND) {
        items += (if (typeOf(c) == "spell") "Play to chain" else "Play to base") to { move(playTarget(c), null) }
        if (typeOf(c) != "spell") for (i in t.battlefields.indices) items += "Play to ${bfName(i)}" to { move(Zone.bf(owner, i), null) }
        for (i in t.battlefields.indices) items += "Hide at ${bfName(i)}" to { app.act(Action.FaceDown(id, true)); move(Zone.bf(owner, i), null) }
        if ((t.turnsTaken[owner] ?: 0) == 0) items += "Mulligan" to { app.act(Action.Mulligan(id)) }
    }
    if (z.kind == ZoneKind.CHAIN) {
        items += "Target  (T)" to { app.targeting = id }
        if (c.targets.isNotEmpty()) items += "Clear targets" to { c.targets.forEach { app.act(Action.Target(id, it)) } }
        if (t.ids(Zone.CHAIN).lastOrNull() == id) items += "Resolve  (S)" to { app.targeting = null; app.act(Action.Resolve) }
    }
    if (onBoard) items += (if (c.exhausted) "Ready" else "Exhaust") to { app.act(Action.Exhaust(id, !c.exhausted)) }
    if (z.kind == ZoneKind.BASE || z.kind == ZoneKind.BATTLEFIELD) {
        items += "Might / damage  (C)" to { app.editing = id }
        items += (if (c.buff) "Remove buff" else "Buff") to { app.act(Action.Buff(id, !c.buff)) }
        items += "XP +1" to { app.act(Action.Xp(id, 1)) }
        if (c.xp > 0) items += "XP −1" to { app.act(Action.Xp(id, -1)) }
        for (i in t.battlefields.indices) if (z != Zone.bf(owner, i)) items += "Move to ${bfName(i)}" to { move(Zone.bf(owner, i), null) }
    }
    if (z.kind == ZoneKind.BATTLEFIELD) items += (if (c.faceDown) "Reveal" else "Face down") to { app.act(Action.FaceDown(id, !c.faceDown)) }
    if (z.kind == ZoneKind.BATTLEFIELD) items += "To base" to { move(Zone.of(ZoneKind.BASE, owner), null) }
    if (z.kind != ZoneKind.HAND) items += "To hand" to { move(Zone.of(ZoneKind.HAND, owner), null) }
    if (z.kind != ZoneKind.TRASH) items += (if (z.kind == ZoneKind.HAND) "Discard" else "To trash") to { move(Zone.of(ZoneKind.TRASH, owner), 0) }
    if (z.kind != ZoneKind.BANISH) items += "Banish" to { move(Zone.of(ZoneKind.BANISH, owner), 0) }
    if (z.kind != ZoneKind.RUNES && z.kind != ZoneKind.RUNE_DECK) {
        items += "Deck top" to { move(Zone.of(ZoneKind.DECK, owner), 0) }
        items += "Deck bottom" to { move(Zone.of(ZoneKind.DECK, owner), null) }
    }
    return items
}

private fun pileMenu(app: AppState, t: TableState, ui: TableUi, z: Zone, viewer: Side): List<Pair<String, () -> Unit>> = when (z.kind) {
    ZoneKind.DECK -> listOf(
        "Draw" to { app.act(Action.Draw(z.side)) },
        "Shuffle" to { app.shuffle(z.side, ZoneKind.DECK) },
        "Top to trash" to { t.ids(z).firstOrNull()?.let { app.act(Action.Move(it, Zone.of(ZoneKind.TRASH, z.side), 0)) } ?: Unit },
    ) + (if (z.side == viewer) listOf("Search" to { ui.browse = z }) else emptyList())
    ZoneKind.RUNE_DECK -> listOf("Channel 1" to { app.act(Action.Channel(z.side, 1)) }, "Shuffle" to { app.shuffle(z.side, ZoneKind.RUNE_DECK) })
    else -> listOf("Look" to { ui.browse = z })
}

private fun bfMenu(app: AppState, t: TableState, i: Int, viewer: Side): List<Pair<String, () -> Unit>> = listOf(
    (if (t.showdown == i) "End showdown" else "Showdown") to { app.act(Action.Showdown(if (t.showdown == i) null else i)) },
    "${app.seatName(viewer)} controls" to { app.act(Action.Control(i, viewer)) },
    "${app.seatName(viewer.other)} controls" to { app.act(Action.Control(i, viewer.other)) },
    "Nobody controls" to { app.act(Action.Control(i, null)) },
)

@Composable
private fun ContextMenu(app: AppState, t: TableState, ui: TableUi, m: Menu, viewer: Side) {
    val felt = LocalFelt.current
    val items = when {
        m.id != null && t.cards.containsKey(m.id) -> cardMenu(app, t, m.id, viewer)
        m.pile != null -> pileMenu(app, t, ui, m.pile, viewer)
        m.bf != null -> bfMenu(app, t, m.bf, viewer)
        else -> emptyList()
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { ui.menu = null } }.onPointerEvent(PointerEventType.Press) { if (it.buttons.isSecondaryPressed) ui.menu = null }) {
        val density = LocalDensity.current
        val menuH = with(density) { (items.size * 34 + 10).dp.toPx() }
        val menuW = with(density) { 210.dp.toPx() }
        val x = m.at.x.coerceAtMost(ui.rootSize.width - menuW - 8f).coerceAtLeast(0f)
        val y = m.at.y.coerceAtMost(ui.rootSize.height - menuH - 8f).coerceAtLeast(0f)
        Column(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(210.dp).clip(RoundedCornerShape(8.dp)).background(felt.panel)
            .border(1.dp, felt.edge, RoundedCornerShape(8.dp)).padding(5.dp).testTag("menu")) {
            items.forEach { (label, act) ->
                Text(label, Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).clickable { ui.menu = null; act() }.height(34.dp).padding(horizontal = 10.dp, vertical = 7.dp).testTag("menu-$label"),
                    color = felt.text, fontSize = 14.sp)
            }
        }
    }
}

// ---------- side panel ----------

@Composable
private fun SidePanel(app: AppState, t: TableState, ui: TableUi, dims: Dims, viewer: Side, modifier: Modifier) {
    val felt = LocalFelt.current
    Column(modifier.background(felt.panel).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Turn ${t.turn}", color = felt.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(app.seatName(t.active) + (if (t.showdown != null) " · showdown" else ""), color = felt.dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        val logState = rememberLazyListState()
        LaunchedEffect(t.log.size) { if (t.log.isNotEmpty()) logState.scrollToItem(t.log.lastIndex) }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).background(felt.panel2).padding(8.dp), state = logState,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(t.log) { e ->
                val colors = e.side?.let { seatOf(t, it) } ?: (felt.dim to felt.dim)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).height(18.dp).background(Brush.verticalGradient(listOf(colors.first, colors.second))))
                    Text(e.text, Modifier.padding(start = 8.dp), color = felt.text, fontSize = 13.sp)
                }
            }
        }
        ChainPanel(app, t, ui, dims, viewer)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(C.Accent))
            Text(if (t.focus == viewer) "Your focus" else "${app.seatName(t.focus)}'s focus", color = felt.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        val starting = t.phase == Phase.AWAKEN
        PanelButton(if (starting) "Start turn" else "Pass", "SPACE", primary = true, Modifier.fillMaxWidth().testTag("primary")) {
            app.act(if (starting) Action.StartTurn else Action.Pass)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelButton("Undo", "R", false, Modifier.weight(1f).testTag("undo")) { app.undo() }
            PanelButton("Draw", "D", false, Modifier.weight(1f)) { app.act(Action.Draw(viewer)) }
            PanelButton("End turn", "E", false, Modifier.weight(1f).testTag("end-turn")) { app.act(Action.EndTurn) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelButton("Switch seat", null, false, Modifier.weight(1f)) { app.switchSeat() }
            PanelButton("Leave", null, false, Modifier.weight(1f)) { app.leaveTable() }
        }
    }
}

/** The chain: the top card large with Resolve and Target, the rest listed under it. Drop a card here to put it on the chain. */
@Composable
private fun ChainPanel(app: AppState, t: TableState, ui: TableUi, dims: Dims, viewer: Side) {
    val felt = LocalFelt.current
    val chain = t.cardsIn(Zone.CHAIN)
    DropZone(ui, Zone.CHAIN, Modifier.fillMaxWidth(), framed = false) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(felt.panel2).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CHAIN", color = felt.dim, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
                Text(if (chain.isEmpty()) "empty" else "${chain.size}", color = felt.dim, fontSize = 12.sp)
            }
            val topCard = chain.lastOrNull()
            if (topCard != null) {
                PanelButton("Resolve", "S", true, Modifier.fillMaxWidth().testTag("resolve")) { app.targeting = null; app.act(Action.Resolve) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val w = (dims.cardW * 1.5f).coerceAtMost(150.dp)
                    TableCard(app, t, ui, topCard, Zone.CHAIN, viewer, w, w * 1.4f, dims)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(Cards.bySlug(topCard.slug)?.name ?: "", color = felt.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(app.seatName(topCard.owner), color = felt.dim, fontSize = 12.sp)
                        PanelButton(if (app.targeting == topCard.id) "Cancel" else "Target", "T", false, Modifier.fillMaxWidth().testTag("target")) {
                            app.targeting = if (app.targeting == topCard.id) null else topCard.id
                        }
                        chain.dropLast(1).asReversed().forEach { c ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TableCard(app, t, ui, c, Zone.CHAIN, viewer, 28.dp, 39.dp, dims)
                                Text(Cards.bySlug(c.slug)?.name ?: "", color = felt.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelButton(text: String, key: String?, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val felt = LocalFelt.current
    val shape = RoundedCornerShape(7.dp)
    Column(
        modifier.clip(shape).background(if (primary) C.Accent else felt.zone).then(if (primary) Modifier else Modifier.border(1.dp, felt.edge, shape))
            .clickable(onClick = onClick).padding(vertical = if (primary) 11.dp else 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = if (primary) C.OnAccent else felt.text, fontSize = if (primary) 15.sp else 13.sp, fontWeight = FontWeight.Bold)
        if (key != null && !primary) Text(key, color = felt.dim, fontSize = 10.sp)
    }
}

// ---------- overlays ----------

@Composable
private fun CoverScreen(app: AppState, side: Side) {
    val felt = LocalFelt.current
    Box(Modifier.fillMaxSize().background(felt.felt).pointerInput(Unit) { detectTapGestures { } }.testTag("cover"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(app.seatName(side), color = felt.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            PanelButton("Continue", null, true, Modifier.width(220.dp).testTag("continue")) { app.uncover() }
        }
    }
}

@Composable
private fun WinBanner(app: AppState, side: Side) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        Column(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Card).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${app.seatName(side)} wins", color = C.Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PanelButton("Undo", null, false, Modifier.width(140.dp)) { app.undo() }
                PanelButton("New game", null, true, Modifier.width(140.dp)) { app.leaveTable() }
            }
        }
    }
}
