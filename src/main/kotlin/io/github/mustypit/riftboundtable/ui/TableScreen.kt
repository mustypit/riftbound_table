@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)

package io.github.mustypit.riftboundtable.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
private data class Menu(val id: Int?, val pile: Zone?, val at: Offset)

/** Screen-only state of the table: where zones and cards are, what's dragged, hovered or open. */
private class TableUi {
    val zoneBounds = HashMap<Zone, Rect>()
    val lastPos = HashMap<Int, Offset>()
    var drag by mutableStateOf<Drag?>(null)
    var menu by mutableStateOf<Menu?>(null)
    var hovered by mutableStateOf<Int?>(null)
    var browse by mutableStateOf<Zone?>(null)
    var dragOver by mutableStateOf<Zone?>(null)
    var rootSize by mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)

    fun zoneAt(p: Offset): Zone? = zoneBounds.entries.filter { it.value.contains(p) }
        .minByOrNull { it.value.width * it.value.height }?.key
}

/** Sizes, from Settings → card size. */
private class Dims(scale: Float) {
    val cardW = 66.dp * scale
    val cardH = cardW * 1.4f
    val boardH = cardH + 16.dp
    /** Runes sit in two rows of up to six; an exhausted rune turns sideways, so each slot is as wide as a rune is tall. */
    val runeH = (boardH - 12.dp - 3.dp) / 2
    val runeW = runeH / 1.4f
    val runeBoxW = (runeH + 3.dp) * 6 + 12.dp
    val handW = cardW * 1.5f
    val handH = handW * 1.4f
    val backW = cardW * 0.62f
    val backH = backW * 1.4f
}

/** Whether [viewer] may see the face of card [c] in zone [z]. */
fun visibleTo(c: CardInst, z: Zone, viewer: Side): Boolean = when {
    z.kind == ZoneKind.HAND -> z.side == viewer
    z.kind.hidden -> false
    c.faceDown -> c.owner == viewer
    else -> true
}

@Composable
fun TableScreen(app: AppState) {
    val t = app.table ?: return
    val ui = remember { TableUi() }
    val felt = LocalFelt.current
    val dims = Dims(app.settings.cardScale / 100f)
    val viewer = app.viewer
    val top = viewer.other
    Box(Modifier.fillMaxSize().background(felt.felt).onGloballyPositioned { ui.rootSize = it.size }.testTag("table")) {
        Row(Modifier.fillMaxSize()) {
            Board(app, t, ui, dims, viewer, top, Modifier.weight(1f).fillMaxHeight())
            SidePanel(app, t, ui, viewer, Modifier.width(300.dp).fillMaxHeight())
        }
        // The dragged card follows the pointer.
        ui.drag?.let { d ->
            val c = t.cards[d.id]
            if (c != null) {
                val z = t.zoneOf(d.id)
                val density = LocalDensity.current
                val tl = d.pos - d.grab
                Box(Modifier.offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }.graphicsLayer { shadowElevation = 24f; scaleX = 1.06f; scaleY = 1.06f }) {
                    CardFace(c, z != null && visibleTo(c, z, viewer), dims.cardW, dims.cardH)
                }
            }
        }
        ui.browse?.let { PileBrowser(app, t, ui, it, viewer, dims) }
        ui.menu?.let { m -> ContextMenu(app, t, ui, m, viewer) }
        t.winner()?.let { WinBanner(app, it) }
        app.cover?.let { CoverScreen(app, it) }
    }
}

/** A seat's colours from its legend on the table. */
private fun seatOf(t: TableState, side: Side): Pair<Color, Color> = seatColors(Cards.bySlug(t.cardsIn(Zone.of(ZoneKind.LEGEND, side)).firstOrNull()?.slug ?: ""))

// ---------- board ----------

