package dev.kris.clyde.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kris.clyde.ui.reduceMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Clawd — our own license-clean 8-bit pixel crab, drawn natively in Compose and fully *rigged*:
 * the body squashes & stretches, the claws open/close on stubby articulated arms, and he can wear
 * costumes, hold props, react to the environment, and throw off effects. No image assets, no GIF —
 * he's code, so he ships in every build and can never go missing. One rig composes every scene.
 */
enum class ClawdState(val blue: Boolean, val periodMs: Int) {
    Idle(true, 3200), // a calm, slow breath
    Listening(true, 1500),
    Working(true, 900),
    Navigating(true, 1100),
    Success(true, 1300),
    Error(false, 700),
}

@Composable
fun ClawdView(
    state: ClawdState,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    // null = decorative mascot (kept out of the TalkBack tree); the panel's text carries the meaning.
    contentDescription: String? = null,
) {
    val skin = if (state.blue) ClawdSkin.Blue else ClawdSkin.Terracotta
    val base = modifier
        .size(size)
        .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)

    if (reduceMotion()) {
        // a calm, readable rest frame — no infinite transition ticking.
        Canvas(base) { drawClawd(state.pose(if (state == ClawdState.Success) 0.55f else 0f), skin) }
        return
    }
    val t = rememberInfiniteTransition(label = "clawd")
    val p by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(state.periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "clawd-phase",
    )
    Canvas(base) { drawClawd(state.pose(p), skin) }
}

// ─────────────────────────── model ───────────────────────────

enum class ClawdEye { Dot, Wide, Happy, X, Closed, Half, Squint, Side, Star, Heart, Wink, Spiral }
enum class ClawdMouth { None, Flat, Grin, Frown, Open, Tongue }
enum class ClawdBrow { None, Up, V, Level }
enum class ClawdAccessory { HardHat, Headphones }
enum class ClawdProp { Wrench }
enum class ClawdEnv { Bolt }
enum class ClawdEffect { Bang, BangWarn, Waves, Dots, Sparkle, Notes, Zzz }

/** A single rendered pose. Motions are pure functions of phase → Pose; scenes compose these parts. */
data class Pose(
    val sx: Float = 1f, val sy: Float = 1f, val tx: Float = 0f, val ty: Float = 0f,
    val eye: ClawdEye = ClawdEye.Dot, val mouth: ClawdMouth = ClawdMouth.None, val brow: ClawdBrow = ClawdBrow.None,
    val openL: Float = 0f, val openR: Float = 0f, val raiseL: Float = 0f, val raiseR: Float = 0f,
    val accessory: ClawdAccessory? = null,
    val prop: ClawdProp? = null, val propAng: Float = -0.6f,
    val env: ClawdEnv? = null,
    val effect: ClawdEffect? = null,
    val p: Float = 0f,
)

data class ClawdSkin(val o: Color, val s: Color, val w: Color, val e: Color, val outline: Color) {
    companion object {
        val Blue = ClawdSkin(Color(0xFF56C1DE), Color(0xFF2E89A6), Color(0xFFFFFFFF), Color(0xFF0E2A33), Color(0xFF236E86))
        val Terracotta = ClawdSkin(Color(0xFFD97757), Color(0xFFBE5D3E), Color(0xFFFFFFFF), Color(0xFF241F1C), Color(0xFF8A4B33))
    }
}

// ─────────────────────────── art data ───────────────────────────

private const val ORX = 2.5f
private const val ORY = 5f
private const val VIEW = 22f      // logical drawing field is VIEW×VIEW cells (room for raised claws + hop)
private const val PIVX = 9.5f     // squash/stretch pivot: body center-x …
private const val PIVY = 14f      // … and feet (so squash keeps him planted)
private val TAU = (PI * 2).toFloat()

