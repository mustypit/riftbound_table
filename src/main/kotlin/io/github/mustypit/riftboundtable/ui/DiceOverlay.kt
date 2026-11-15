package io.github.mustypit.riftboundtable.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mustypit.riftboundtable.game.D20
import io.github.mustypit.riftboundtable.game.Dice
import io.github.mustypit.riftboundtable.game.Side
import io.github.mustypit.riftboundtable.game.V3
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.TextLine
import kotlin.math.cos
import kotlin.math.sin

private const val DIE_RADIUS = 80f
private const val TIE_PAUSE = 500f
private const val WIN_FADE = 450f
private const val SPARKLE_TIME = 8000f

/**
 * Who goes first: each seat's d20 is thrown in from its edge (RiftTracker's dice). The numbers were
 * drawn before ([Dice.roll]); the throw only shows them. One click once they settle carries on.
 */
@Composable
fun DiceOverlay(roll: Dice.Roll, names: (Side) -> String, colors: (Side) -> Pair<Color, Color>, clockMs: Float? = null, onDone: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(C.Paper)) {
        val w = maxWidth.value
        val halfH = (maxHeight.value - 4f) / 2f
        val throws = remember(roll, w, halfH) {
            roll.throws.mapIndexed { i, (me, opp) ->
                D20.simulate(kotlin.random.Random(roll.seed + i * 2), w, halfH, DIE_RADIUS, me) to
                    D20.simulate(kotlin.random.Random(roll.seed + i * 2 + 1), w, halfH, DIE_RADIUS, opp)
            }
        }
        val starts = remember(throws) { throws.runningFold(0f) { acc, (a, b) -> acc + maxOf(a.durationMs, b.durationMs) + TIE_PAUSE }.dropLast(1) }
        val end = starts.last() + maxOf(throws.last().first.durationMs, throws.last().second.durationMs)
        val t = remember(roll) { Animatable(0f) }
        LaunchedEffect(roll, clockMs) { if (clockMs == null) t.animateTo(end + SPARKLE_TIME, tween((end + SPARKLE_TIME).toInt(), easing = LinearEasing)) }
        val ms = clockMs ?: t.value
        val settled = ms >= end
        val i = starts.indexOfLast { it <= ms }.coerceAtLeast(0)
        val local = ms - starts[i]
        val emph = if (settled) FastOutSlowInEasing.transform(((ms - end) / WIN_FADE).coerceIn(0f, 1f)) else 0f
        Column(
            Modifier.fillMaxSize().testTag("dice")
                .clickable(enabled = settled, indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDone),
        ) {
            for (side in listOf(Side.OPP, Side.ME)) {
                val track = if (side == Side.ME) throws[i].first else throws[i].second
                val win = settled && roll.winner == side
                val (c1, c2) = colors(side)
                Box(Modifier.weight(1f).fillMaxWidth().let { if (side == Side.OPP) it.rotate(180f) else it }) {
                    if (win) {
                        val l1 = lerp(c1, Color.White, 0.35f); val l2 = lerp(c2, Color.White, 0.35f)
                        Box(Modifier.matchParentSize().graphicsLayer { alpha = emph }.background(Brush.linearGradient(listOf(l1, l2))))
                        Box(Modifier.matchParentSize().graphicsLayer { alpha = emph }.background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent))))
                        Sparkles(roll, ms - end, listOf(Color.White, l1, l2, Color.White), Modifier.matchParentSize().graphicsLayer { alpha = emph })
                    }
                    Canvas(Modifier.matchParentSize()) {
                        drawD20(track.at(local), DIE_RADIUS, c1, c2, alpha = if (settled && !win) 1f - 0.5f * emph else 1f, scale = if (win) 1f + 0.15f * emph else 1f)
                    }
                    Text(names(side), Modifier.align(Alignment.BottomStart).padding(20.dp), color = if (win) C.inkOn(c1) else C.Muted,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                if (side == Side.OPP) Spacer(Modifier.height(4.dp).fillMaxWidth().background(C.Black))
            }
        }
    }
}

private val light = V3(-0.35, -0.55, 1.0).unit()
private val dieFont by lazy { Font(FontMgr.default.matchFamilyStyle(null, FontStyle.BOLD), 104f) }
private val dieText = Paint().apply { isAntiAlias = true }
private val dieOutline = Paint().apply { isAntiAlias = true; mode = PaintMode.STROKE; strokeWidth = 14f; strokeJoin = PaintStrokeJoin.ROUND }