@Composable
private fun Board(app: AppState, t: TableState, ui: TableUi, dims: Dims, viewer: Side, top: Side, modifier: Modifier) {
    Row(modifier.padding(vertical = 8.dp, horizontal = 6.dp)) {
        Column(Modifier.width(44.dp).fillMaxHeight()) {
            Spacer(Modifier.weight(1f))
            ScoreTrack(app, t, viewer, upwards = true, Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HandRow(app, t, ui, dims, top, viewer, isTop = true)
            BoardRow(app, t, ui, dims, top, viewer, isTop = true)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in t.battlefields.indices) BattlefieldView(app, t, ui, dims, i, viewer, top, Modifier.weight(1f).fillMaxHeight())
            }
            BoardRow(app, t, ui, dims, viewer, viewer, isTop = false)
            HandRow(app, t, ui, dims, viewer, viewer, isTop = false)
        }
        Column(Modifier.width(44.dp).fillMaxHeight()) {
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
    Column(modifier.fillMaxWidth().testTag("track-$side"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
        if (!upwards) Text(app.shortName(side), color = felt.dim, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        for (v in values) {
            val on = v == score
            Box(
                Modifier.size(30.dp, 22.dp).scale(if (on) pop.value else 1f).clip(RoundedCornerShape(7.dp))
                    .then(if (on) Modifier.background(Brush.linearGradient(listOf(c1, c2))) else Modifier.background(felt.zone).border(1.dp, felt.edge, RoundedCornerShape(7.dp)))
                    .clickable { app.act(Action.SetScore(side, v)) }.testTag("score-$side-$v"),
                contentAlignment = Alignment.Center,
            ) { Text("$v", color = if (on) Color.White else felt.dim, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        if (upwards) Text(app.shortName(side), color = felt.dim, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** Edge row: rune deck and floating Energy / Power on the left, the hand, then deck, trash and banishment. */
@Composable
private fun HandRow(app: AppState, t: TableState, ui: TableUi, dims: Dims, side: Side, viewer: Side, isTop: Boolean) {
    val hand = Zone.of(ZoneKind.HAND, side)
    val h = if (side == viewer) dims.handH + 18.dp else dims.backH + 8.dp
    Row(Modifier.fillMaxWidth().height(h), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pile(app, t, ui, dims, Zone.of(ZoneKind.RUNE_DECK, side), viewer, rune = true, small = isTop)
        PoolCounters(app, t, side)
        DropZone(ui, hand, Modifier.weight(1f).fillMaxHeight(), framed = false) {
            val cards = t.cardsIn(hand)
            if (side == viewer) {
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(-dims.handW * 0.14f)) {
                    cards.forEachIndexed { i, c ->
                        val mid = (cards.size - 1) / 2f
                        val lift = ui.hovered == c.id
                        Box(Modifier.graphicsLayer {
                            rotationZ = if (lift) 0f else (i - mid) * 3f
                            translationY = if (lift) -12.dp.toPx() else kotlin.math.abs(i - mid) * 2f * density
                        }) { TableCard(app, t, ui, c, hand, viewer, dims.handW, dims.handH) }
                    }
                }
            } else {
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(-dims.backW * 0.08f)) {
                    cards.forEach { c -> TableCard(app, t, ui, c, hand, viewer, dims.backW, dims.backH) }
                }
            }
        }
        Pile(app, t, ui, dims, Zone.of(ZoneKind.DECK, side), viewer, small = isTop)
        Pile(app, t, ui, dims, Zone.of(ZoneKind.TRASH, side), viewer, small = isTop)
        Pile(app, t, ui, dims, Zone.of(ZoneKind.BANISH, side), viewer, small = true, hideWhenEmpty = true)
    }
}

/** Board row: the rune box on the left, the base to its right, then champion and legend (RiftAtlas order). */
@Composable
private fun BoardRow(app: AppState, t: TableState, ui: TableUi, dims: Dims, side: Side, viewer: Side, isTop: Boolean) {
    val felt = LocalFelt.current
    Row(Modifier.fillMaxWidth().height(dims.boardH), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val legend = Zone.of(ZoneKind.LEGEND, side); val champ = Zone.of(ZoneKind.CHAMPION, side); val base = Zone.of(ZoneKind.BASE, side)
        val runes = Zone.of(ZoneKind.RUNES, side)
        @Composable fun slot(z: Zone) = DropZone(ui, z, Modifier.size(dims.cardW + 8.dp, dims.cardH + 8.dp), framed = t.ids(z).isEmpty(), dashed = true) {
            t.cardsIn(z).firstOrNull()?.let { Box(Modifier.align(Alignment.Center)) { TableCard(app, t, ui, it, z, viewer, dims.cardW, dims.cardH) } }
        }
        DropZone(ui, runes, Modifier.width(dims.runeBoxW).fillMaxHeight()) {
            val all = t.cardsIn(runes)
            FlowRow(Modifier.align(Alignment.CenterStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp), maxItemsInEachRow = 6) {
                all.forEach { TableCard(app, t, ui, it, runes, viewer, dims.runeW, dims.runeH) }
            }
            if (all.isNotEmpty()) Text("${all.count { !it.exhausted }}/${all.size}", Modifier.align(Alignment.TopEnd).padding(4.dp), color = felt.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        DropZone(ui, base, Modifier.weight(1f).fillMaxHeight()) { CardRow(app, t, ui, base, viewer, dims, Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp)) }
        if (isTop) { slot(legend); slot(champ) } else { slot(champ); slot(legend) }
    }
}

@Composable
private fun PoolCounters(app: AppState, t: TableState, side: Side) {
    val felt = LocalFelt.current
    val pool = t.pools[side] ?: Pool()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((label, value) in listOf("Energy" to pool.energy, "Power" to pool.power)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, color = felt.dim, fontSize = 11.sp, modifier = Modifier.width(44.dp))
                MiniButton("−") { app.act(Action.SetPool(side, if (label == "Energy") pool.copy(energy = pool.energy - 1) else pool.copy(power = pool.power - 1))) }
                Text("$value", color = felt.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                MiniButton("+") { app.act(Action.SetPool(side, if (label == "Energy") pool.copy(energy = pool.energy + 1) else pool.copy(power = pool.power + 1))) }
            }
        }
    }
}

@Composable
private fun MiniButton(text: String, onClick: () -> Unit) {
    val felt = LocalFelt.current
    Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(felt.zone).border(1.dp, felt.edge, RoundedCornerShape(4.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = felt.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** A battlefield: its art across the whole zone, the name band in the middle, each side's units above and below. */
@Composable
private fun BattlefieldView(app: AppState, t: TableState, ui: TableUi, dims: Dims, i: Int, viewer: Side, top: Side, modifier: Modifier) {
    val felt = LocalFelt.current
    val sd = t.showdown == i
    val shape = RoundedCornerShape(10.dp)
    val slug = t.battlefields[i]
    Box(modifier.clip(shape).background(Color(0xFF191A1C)).border(if (sd) 2.dp else 1.dp, if (sd) C.Accent else felt.edge, shape).testTag("bf-$i")) {
        Images.battlefieldArt(slug)?.let { Image(it, null, Modifier.matchParentSize().alpha(0.34f), contentScale = ContentScale.Crop) }
        Column(Modifier.fillMaxSize()) {
            DropZone(ui, Zone.bf(top, i), Modifier.weight(1f).fillMaxWidth(), framed = false) { CardRow(app, t, ui, Zone.bf(top, i), viewer, dims, Modifier.align(Alignment.Center)) }
            Row(Modifier.fillMaxWidth().height(30.dp).background(Color.Black.copy(alpha = 0.55f))
                .onPointerEvent(PointerEventType.Enter) { ui.hovered = -1 - i }.onPointerEvent(PointerEventType.Exit) { if (ui.hovered == -1 - i) ui.hovered = null }
                .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Clicking the name opens or closes a showdown here.
                Text(Cards.bySlug(slug)?.name ?: slug, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).clickable { app.act(Action.Showdown(if (sd) null else i)) }.testTag("showdown-$i"))
                if (sd) Text("SHOWDOWN", Modifier.clip(RoundedCornerShape(4.dp)).background(C.Accent).clickable { app.act(Action.Showdown(null)) }.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = C.OnAccent, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                Spacer(Modifier.weight(1f))
                // Control: click cycles nobody → bottom seat → top seat.
                val ctl = t.control.getOrNull(i)
                val ctlColors = ctl?.let { seatOf(t, it) }
                Text(ctl?.let { app.shortName(it) } ?: "—", Modifier.clip(RoundedCornerShape(4.dp))
                    .background(if (ctlColors != null) Brush.linearGradient(listOf(ctlColors.first, ctlColors.second)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))))
                    .clickable { app.act(Action.Control(i, when (ctl) { null -> viewer; viewer -> top; else -> null })) }
                    .padding(horizontal = 8.dp, vertical = 2.dp).testTag("control-$i"),
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
            }
            DropZone(ui, Zone.bf(viewer, i), Modifier.weight(1f).fillMaxWidth(), framed = false) { CardRow(app, t, ui, Zone.bf(viewer, i), viewer, dims, Modifier.align(Alignment.Center)) }
        }
    }
}

@Composable
private fun CardRow(app: AppState, t: TableState, ui: TableUi, z: Zone, viewer: Side, dims: Dims, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        t.cardsIn(z).forEach { TableCard(app, t, ui, it, z, viewer, dims.cardW, dims.cardH) }
    }
}

/** A zone that takes dropped cards; highlights while a card is dragged over it. */
@Composable
private fun DropZone(ui: TableUi, zone: Zone, modifier: Modifier, framed: Boolean = true, dashed: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val felt = LocalFelt.current
    val over = ui.drag != null && ui.dragOver == zone
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.onGloballyPositioned { ui.zoneBounds[zone] = it.boundsInRoot() }
            .then(if (framed) Modifier.background(if (dashed) Color.Transparent else felt.zone, shape).border(1.dp, felt.edge.copy(alpha = if (dashed) 0.6f else 1f), shape) else Modifier)
            .then(if (over) Modifier.border(2.dp, C.Accent, shape) else Modifier)
            .testTag("zone-${zone.kind}-${zone.side}${if (zone.bf >= 0) "-${zone.bf}" else ""}"),
        content = content,
    )
}

// ---------- cards ----------

@Composable
fun CardFace(c: CardInst, visible: Boolean, w: Dp, h: Dp, modifier: Modifier = Modifier) {
    val img = if (visible) Images.card(c.slug) else Images.back
    val shape = RoundedCornerShape(w * 0.07f)
    Box(modifier.size(w, h).clip(shape).background(Color(0xFF14161A))
        .then(if (c.faceDown) Modifier.border(2.dp, Color(0xFF9C9EA3), shape) else Modifier)) {
        img?.let { Image(it, null, Modifier.fillMaxSize().alpha(if (visible && c.faceDown) 0.55f else 1f), contentScale = ContentScale.Crop) }
        if (visible && img == null) Text(Cards.bySlug(c.slug)?.name ?: c.slug, Modifier.padding(4.dp), color = Color.White, fontSize = 9.sp)
    }
}

@Composable
private fun TableCard(app: AppState, t: TableState, ui: TableUi, c: CardInst, z: Zone, viewer: Side, w: Dp, h: Dp) {
    val scope = rememberCoroutineScope()
    val visible = visibleTo(c, z, viewer)
    val slide = remember(c.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val rot by animateFloatAsState(if (c.exhausted) 90f else 0f, tween(140))
    val dragging = ui.drag?.id == c.id
    val slotW = if (c.exhausted) h else w
    val slotH = if (c.exhausted) w else h
    Box(
        Modifier.size(slotW, slotH)
            .onGloballyPositioned { lc ->
                // Slide in from wherever the card was drawn last (FLIP).
                val p = lc.positionInRoot()
                val old = ui.lastPos[c.id]
                ui.lastPos[c.id] = p
                if (old != null && (old - p).getDistance() > 2f && !dragging) scope.launch { slide.snapTo(old - p + slide.value); slide.animateTo(Offset.Zero, tween(200)) }
            }
            .graphicsLayer { translationX = slide.value.x; translationY = slide.value.y; alpha = if (dragging) 0.25f else 1f }
            .onPointerEvent(PointerEventType.Enter) { ui.hovered = c.id }
            .onPointerEvent(PointerEventType.Exit) { if (ui.hovered == c.id) ui.hovered = null }
            .onPointerEvent(PointerEventType.Press) { e ->
                if (e.buttons.isSecondaryPressed) ui.menu = Menu(c.id, null, (ui.lastPos[c.id] ?: Offset.Zero) + e.changes.first().position)
            }
            .pointerInput(c.id, z) {
                detectTapGestures(onTap = {
                    when (z.kind) {
                        ZoneKind.BASE, ZoneKind.BATTLEFIELD, ZoneKind.RUNES, ZoneKind.LEGEND, ZoneKind.CHAMPION -> app.act(Action.Exhaust(c.id, !c.exhausted))
                        else -> ui.menu = Menu(c.id, null, (ui.lastPos[c.id] ?: Offset.Zero) + it)
                    }
                })
            }
            .pointerInput(c.id, z) {
                detectDragGestures(
                    onDragStart = { o -> ui.menu = null; ui.drag = Drag(c.id, (ui.lastPos[c.id] ?: Offset.Zero) + o, o) },
                    onDrag = { change, amount -> change.consume(); ui.drag?.let { d -> val np = d.pos + amount; ui.drag = d.copy(pos = np); ui.dragOver = ui.zoneAt(np) } },
                    onDragEnd = { drop(app, t, ui) },
                    onDragCancel = { ui.drag = null; ui.dragOver = null },
                )
            }
            .testTag("card-${c.id}"),
        contentAlignment = Alignment.Center,
    ) {
        CardFace(c, visible, w, h, Modifier.requiredSize(w, h).rotate(rot))
        Badges(c, Modifier.matchParentSize())
    }
}

@Composable
private fun Badges(c: CardInst, modifier: Modifier) {
    Box(modifier) {
        if (c.damage > 0) Badge("${c.damage}", C.Red, Color.White, Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp))
        val might = (if (c.buff) 1 else 0) + c.mightMod
        if (c.buff || c.mightMod != 0) Badge(if (might >= 0) "+$might" else "$might", C.Accent, C.OnAccent, Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp))
        if (c.xp > 0) Badge("${c.xp} XP", Color(0xFF3B8BEB), Color.White, Modifier.align(Alignment.BottomStart).offset((-4).dp, 4.dp))
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color, modifier: Modifier) =
    Text(text, modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 4.dp, vertical = 1.dp), color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)

private fun drop(app: AppState, t: TableState, ui: TableUi) {
    val d = ui.drag ?: return
    ui.drag = null; ui.dragOver = null
    val to = ui.zoneAt(d.pos) ?: return
    val from = t.zoneOf(d.id) ?: return
    if (to == from) return
    ui.lastPos[d.id] = d.pos - d.grab
    val index = when (to.kind) { ZoneKind.DECK, ZoneKind.TRASH, ZoneKind.BANISH -> 0; else -> null }
    app.act(Action.Move(d.id, to, index))
}

// ---------- piles ----------

@Composable
private fun Pile(app: AppState, t: TableState, ui: TableUi, dims: Dims, z: Zone, viewer: Side, small: Boolean = false, rune: Boolean = false, hideWhenEmpty: Boolean = false) {
    val felt = LocalFelt.current
    val ids = t.ids(z)
    if (hideWhenEmpty && ids.isEmpty()) { DropZone(ui, z, Modifier.size(1.dp), framed = false) {}; return }
    val w = if (small || rune) dims.cardW * 0.72f else dims.cardW
    val h = w * 1.4f
    var pos by remember { mutableStateOf(Offset.Zero) }
    SideEffect { ids.forEach { ui.lastPos[it] = pos } }
    DropZone(ui, z, Modifier.size(w + 8.dp, h + 8.dp).onGloballyPositioned { pos = it.positionInRoot() }, framed = false) {
        val topCard = ids.firstOrNull()?.let { t.card(it) }
        Box(Modifier.align(Alignment.Center).size(w, h)
            .onPointerEvent(PointerEventType.Press) { e -> if (e.buttons.isSecondaryPressed) ui.menu = Menu(null, z, pos + e.changes.first().position) }
            .onPointerEvent(PointerEventType.Enter) { if (topCard != null && !z.kind.hidden) ui.hovered = topCard.id }
            .clickable {
                when (z.kind) {
                    ZoneKind.DECK -> app.act(Action.Draw(z.side))
                    ZoneKind.RUNE_DECK -> app.act(Action.Channel(z.side, 1))
                    else -> ui.browse = z
                }
            }.testTag("pile-${z.kind}-${z.side}")) {
            if (topCard == null) {
                Box(Modifier.fillMaxSize().border(1.dp, felt.edge, RoundedCornerShape(5.dp)))
            } else {
                CardFace(topCard, !z.kind.hidden, w, h)
            }
            Text("${ids.size}", Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp).clip(RoundedCornerShape(6.dp)).background(felt.panel2)
                .border(1.dp, felt.edge, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp), color = felt.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PileBrowser(app: AppState, t: TableState, ui: TableUi, z: Zone, viewer: Side, dims: Dims) {
    val felt = LocalFelt.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).pointerInput(Unit) { detectTapGestures { ui.browse = null } }) {
        Column(Modifier.align(Alignment.Center).widthIn(max = 900.dp).clip(RoundedCornerShape(10.dp)).background(felt.panel)
            .pointerInput(Unit) { detectTapGestures { } }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val title = when (z.kind) { ZoneKind.TRASH -> "Trash"; ZoneKind.DECK -> "Deck"; ZoneKind.BANISH -> "Banishment"; ZoneKind.RUNE_DECK -> "Rune deck"; else -> "" }
            Text("$title · ${app.seatName(z.side)}", color = felt.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            val cards = t.cardsIn(z)
            if (cards.isEmpty()) Text("Empty", color = felt.dim)
            FlowRow(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.forEach { c ->
                    var at by remember { mutableStateOf(Offset.Zero) }
                    CardFace(c, true, dims.cardW * 1.3f, dims.cardH * 1.3f, Modifier.onGloballyPositioned { at = it.positionInRoot() }
                        .onPointerEvent(PointerEventType.Enter) { ui.hovered = c.id }
                        .pointerInput(c.id) { detectTapGestures { o -> ui.menu = Menu(c.id, null, at + o) } })
                }
            }
        }
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
    if (onBoard) items += (if (c.exhausted) "Ready" else "Exhaust") to { app.act(Action.Exhaust(id, !c.exhausted)) }
    if (z.kind == ZoneKind.BASE || z.kind == ZoneKind.BATTLEFIELD) {
        items += "Damage +1" to { app.act(Action.Damage(id, 1)) }
        if (c.damage > 0) items += "Damage −1" to { app.act(Action.Damage(id, -1)) }
        items += (if (c.buff) "Remove buff" else "Buff") to { app.act(Action.Buff(id, !c.buff)) }
        items += "Might +1 this turn" to { app.act(Action.Might(id, 1)) }
        items += "Might −1 this turn" to { app.act(Action.Might(id, -1)) }
        items += "XP +1" to { app.act(Action.Xp(id, 1)) }
        if (c.xp > 0) items += "XP −1" to { app.act(Action.Xp(id, -1)) }
    }
    if (z.kind == ZoneKind.BATTLEFIELD) items += (if (c.faceDown) "Reveal" else "Face down") to { app.act(Action.FaceDown(id, !c.faceDown)) }
    if (z.kind == ZoneKind.RUNES) items += "Recycle" to { app.act(Action.Recycle(id)) }
    if (z.kind == ZoneKind.HAND) {
        items += "Play to base" to { move(Zone.of(ZoneKind.BASE, owner), null) }
        for (i in t.battlefields.indices) items += "Hide at ${bfName(i)}" to { app.act(Action.FaceDown(id, true)); move(Zone.bf(owner, i), null) }
        if ((t.turnsTaken[owner] ?: 0) == 0) items += "Mulligan" to { app.act(Action.Mulligan(id)) }
    }
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

@Composable
private fun ContextMenu(app: AppState, t: TableState, ui: TableUi, m: Menu, viewer: Side) {
    val felt = LocalFelt.current
    val items = when {
        m.id != null && t.cards.containsKey(m.id) -> cardMenu(app, t, m.id, viewer)
        m.pile != null -> pileMenu(app, t, ui, m.pile, viewer)
        else -> emptyList()
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { ui.menu = null } }.onPointerEvent(PointerEventType.Press) { if (it.buttons.isSecondaryPressed) ui.menu = null }) {
        val density = LocalDensity.current
        val menuH = with(density) { (items.size * 31 + 10).dp.toPx() }
        val menuW = with(density) { 180.dp.toPx() }
        val x = m.at.x.coerceAtMost(ui.rootSize.width - menuW - 8f).coerceAtLeast(0f)
        val y = m.at.y.coerceAtMost(ui.rootSize.height - menuH - 8f).coerceAtLeast(0f)
        Column(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(180.dp).clip(RoundedCornerShape(8.dp)).background(felt.panel)
            .border(1.dp, felt.edge, RoundedCornerShape(8.dp)).padding(4.dp).testTag("menu")) {
            items.forEach { (label, act) ->
                Text(label, Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).clickable { ui.menu = null; act() }.height(31.dp).padding(horizontal = 10.dp, vertical = 6.dp).testTag("menu-$label"),
                    color = felt.text, fontSize = 13.sp)
            }
        }
    }
}

// ---------- side panel ----------

@Composable
private fun SidePanel(app: AppState, t: TableState, ui: TableUi, viewer: Side, modifier: Modifier) {
    val felt = LocalFelt.current
    Column(modifier.background(felt.panel).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Turn ${t.turn}", color = felt.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(app.seatName(t.active) + (if (t.showdown != null) " · showdown" else ""), color = felt.dim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        val logState = rememberLazyListState()
        LaunchedEffect(t.log.size) { if (t.log.isNotEmpty()) logState.scrollToItem(t.log.lastIndex) }
        LazyColumn(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(6.dp)).background(felt.panel2).padding(6.dp), state = logState,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(t.log) { e ->
                val colors = e.side?.let { seatOf(t, it) } ?: (felt.dim to felt.dim)
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.width(3.dp).height(16.dp).background(Brush.verticalGradient(listOf(colors.first, colors.second))))
                    Text(e.text, Modifier.padding(start = 6.dp), color = felt.text, fontSize = 12.sp)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(C.Accent))
            Text(if (t.focus == viewer) "Your focus" else "${app.seatName(t.focus)}'s focus", color = felt.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        val starting = t.phase == Phase.AWAKEN
        PanelButton(if (starting) "Start turn" else "Pass", if (starting) "N" else "SPACE", primary = true, Modifier.fillMaxWidth().testTag("primary")) {
            app.act(if (starting) Action.StartTurn else Action.Pass)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelButton("Undo", "⌘Z", false, Modifier.weight(1f)) { app.undo() }
            PanelButton("Draw", "D", false, Modifier.weight(1f)) { app.act(Action.Draw(viewer)) }
            PanelButton("End turn", "E", false, Modifier.weight(1f).testTag("end-turn")) { app.act(Action.EndTurn) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val h = ui.hovered
            val img = when {
                h == null -> null
                h < 0 -> t.battlefields.getOrNull(-1 - h)?.let { Images.card(it) }
                else -> t.cards[h]?.let { c -> t.zoneOf(h)?.let { z -> if (visibleTo(c, z, viewer)) Images.card(c.slug) else null } }
            }
            img?.let { Image(it, null, Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelButton("Switch seat", null, false, Modifier.weight(1f)) { app.switchSeat() }
            PanelButton("Leave", null, false, Modifier.weight(1f)) { app.leaveTable() }
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
        Text(text, color = if (primary) C.OnAccent else felt.text, fontSize = if (primary) 15.sp else 12.sp, fontWeight = FontWeight.Bold)
        if (key != null && !primary) Text(key, color = felt.dim, fontSize = 9.sp)
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