private val CLAWD_GRID = listOf(
    "                    ", "  oo            oo  ", " oooo          oooo ", " oooo          oooo ",
    " ooso  oooooo  osoo ", "  oo  oooooooo  oo  ", "  o  oooooooooo  o  ", "    owoooooooooo    ",
    "   ooooeooooeoooo   ", "   ooooeooooeoooo   ", "   oooooooooooooo   ", "   oooooooooooooo   ",
    "    ssoooooooooo    ", "    o o o  o o o    ", "   o   o    o   o   ", "                    ",
)
private class Cell(val x: Int, val y: Int, val c: Char)
// body = the v1 silhouette minus the static top claws and baked eyes (those are now rig parts)
private val CLAWD_BODY: List<Cell> = buildList {
    CLAWD_GRID.forEachIndexed { y, row ->
        for (x in row.indices) {
            val c = row[x]
            if (c == ' ' || c == 'e') continue
            if ((x <= 4 && y <= 6) || (x >= 15 && y <= 6)) continue
            add(Cell(x, y, c))
        }
    }
}

// ─────────────────────────── draw frame + helpers ───────────────────────────

private class RigFrame(val cell: Float, val ox: Float, val oy: Float) {
    fun x(g: Float) = ox + (ORX + g) * cell
    fun y(g: Float) = oy + (ORY + g) * cell
}
private fun txc(p: Pose, gx: Float) = PIVX + (gx - PIVX) * p.sx + p.tx
private fun tyc(p: Pose, gy: Float) = PIVY + (gy - PIVY) * p.sy + p.ty
private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k
private fun colp(sk: ClawdSkin, c: Char) = when (c) { 's' -> sk.s; 'w' -> sk.w; 'e' -> sk.e; else -> sk.o }

private fun DrawScope.odisc(x: Float, y: Float, r: Float, fill: Color, outline: Color, cell: Float) {
    drawCircle(outline, r + cell * 0.14f, Offset(x, y))
    drawCircle(fill, r, Offset(x, y))
}

fun DrawScope.drawClawd(pose: Pose, skin: ClawdSkin) {
    val cell = size.minDimension / VIEW
    val f = RigFrame(cell, (size.width - VIEW * cell) / 2f, (size.height - VIEW * cell) / 2f)

    if (pose.env == ClawdEnv.Bolt) bolt(f, skin)
    when (pose.effect) {                              // background effects (behind the crab)
        ClawdEffect.Waves -> waves(f, pose, skin)
        ClawdEffect.Notes -> notes(f, pose)
        ClawdEffect.Zzz -> zzz(f, pose)
        else -> {}
    }

    body(f, pose, skin)
    face(f, pose, skin)
    val mouthR = claw(f, pose, skin, 15f, 4.5f, +1f, pose.openR, pose.raiseR)
    claw(f, pose, skin, 4f, 4.5f, -1f, pose.openL, pose.raiseL)

    if (pose.prop == ClawdProp.Wrench) wrench(f, mouthR, pose.propAng)
    when (pose.accessory) {
        ClawdAccessory.HardHat -> hardHat(f, pose)
        ClawdAccessory.Headphones -> headphones(f, pose, skin)
        else -> {}
    }
    when (pose.effect) {                              // foreground effects (in front)
        ClawdEffect.Bang -> bang(f, pose, Color(0xFFD97757))
        ClawdEffect.BangWarn -> bang(f, pose, Color(0xFFBE5D3E))
        ClawdEffect.Dots -> dots(f, pose, skin)
        ClawdEffect.Sparkle -> sparkle(f, pose)
        else -> {}
    }
}

private fun DrawScope.body(f: RigFrame, p: Pose, sk: ClawdSkin) {
    val over = f.cell * 0.08f
    for (c in CLAWD_BODY) {
        val gx = txc(p, c.x.toFloat()); val gy = tyc(p, c.y.toFloat())
        drawRect(colp(sk, c.c), Offset(f.x(gx), f.y(gy)), Size(p.sx * f.cell + over, p.sy * f.cell + over))
    }
}