/** A d20 in perspective from above: lit faces nearest last, numbers on the faces that look up, a soft shadow. */
private fun DrawScope.drawD20(f: D20.Frame, radiusDp: Float, c1: Color, c2: Color, alpha: Float, scale: Float) {
    val r = (radiusDp * density * scale).toDouble()
    val camX = size.width / 2.0; val camY = size.height / 2.0
    val camZ = 900.0 * density
    fun project(p: V3): Offset { val k = camZ / (camZ - p.z); return Offset((camX + (p.x - camX) * k).toFloat(), (camY + (p.y - camY) * k).toFloat()) }
    val lift = f.z * density
    val center = V3(f.x * density.toDouble(), f.y * density.toDouble(), r * D20.inradius + lift)
    val sh = project(V3(center.x + lift * 0.3, center.y + lift * 0.5, 0.0))
    val shR = (r * (1.0 + lift / (220.0 * density))).toFloat()
    drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = (0.55f * alpha / (1f + f.z / 90f))), Color.Transparent), center = sh, radius = shR), radius = shR, center = sh)
    val verts = D20.vertices.map { f.q.rotate(it) * r + center }
    val camera = V3(camX, camY, camZ)
    val visible = D20.faces.indices.mapNotNull { i ->
        val n = f.q.rotate(D20.normals[i])
        val c = D20.faces[i].map { verts[it] }.reduce(V3::plus) * (1.0 / 3)
        if ((n dot (camera - c)) <= 0) null else Triple(i, n, c)
    }.sortedBy { it.third.z }
    val edge = Color.White.copy(alpha = 0.35f * alpha)
    for ((i, n, c) in visible) {
        val base = lerp(c1, c2, ((D20.normals[i].x + D20.normals[i].y * 0.5 + 1.5) / 3.0).toFloat().coerceIn(0f, 1f))
        val pts = D20.faces[i].map { project(verts[it]) }
        val path = Path().apply { moveTo(pts[0].x, pts[0].y); lineTo(pts[1].x, pts[1].y); lineTo(pts[2].x, pts[2].y); close() }
        val diffuse = (n dot light).coerceAtLeast(0.0)
        val half = (light + V3(0.0, 0.0, 1.0)).unit()
        val spec = Math.pow((n dot half).coerceAtLeast(0.0), 40.0)
        val shade = (0.45 + 0.55 * diffuse).toFloat()
        val fill = lerp(lerp(Color.Black, base, shade), Color.White, (0.55 * spec).toFloat())
        drawPath(path, fill, alpha = alpha)
        drawPath(path, edge, style = Stroke(1.2f * density, join = StrokeJoin.Round))
        if (n.z > 0.2) {
            val up = (verts[D20.faces[i][0]] - c).unit()
            var right = (n cross up).unit()
            val k = r * 0.25
            val p0 = project(c); var pr = project(c + right * k); val pu = project(c + up * k)
            if ((pr.x - p0.x) * (pu.y - p0.y) - (pr.y - p0.y) * (pu.x - p0.x) > 0) { right = right * -1.0; pr = project(c + right * k) }
            // Maps text space (x right, y down, 100 units) onto the face: (100,0) -> pr, (0,-100) -> pu.
            val m = Matrix33((pr.x - p0.x) / 100f, (p0.x - pu.x) / 100f, p0.x, (pr.y - p0.y) / 100f, (p0.y - pu.y) / 100f, p0.y, 0f, 0f, 1f)
            val num = D20.numbers[i]
            val label = if (num == 6 || num == 9) "$num." else "$num"
            val line = TextLine.make(label, dieFont)
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.save(); nc.concat(m)
                val a = (255 * alpha * (0.75f + 0.25f * ((n.z - 0.2) / 0.8).toFloat().coerceIn(0f, 1f))).toInt()
                dieOutline.color = org.jetbrains.skia.Color.makeARGB((a * 0.75f).toInt(), 0, 0, 0)
                nc.drawTextLine(line, -line.width / 2f, 36f, dieOutline)
                dieText.color = org.jetbrains.skia.Color.makeARGB(a, 255, 255, 255)
                nc.drawTextLine(line, -line.width / 2f, 36f, dieText)
                nc.restore()
            }
        }
    }
}

private data class Star(val x: Float, val y: Float, val size: Float, val phase: Float, val speed: Float, val color: Int)

@Composable
private fun Sparkles(roll: Dice.Roll, ms: Float, colors: List<Color>, modifier: Modifier) {
    val stars = remember(roll) {
        val r = kotlin.random.Random(roll.seed)
        List(50) { Star(r.nextFloat(), r.nextFloat(), 3f + r.nextFloat() * 8f, r.nextFloat() * 6.28f, 0.02f + r.nextFloat() * 0.05f, r.nextInt(colors.size)) }
    }
    Canvas(modifier) {
        val s = ms.coerceAtLeast(0f) / 1000f
        stars.forEach { st ->
            val yy = ((st.y - s * st.speed) % 1f + 1f) % 1f
            val twinkle = 0.35f + 0.65f * ((sin(st.phase + s * 4f) + 1f) / 2f)
            val rad = st.size * density * (0.7f + 0.3f * twinkle)
            val cx = st.x * size.width; val cy = yy * size.height
            val path = Path()
            for (k in 0 until 10) {
                val a = -Math.PI / 2 + k * Math.PI / 5
                val rr = if (k % 2 == 0) rad else rad * 0.42f
                val px = cx + (rr * cos(a)).toFloat(); val py = cy + (rr * sin(a)).toFloat()
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, colors[st.color].copy(alpha = twinkle))
        }
    }
}