private fun DrawScope.face(f: RigFrame, p: Pose, sk: ClawdSkin) {
    val over = f.cell * 0.08f
    fun cell(gx: Float, gy: Float, c: Color, w: Float = 1f, h: Float = 1f) {
        drawRect(c, Offset(f.x(txc(p, gx)), f.y(tyc(p, gy))), Size(w * p.sx * f.cell + over, h * p.sy * f.cell + over))
    }
    // eyes — two slots (x=7, x=12); `sign` points toward the inner (nose) side of each eye.
    for ((slot, sign) in listOf(7 to 1, 12 to -1)) {
        val sx = slot.toFloat()
        when (p.eye) {
            ClawdEye.Dot -> cell(sx, 8f, sk.e, 1f, 2f)
            ClawdEye.Wide -> { cell(sx - 1, 7f, sk.w, 2f, 2f); cell(sx, 8f, sk.e) }
            ClawdEye.Half -> cell(sx, 9f, sk.e)
            ClawdEye.Squint -> cell(sx, 8f, sk.e)
            ClawdEye.Side -> { cell(sx + sign, 8f, sk.e); cell(sx - sign, 8f, sk.w) }
            ClawdEye.Happy -> { cell(sx - 1, 8f, sk.e); cell(sx, 7f, sk.e); cell(sx + 1, 8f, sk.e) }
            ClawdEye.Closed -> cell(sx - 1, 8f, sk.e, 3f, 1f)
            ClawdEye.Star -> listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0, 0 to 0).forEach { cell(sx + it.first, 8f + it.second, if (it.first == 0 && it.second == 0) sk.w else sk.e) }
            ClawdEye.Heart -> { cell(sx - 1, 7f, sk.e); cell(sx + 1, 7f, sk.e); cell(sx, 8f, sk.e); cell(sx, 7f, sk.w) }
            ClawdEye.Wink -> if (sign == 1) cell(sx, 8f, sk.e, 1f, 2f) else cell(sx - 1, 8f, sk.e, 3f, 1f)
            ClawdEye.Spiral -> listOf(0 to 0, 1 to 0, 1 to 1, -1 to 1, -1 to -1, 1 to -1).forEach { cell(sx + it.first, 8f + it.second, sk.e) }
            ClawdEye.X -> listOf(-1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1).forEach { cell(sx + it.first, 8f + it.second, sk.e) }
        }
    }
    when (p.mouth) {
        ClawdMouth.Flat -> cell(9f, 11f, sk.e, 2f, 1f)
        ClawdMouth.Grin -> { cell(8f, 11f, sk.e); cell(9f, 12f, sk.e); cell(10f, 12f, sk.e); cell(11f, 11f, sk.e) }
        ClawdMouth.Frown -> { cell(8f, 12f, sk.e); cell(9f, 11f, sk.e); cell(10f, 11f, sk.e); cell(11f, 12f, sk.e) }
        ClawdMouth.Open -> cell(9f, 11f, sk.e, 2f, 2f)
        ClawdMouth.Tongue -> { cell(9f, 11f, sk.e, 2f, 1f); cell(9f, 12f, Color(0xFFE27A8B), 2f, 1f); cell(9f, 13f, Color(0xFFC66A86)) }
        ClawdMouth.None -> {}
    }
    when (p.brow) {
        ClawdBrow.Up -> { cell(6f, 6f, sk.e); cell(8f, 5f, sk.e); cell(11f, 5f, sk.e); cell(13f, 6f, sk.e) }
        ClawdBrow.V -> { cell(6f, 5f, sk.e); cell(8f, 6f, sk.e); cell(11f, 6f, sk.e); cell(13f, 5f, sk.e) }
        ClawdBrow.Level -> { cell(6f, 6f, sk.e, 2f, 1f); cell(11f, 6f, sk.e, 2f, 1f) }
        ClawdBrow.None -> {}
    }
}

/** Articulated pincer: open 0=closed‥1=wide, raise lifts it; returns the "mouth" point for held props. */
private fun DrawScope.claw(f: RigFrame, p: Pose, sk: ClawdSkin, agx: Float, agy: Float, side: Float, open: Float, raise: Float): Offset {
    val sx = f.x(txc(p, if (side < 0) 6f else 13f)); val sy = f.y(tyc(p, 5f))
    // arc the claw OUT as it raises — limbs travel curved paths, never straight lines.
    val wx = f.x(txc(p, agx)) + side * raise * 0.18f * f.cell; val wy = f.y(tyc(p, agy)) - raise * f.cell
    drawLine(sk.s, Offset(sx, sy), Offset(wx, wy), f.cell * 0.55f, StrokeCap.Round)
    drawLine(sk.o, Offset(sx, sy), Offset(wx, wy), f.cell * 0.32f, StrokeCap.Round)
    val reach = side * 1.15f * f.cell
    odisc(wx, wy, f.cell * 0.7f, sk.o, sk.outline, f.cell)
    odisc(wx + reach, wy - (0.9f + open * 1.1f) * f.cell, f.cell * 0.62f, sk.o, sk.outline, f.cell)
    odisc(wx + reach, wy + (0.9f + open * 1.1f) * f.cell, f.cell * 0.62f, sk.o, sk.outline, f.cell)
    return Offset(wx + reach, wy)
}

// ─────────── costumes / props / environment / effects ───────────

private fun DrawScope.hardHat(f: RigFrame, p: Pose) {
    val cx = f.x(txc(p, 9.5f)); val cy = f.y(tyc(p, 3.2f)); val cell = f.cell
    drawCircle(Color(0xFFC8862A), cell * 3.1f, Offset(cx, cy + cell * 0.4f))
    drawCircle(Color(0xFFF2B33D), cell * 3.0f, Offset(cx, cy))
    drawRect(Color(0xFFF2B33D), Offset(cx - cell * 3.6f, cy + cell * 0.4f), Size(cell * 7.2f, cell * 0.8f))
    drawRect(Color(0xFFC8862A), Offset(cx - cell * 0.4f, cy - cell * 2.8f), Size(cell * 0.8f, cell * 1.6f))
}
private fun DrawScope.headphones(f: RigFrame, p: Pose, sk: ClawdSkin) {
    val cx = f.x(txc(p, 9.5f)); val cy = f.y(tyc(p, 3f)); val cell = f.cell
    var a = -1.2f
    while (a <= 1.2f) { drawRect(Color(0xFF33414A), Offset(cx + kotlin.math.cos(a - PI.toFloat() / 2) * cell * 3.4f, cy + sin(a - PI.toFloat() / 2) * cell * 3.4f), Size(cell * 0.8f, cell * 0.8f)); a += 0.05f }
    listOf(4 to 9, 15 to 9).forEach { odisc(f.x(txc(p, it.first.toFloat())), f.y(tyc(p, it.second.toFloat())), cell * 0.95f, Color(0xFF2E89A6), Color(0xFF1C5566), cell) }
}
private fun DrawScope.wrench(f: RigFrame, mouth: Offset, ang: Float) {
    val cell = f.cell
    val ex = mouth.x + kotlin.math.cos(ang) * cell * 3.1f; val ey = mouth.y + sin(ang) * cell * 3.1f
    drawLine(Color(0xFF5E6970), mouth, Offset(ex, ey), cell * 0.7f, StrokeCap.Round)
    drawLine(Color(0xFFAEB8BE), mouth, Offset(ex, ey), cell * 0.42f, StrokeCap.Round)
    odisc(ex, ey, cell * 0.9f, Color(0xFFAEB8BE), Color(0xFF5E6970), cell)
    drawCircle(Color(0xFFFAF9F5), cell * 0.42f, Offset(ex, ey))
}
private fun DrawScope.bolt(f: RigFrame, sk: ClawdSkin) {
    val x = f.x(18.5f); val y = f.y(14.5f)
    odisc(x, y, f.cell * 0.8f, Color(0xFF9AA6AD), Color(0xFF5E6970), f.cell)
    drawCircle(Color(0xFF3A4348), f.cell * 0.3f, Offset(x, y))
}
private fun DrawScope.bang(f: RigFrame, p: Pose, color: Color) {
    val x = f.x(txc(p, 9.5f)); val y = f.y(tyc(p, -2f)); val cell = f.cell
    drawRect(color, Offset(x - cell * 0.3f, y), Size(cell * 0.8f, cell * 1.6f))
    drawRect(color, Offset(x - cell * 0.3f, y + cell * 2.0f), Size(cell * 0.8f, cell * 0.8f))
}
private fun DrawScope.waves(f: RigFrame, p: Pose, sk: ClawdSkin) {
    val cx = f.x(txc(p, 17f)); val cy = f.y(tyc(p, 4f))
    for (i in 0..2) {
        val ph = ((p.p + i / 3f) % 1f); val r = f.cell * (2.5f + ph * 7f)
        drawArc(sk.o.copy(alpha = (1f - ph).coerceIn(0f, 1f)), -40f, 80f, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = Stroke(f.cell * 0.5f))
    }
}
private fun DrawScope.dots(f: RigFrame, p: Pose, sk: ClawdSkin) {
    val n = ((p.p * 4f).toInt()) % 4
    for (i in 0..2) drawCircle(sk.s.copy(alpha = if (i < n) 1f else 0.18f), f.cell * 0.4f, Offset(f.x(14.5f + i * 1.4f), f.y(0.5f)))
}
private fun DrawScope.notes(f: RigFrame, p: Pose) {
    fun note(gx: Float, gy: Float, c: Color) { drawCircle(c, f.cell * 0.55f, Offset(f.x(gx), f.y(gy))); drawRect(c, Offset(f.x(gx) + f.cell * 0.4f, f.y(gy) - f.cell * 1.4f), Size(f.cell * 0.35f, f.cell * 1.5f)) }
    note(1.5f, 2f + sin(p.p * TAU) * 0.6f, Color(0xFF56C1DE)); note(18f, 1.5f + kotlin.math.cos(p.p * TAU) * 0.6f, Color(0xFFD97757))
}
private fun DrawScope.zzz(f: RigFrame, p: Pose) {
    val cell = f.cell
    for (i in 0..2) {
        val k = (p.p + i / 3f) % 1f; val s = cell * (0.5f + i * 0.25f); val a = (1f - k).coerceIn(0f, 1f)
        val cx = f.x(15f + i * 1.2f); val cy = f.y(2f) - k * cell * 3f; val col = Color(0xFF73706A).copy(alpha = a)
        drawLine(col, Offset(cx - s, cy - s), Offset(cx + s, cy - s), cell * 0.22f)
        drawLine(col, Offset(cx + s, cy - s), Offset(cx - s, cy + s), cell * 0.22f)
        drawLine(col, Offset(cx - s, cy + s), Offset(cx + s, cy + s), cell * 0.22f)
    }
}
private fun DrawScope.sparkle(f: RigFrame, p: Pose) {
    val pts = listOf(Triple(5f, -1f, Color(0xFF56C1DE)), Triple(14f, 0f, Color(0xFFD97757)), Triple(10f, -2f, Color(0xFF788C5D)))
    pts.forEachIndexed { i, (gx, gy, c) ->
        val s = f.cell * (0.5f + 1.3f * abs(sin((p.p + i * 0.33f) * TAU)))
        val cx = f.x(gx); val cy = f.y(gy)
        drawLine(c, Offset(cx - s, cy), Offset(cx + s, cy), f.cell * 0.18f, StrokeCap.Round)
        drawLine(c, Offset(cx, cy - s), Offset(cx, cy + s), f.cell * 0.18f, StrokeCap.Round)
    }
}

// ─────────────────────────── motions (phase → Pose) ───────────────────────────

// Easing + animation helpers — real craft: slow-in/out (no linear/raw-sine), anticipation,
// follow-through (claws lag the body), overshoot-and-settle, volume-preserving squash & stretch.
private fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
private fun seg(p: Float, s: Float, e: Float, a: Float, b: Float, fn: (Float) -> Float) = lerp(a, b, fn(clamp01((p - s) / (e - s))))
private fun volSx(sy: Float) = sy.pow(-0.55f) // widen/narrow so the silhouette's area is conserved
private fun damp(p: Float, amp: Float, freq: Float, decay: Float) = amp * exp(-decay * p) * sin(freq * p * TAU)

private object Ease {
    fun inOutSine(t: Float) = -(cos(PI.toFloat() * t) - 1f) / 2f
    fun inQuad(t: Float) = t * t
    fun outQuad(t: Float) = 1f - (1f - t) * (1f - t)
    fun outCubic(t: Float) = 1f - (1f - t).pow(3)
    fun outBounce(t0: Float): Float {
        val n = 7.5625f; val d = 2.75f; var t = t0
        return when {
            t < 1f / d -> n * t * t
            t < 2f / d -> { t -= 1.5f / d; n * t * t + 0.75f }
            t < 2.5f / d -> { t -= 2.25f / d; n * t * t + 0.9375f }
            else -> { t -= 2.625f / d; n * t * t + 0.984375f }
        }
    }
}

fun ClawdState.pose(p: Float): Pose = when (this) {
    ClawdState.Idle -> {
        val breath = Ease.inOutSine((sin(p * TAU) + 1f) / 2f)              // eased breathing
        val sy = 0.97f + 0.06f * breath
        val lagL = (sin((p - 0.10f) * TAU) + 1f) / 2f                     // claws trail the body…
        val lagR = (sin((p - 0.16f) * TAU) + 1f) / 2f                     // …and L/R are offset (no twinning)
        Pose(
            sx = volSx(sy), sy = sy, ty = -0.35f * breath,
            raiseL = 0.22f + 0.16f * lagL, raiseR = 0.22f + 0.16f * lagR, openL = 0.12f, openR = 0.12f,
            eye = if (p in 0.46f..0.52f) ClawdEye.Closed else ClawdEye.Dot, mouth = ClawdMouth.Flat, p = p,
        )
    }
    ClawdState.Listening -> {
        val breath = Ease.inOutSine((sin(p * TAU) + 1f) / 2f); val sy = 0.98f + 0.04f * breath
        val cup = Ease.inOutSine((sin(p * TAU * 2) + 1f) / 2f)
        Pose(
            eye = ClawdEye.Wide, tx = 0.35f, ty = -0.2f * breath, sx = volSx(sy), sy = sy,
            raiseR = 1.7f + 0.15f * cup, openR = 0.2f, raiseL = 0.4f, openL = 0.15f,
            effect = ClawdEffect.Waves, mouth = ClawdMouth.Flat, p = p,
        )
    }
    ClawdState.Working -> {
        val beat = (p * 2f) % 1f
        val down = if (beat < 0.5f) Ease.outQuad(beat / 0.5f) else 1f - Ease.inQuad((beat - 0.5f) / 0.5f) // snappy tap
        val sy = 1f - 0.05f * down                                        // squash on impact
        Pose(
            eye = ClawdEye.Dot, sx = volSx(sy), sy = sy, ty = -0.15f * down,
            raiseR = 0.5f + down * 0.8f, raiseL = 0.5f + (1f - down) * 0.5f, openR = 0.2f, openL = 0.2f,
            effect = ClawdEffect.Dots, mouth = ClawdMouth.Flat, p = p,
        )
    }
    ClawdState.Navigating -> {
        val sway = sin(p * TAU); val sy = 1f - 0.03f * abs(sin(p * TAU * 2))
        Pose(
            eye = ClawdEye.Wide, tx = 2f * (Ease.inOutSine((sway + 1f) / 2f) - 0.5f), ty = abs(sin(p * TAU * 2)) * 0.25f,
            sx = volSx(sy), sy = sy,
            raiseL = 0.5f + 0.3f * sway, raiseR = 0.5f - 0.3f * sway, openL = 0.15f, openR = 0.15f, mouth = ClawdMouth.Flat, p = p,
        )
    }
    ClawdState.Success -> {
        val ty = if (p < 0.4f) seg(p, 0f, 0.4f, 0f, -3.5f, Ease::outQuad) else -3.5f * (1f - Ease.outBounce((p - 0.4f) / 0.6f))
        val sy = if (p < 0.14f) lerp(0.82f, 1.16f, Ease.outCubic(p / 0.14f)) else 1f + 0.04f * sin(p * TAU * 3) * (1f - p)
        val air = ty < -1.0f
        Pose(
            sx = volSx(sy), sy = sy, ty = ty, eye = ClawdEye.Happy,
            raiseL = if (air) 2.0f else 0.4f, raiseR = if (air) 2.0f else 0.4f,
            openL = if (air) 0.8f else 0.2f, openR = if (air) 0.8f else 0.2f,
            effect = ClawdEffect.Sparkle, mouth = ClawdMouth.Grin, p = p,
        )
    }
    ClawdState.Error -> {
        val w = damp(p, 0.9f, 3.0f, 3.2f)                                 // damped wobble — settles, not a constant shake
        Pose(
            eye = ClawdEye.X, tx = w, sx = 1f + 0.04f * cos(p * TAU * 3) * exp(-3f * p), sy = 1f,
            raiseL = -0.2f + w * 0.2f, raiseR = -0.2f - w * 0.2f, openL = 0.1f, openR = 0.1f,
            effect = ClawdEffect.BangWarn, mouth = ClawdMouth.Tongue, p = p,
        )
    }
}
