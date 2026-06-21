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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * Catalog-driven SCENE engine — the Compose port of scripts/clawd-scene.mjs (the visually-verified
 * design bench). Composes the kit (expression + mouth/brow + rich claw poses + costume + props +
 * effects + eased motion) into an animation for ANY of the 194 catalog scenarios. The draw primitives
 * map 1:1 to the bench (drawRect↔rect, drawCircle↔disc, drawLine↔line) so what the bench renders is
 * what this renders; only fine pixel offsets may want on-device tuning.
 *
 * The 6 [ClawdState]s stay the hand-tuned hero animations (overlay/ClawdView); this drives the long
 * tail. A scenario key → [ClawScene] via [sceneFor]; the brain names the situation, the app plays it.
 */

// ── scene model ──
class ClawPose(
    val raise: Float = 0f, val open: Float = 0.12f, val reach: Float = 1.15f,
    val center: Float = 0f, val tip: Boolean = false, val vertical: Boolean = false,
)

class ClawScene(
    val eye: String = "dot", val mouth: String = "flat", val brow: String? = null,
    val clawL: ClawPose = ClawPose(), val clawR: ClawPose = ClawPose(),
    val accessory: String? = null, val props: List<String> = emptyList(),
    val effect: String? = null, val motion: String = "idle-bob", val warn: Boolean = false,
)

// ── geometry / palette (mirrors the bench) ──
private const val S_ORX = 5f
private const val S_ORY = 5f
private const val S_VIEW = 24f       // logical drawing field (cells) — fits the crab + side props
private const val S_PIVX = 9.5f
private const val S_PIVY = 14f
private val S_TAU = (PI * 2).toFloat()

private class SkinS(val o: Color, val s: Color, val w: Color, val e: Color, val outline: Color)
private val S_BLUE = SkinS(Color(0xFF56C1DE), Color(0xFF2E89A6), Color(0xFFFFFFFF), Color(0xFF0E2A33), Color(0xFF236E86))
private val S_TER = SkinS(Color(0xFFD97757), Color(0xFFBE5D3E), Color(0xFFFFFFFF), Color(0xFF241F1C), Color(0xFF8A4B33))
private val GOLD = Color(0xFFF2B33D); private val GOLDS = Color(0xFFC8862A)
private val STEEL = Color(0xFFAEB8BE); private val STEELD = Color(0xFF5E6970)
private val GRN = Color(0xFF788C5D); private val TER = Color(0xFFD97757)
private val GRAY = Color(0xFF9AA6AD); private val PINK = Color(0xFFE27A8B); private val GLOW = Color(0xFF9BDCEE)

private val S_GRID = listOf(
    "                    ", "  oo            oo  ", " oooo          oooo ", " oooo          oooo ",
    " ooso  oooooo  osoo ", "  oo  oooooooo  oo  ", "  o  oooooooooo  o  ", "    owoooooooooo    ",
    "   ooooeooooeoooo   ", "   ooooeooooeoooo   ", "   oooooooooooooo   ", "   oooooooooooooo   ",
    "    ssoooooooooo    ", "    o o o  o o o    ", "   o   o    o   o   ", "                    ",
)
private class SCell(val x: Int, val y: Int, val c: Char)
private val S_BODY: List<SCell> = buildList {
    S_GRID.forEachIndexed { y, row ->
        for (x in row.indices) {
            val c = row[x]
            if (c == ' ' || c == 'e' || (x <= 4 && y <= 6) || (x >= 15 && y <= 6)) continue
            add(SCell(x, y, c))
        }
    }
}
private fun scol(sk: SkinS, c: Char) = when (c) { 's' -> sk.s; 'w' -> sk.w; 'e' -> sk.e; else -> sk.o }

private class SFrame(val cell: Float, val ox: Float, val oy: Float) {
    fun x(g: Float) = ox + (S_ORX + g) * cell
    fun y(g: Float) = oy + (S_ORY + g) * cell
}
// body transform (squash/stretch + translate)
private class Tf(val sx: Float, val sy: Float, val tx: Float, val ty: Float)
private fun tX(t: Tf, gx: Float) = S_PIVX + (gx - S_PIVX) * t.sx + t.tx
private fun tY(t: Tf, gy: Float) = S_PIVY + (gy - S_PIVY) * t.sy + t.ty

// ── eased motion → transform (mirrors bench bodyTf) ──
private object SE {
    fun inOutSine(t: Float) = -(cos(PI.toFloat() * t) - 1f) / 2f
    fun outQuad(t: Float) = 1f - (1f - t) * (1f - t)
    fun inQuad(t: Float) = t * t
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
private fun sVol(s: Float) = s.pow(-0.55f)
private fun bodyTf(motion: String, p: Float): Tf {
    val breath = SE.inOutSine((sin(p * S_TAU) + 1f) / 2f)
    var s = 1f; var tx = 0f; var ty = 0f
    when {
        motion.contains("hop") || motion.contains("pop") -> {
            val a = if (p < 0.4f) SE.outQuad(p / 0.4f) else 1f - SE.outBounce((p - 0.4f) / 0.6f)
            ty = -3.0f * a; s = if (p < 0.14f) 0.82f + 0.4f * SE.outQuad(p / 0.14f) else 1f + 0.04f * sin(p * S_TAU * 3) * (1f - p)
        }
        motion.contains("wobble") || motion.contains("shake") -> { tx = 0.9f * exp(-3f * p) * sin(3f * p * S_TAU); s = 1f }
        motion.contains("sway") || motion.contains("groove") -> { tx = 1.2f * (SE.inOutSine((sin(p * S_TAU) + 1f) / 2f) - 0.5f); s = 1f - 0.05f * SE.inOutSine((sin(p * S_TAU * 2) + 1f) / 2f) }
        motion.contains("walk") -> { tx = 1.0f * sin(p * S_TAU); ty = abs(sin(p * S_TAU * 2)) * 0.3f; s = 1f }
        motion.contains("lean") -> { tx = 0.5f; s = 0.98f + 0.04f * breath; ty = -0.2f * breath }
        motion.contains("tap") || motion.contains("nod") -> {
            val b = (p * 2f) % 1f; val d = if (b < 0.5f) SE.outQuad(b / 0.5f) else 1f - SE.inQuad((b - 0.5f) / 0.5f)
            s = 1f - 0.05f * d; ty = -0.15f * d
        }
        else -> { s = 0.97f + 0.06f * breath; ty = -0.35f * breath } // idle-bob
    }
    return Tf(sVol(s), s, tx, ty)
}

// ── draw helpers (DrawScope) ──
private fun DrawScope.sRect(f: SFrame, gx: Float, gy: Float, w: Float, h: Float, c: Color, a: Float = 1f) =
    drawRect(c.copy(alpha = a), Offset(f.x(gx), f.y(gy)), Size(w * f.cell + f.cell * 0.06f, h * f.cell + f.cell * 0.06f))
private fun DrawScope.sDisc(x: Float, y: Float, r: Float, c: Color, a: Float = 1f) = drawCircle(c.copy(alpha = a), r, Offset(x, y))
private fun DrawScope.sOdisc(x: Float, y: Float, r: Float, fill: Color, ol: Color, cell: Float) { drawCircle(ol, r + cell * 0.16f, Offset(x, y)); drawCircle(fill, r, Offset(x, y)) }
private fun DrawScope.sLine(x0: Float, y0: Float, x1: Float, y1: Float, c: Color, th: Float, a: Float = 1f) = drawLine(c.copy(alpha = a), Offset(x0, y0), Offset(x1, y1), th, StrokeCap.Round)
private fun DrawScope.sArc(cx: Float, cy: Float, r: Float, a0: Float, a1: Float, c: Color, th: Float, a: Float = 1f) {
    var t = a0; while (t <= a1) { drawRect(c.copy(alpha = a), Offset(cx + cos(t) * r - th / 2, cy + sin(t) * r - th / 2), Size(th, th)); t += 0.05f }
}

private fun DrawScope.sBody(f: SFrame, t: Tf, sk: SkinS) {
    for (c in S_BODY) drawRect(scol(sk, c.c), Offset(f.x(tX(t, c.x.toFloat())), f.y(tY(t, c.y.toFloat()))), Size(t.sx * f.cell + f.cell * 0.08f, t.sy * f.cell + f.cell * 0.08f))
}
private fun DrawScope.sFace(f: SFrame, t: Tf, sk: SkinS, eye: String, mouth: String, brow: String?) {
    fun cell(gx: Float, gy: Float, c: Color, w: Float = 1f, h: Float = 1f) =
        drawRect(c, Offset(f.x(tX(t, gx)), f.y(tY(t, gy))), Size(w * t.sx * f.cell + f.cell * 0.08f, h * t.sy * f.cell + f.cell * 0.08f))
    for ((slot, sign) in listOf(7 to 1, 12 to -1)) {
        val sx = slot.toFloat()
        when (eye) {
            "wide" -> { cell(sx - 1, 7f, sk.w, 2f, 2f); cell(sx, 8f, sk.e) }
            "happy" -> { cell(sx - 1, 8f, sk.e); cell(sx, 7f, sk.e); cell(sx + 1, 8f, sk.e) }
            "x" -> listOf(-1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1).forEach { cell(sx + it.first, 8f + it.second, sk.e) }
            "closed" -> cell(sx - 1, 8f, sk.e, 3f, 1f)
            "squint" -> cell(sx, 8f, sk.e)
            "half" -> cell(sx, 9f, sk.e)
            "side" -> { cell(sx + sign, 8f, sk.e); cell(sx - sign, 8f, sk.w) }
            "star" -> listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0, 0 to 0).forEach { cell(sx + it.first, 8f + it.second, if (it.first == 0 && it.second == 0) sk.w else sk.e) }
            "wink" -> if (sign == 1) cell(sx, 8f, sk.e, 1f, 2f) else cell(sx - 1, 8f, sk.e, 3f, 1f)
            else -> cell(sx, 8f, sk.e, 1f, 2f)
        }
    }
    when (mouth) {
        "flat" -> cell(9f, 11f, sk.e, 2f, 1f)
        "grin" -> { cell(8f, 11f, sk.e); cell(9f, 12f, sk.e); cell(10f, 12f, sk.e); cell(11f, 11f, sk.e) }
        "frown" -> { cell(8f, 12f, sk.e); cell(9f, 11f, sk.e); cell(10f, 11f, sk.e); cell(11f, 12f, sk.e) }
        "open" -> cell(9f, 11f, sk.e, 2f, 2f)
        "tongue" -> { cell(9f, 11f, sk.e, 2f, 1f); cell(9f, 12f, PINK, 2f, 1f) }
    }
    when (brow) {
        "up" -> { cell(6f, 6f, sk.e); cell(8f, 5f, sk.e); cell(11f, 5f, sk.e); cell(13f, 6f, sk.e) }
        "v" -> { cell(6f, 5f, sk.e); cell(8f, 6f, sk.e); cell(11f, 6f, sk.e); cell(13f, 5f, sk.e) }
        "level" -> { cell(6f, 6f, sk.e, 2f, 1f); cell(11f, 6f, sk.e, 2f, 1f) }
    }
}
private fun DrawScope.sClaw(f: SFrame, t: Tf, sk: SkinS, side: Float, q: ClawPose): Offset {
    val shx = f.x(tX(t, if (side < 0) 6f else 13f)); val shy = f.y(tY(t, 5f))
    val cs = q.center * -side
    val wGX = (if (side < 0) 4f else 15f) + cs; val wGY = 4.5f - q.raise
    val wx = f.x(tX(t, wGX)) + side * q.raise * 0.18f * f.cell; val wy = f.y(tY(t, wGY))
    sLine(shx, shy, wx, wy, sk.s, f.cell * 0.55f); sLine(shx, shy, wx, wy, sk.o, f.cell * 0.32f)
    sOdisc(wx, wy, f.cell * 0.7f, sk.o, sk.outline, f.cell)
    if (q.vertical) { sOdisc(wx, wy - f.cell * 1.1f, f.cell * 0.6f, sk.o, sk.outline, f.cell); sOdisc(wx, wy + f.cell * 1.1f, f.cell * 0.6f, sk.o, sk.outline, f.cell); return Offset(wx, wy) }
    val reach = q.reach * side * f.cell
    if (q.tip) { val tx = wx + reach * 1.7f; sLine(wx, wy, tx, wy, sk.o, f.cell * 0.5f); sOdisc(tx, wy, f.cell * 0.5f, sk.o, sk.outline, f.cell); return Offset(tx, wy) }
    sOdisc(wx + reach, wy - (0.9f + q.open * 1.1f) * f.cell, f.cell * 0.62f, sk.o, sk.outline, f.cell)
    sOdisc(wx + reach, wy + (0.9f + q.open * 1.1f) * f.cell, f.cell * 0.62f, sk.o, sk.outline, f.cell)
    return Offset(wx + reach, wy)
}

// costumes / props / effects — the high-frequency subset (extends over time)
private fun DrawScope.costume(f: SFrame, t: Tf, name: String) {
    when (name) {
        "hardhat" -> { val cx = f.x(tX(t, 9.5f)); val cy = f.y(tY(t, 3.2f)); sDisc(cx, cy + f.cell * 0.4f, f.cell * 3.1f, GOLDS); sDisc(cx, cy, f.cell * 3.0f, GOLD); drawRect(GOLD, Offset(cx - f.cell * 3.6f, cy + f.cell * 0.4f), Size(f.cell * 7.2f, f.cell * 0.8f)); drawRect(GOLDS, Offset(cx - f.cell * 0.4f, cy - f.cell * 2.8f), Size(f.cell * 0.8f, f.cell * 1.6f)) }
        "headphones" -> { val bx = f.x(tX(t, 9.5f)); val by = f.y(tY(t, 3f)); var a = -1.2f; while (a <= 1.2f) { drawRect(Color(0xFF33414A), Offset(bx + cos(a - PI.toFloat() / 2) * f.cell * 3.4f, by + sin(a - PI.toFloat() / 2) * f.cell * 3.4f), Size(f.cell * 0.8f, f.cell * 0.8f)); a += 0.05f }; listOf(4 to 9, 15 to 9).forEach { sOdisc(f.x(tX(t, it.first.toFloat())), f.y(tY(t, it.second.toFloat())), f.cell * 0.95f, Color(0xFF2E89A6), Color(0xFF1C5566), f.cell) } }
    }
}
private fun DrawScope.prop(f: SFrame, t: Tf, p: Float, name: String, mouthR: Offset) {
    fun X(g: Float) = f.x(g)
    fun Y(g: Float) = f.y(g)
    val cell = f.cell
    when (name) {
        "wrench" -> { val ang = -0.9f + (0.5f + 0.5f * sin(p * S_TAU * 2)) * 0.7f; val ex = mouthR.x + cos(ang) * cell * 3.0f; val ey = mouthR.y + sin(ang) * cell * 3.0f; sLine(mouthR.x, mouthR.y, ex, ey, STEELD, cell * 0.7f); sLine(mouthR.x, mouthR.y, ex, ey, STEEL, cell * 0.42f); sOdisc(ex, ey, cell * 0.9f, STEEL, STEELD, cell); sDisc(ex, ey, cell * 0.42f, Color(0xFFFAF9F5)) }
        "monitor" -> { drawRect(Color(0xFF2A2622), Offset(X(-2.5f), Y(2f)), Size(cell * 5.5f, cell * 5f)); drawRect(Color(0xFF0E2A33), Offset(X(-2f), Y(2.6f)), Size(cell * 4.5f, cell * 3.6f)); drawRect(S_BLUE.o, Offset(X(-1.6f), Y(3.2f)), Size(cell * 2f, cell * 0.7f)) }
        "phone" -> { drawRect(Color(0xFF2A2622), Offset(X(-1f), Y(8f)), Size(cell * 2.2f, cell * 4f)); drawRect(S_BLUE.e, Offset(X(-0.7f), Y(8.5f)), Size(cell * 1.6f, cell * 3f)) }
        "magnifier" -> { val cx = X(-1f); val cy = Y(4f); sOdisc(cx, cy, cell * 1.6f, Color(0xFF0E2A33), STEELD, cell); sDisc(cx, cy, cell * 1.1f, Color(0xFFBFE9F5)); sLine(cx + cell, cy + cell, cx + cell * 2.4f, cy + cell * 2.4f, STEELD, cell * 0.6f) }
        "map" -> { drawRect(Color(0xFFF3E7C8), Offset(X(3f), Y(11.5f)), Size(cell * 12f, cell * 4f)); sLine(X(5f), Y(13.5f), X(13f), Y(12.5f), TER, cell * 0.4f); sDisc(X(13f), Y(12.5f), cell * 0.7f, TER) }
        "camera" -> { drawRect(Color(0xFF2A2622), Offset(X(-2f), Y(8f)), Size(cell * 4f, cell * 3f)); sOdisc(X(0f), Y(9.5f), cell * 1.1f, Color(0xFF0E2A33), STEELD, cell); drawRect(Color.White, Offset(X(1.4f), Y(7.6f)), Size(cell * 0.6f, cell * 0.6f)) }
        "envelope" -> { drawRect(Color(0xFFFAF6EC), Offset(X(-1.5f), Y(8f)), Size(cell * 4f, cell * 2.8f)); sLine(X(-1.5f), Y(8f), X(0.5f), Y(9.4f), GRAY, cell * 0.3f); sLine(X(2.5f), Y(8f), X(0.5f), Y(9.4f), GRAY, cell * 0.3f) }
        "phonecall" -> { drawRect(Color(0xFF2A2622), Offset(X(-1f), Y(8f)), Size(cell * 2.2f, cell * 4f)); sDisc(X(0f), Y(10f), cell * 0.7f, GRN) }
        "battery" -> { drawRect(Color(0xFFFAF6EC), Offset(X(-2f), Y(9f)), Size(cell * 4f, cell * 2f)); drawRect(GRAY, Offset(X(2f), Y(9.5f)), Size(cell * 0.5f, cell)); drawRect(GRN, Offset(X(-1.7f), Y(9.3f)), Size(cell * 2.4f, cell * 1.4f)) }
        "compass" -> { val x = X(-1f); val y = Y(9.5f); sOdisc(x, y, cell * 1.8f, Color(0xFFFAF6EC), STEELD, cell); sLine(x, y, x, y - cell * 1.3f, TER, cell * 0.4f); sLine(x, y, x, y + cell * 1.3f, S_BLUE.s, cell * 0.4f) }
        "music" -> { listOf(1.5f to 2f, 18f to 1.5f).forEachIndexed { i, (gx, gy) -> val c = if (i == 1) TER else S_BLUE.o; val x = X(gx); val yy = Y(gy + sin((p + i * 0.4f) * S_TAU) * 0.6f); sDisc(x, yy, cell * 0.55f, c); drawRect(c, Offset(x + cell * 0.4f, yy - cell * 1.4f), Size(cell * 0.35f, cell * 1.5f)) } }
        "sun" -> { sDisc(X(15.5f), Y(2f), cell * 1.2f, GOLD); for (k in 0 until 8) { val a = k / 8f * S_TAU; sLine(X(15.5f) + cos(a) * cell * 1.7f, Y(2f) + sin(a) * cell * 1.7f, X(15.5f) + cos(a) * cell * 2.4f, Y(2f) + sin(a) * cell * 2.4f, GOLD, cell * 0.3f) } }
    }
}
private fun DrawScope.effect(f: SFrame, t: Tf, p: Float, name: String) {
    fun X(g: Float) = f.x(g)
    fun Y(g: Float) = f.y(g)
    val cell = f.cell
    fun ring(gx: Float, gy: Float, r: Float, c: Color, a: Float) { var a0 = 0f; while (a0 < S_TAU) { drawRect(c.copy(alpha = a), Offset(X(gx + cos(a0) * r - 0.5f), Y(gy + sin(a0) * r - 0.5f)), Size(cell * 0.6f, cell * 0.6f)); a0 += 0.13f } }
    when {
        name.contains("soundwaves") || name == "sound-waves" -> for (i in 0 until 3) { val ph = (p + i / 3f) % 1f; sArc(X(17f), Y(5f), cell * (2.5f + ph * 6f), -0.7f, 0.7f, S_BLUE.o, cell * 0.5f, 1f - ph) }
        name.contains("voicewaves") || name == "voice-waves-out" -> for (i in 0 until 3) { val ph = (p + i / 3f) % 1f; sArc(X(9.5f), Y(12f), cell * (2f + ph * 6f), 0.6f, 2.54f, S_BLUE.o, cell * 0.5f, 1f - ph) }
        name.contains("spinner") -> for (i in 0 until 3) sDisc(X(15.5f + i * 1.6f), Y(0.6f), cell * 0.45f, S_BLUE.s, if ((p * 6).toInt() % 3 == i) 1f else 0.3f)
        name.contains("loading") -> for (i in 0 until 8) { val a = i / 8f * S_TAU; sDisc(X(9.5f + cos(a) * 1.9f), Y(2.0f + sin(a) * 1.9f), cell * 0.4f, S_BLUE.o, if ((p * 8).toInt() % 8 == i) 1f else 0.3f) }
        name.contains("progress") -> { drawRect(Color(0xFF1C3A45), Offset(X(4f), Y(16f)), Size(cell * 12f, cell * 0.8f)); drawRect(S_BLUE.o, Offset(X(4f), Y(16f)), Size(cell * 12f * (0.3f + 0.6f * p), cell * 0.8f)) }
        name.contains("sparkle") -> listOf(5 to 1, 14 to 0, 10 to 2).forEachIndexed { i, (gx, gy) -> val s = 0.5f + 1.0f * abs(sin((p + i * 0.33f) * S_TAU)); sLine(X(gx.toFloat()) - s * cell, Y(gy.toFloat()), X(gx.toFloat()) + s * cell, Y(gy.toFloat()), GLOW, cell * 0.2f); sLine(X(gx.toFloat()), Y(gy.toFloat()) - s * cell, X(gx.toFloat()), Y(gy.toFloat()) + s * cell, GLOW, cell * 0.2f) }
        name.contains("exclam") -> { drawRect(TER, Offset(X(9.5f), Y(1f)), Size(cell * 0.8f, cell * 1.6f)); drawRect(TER, Offset(X(9.5f), Y(3f)), Size(cell * 0.8f, cell * 0.8f)) }
        name.contains("question") -> { drawRect(S_BLUE.s, Offset(X(9f), Y(1f)), Size(cell * 2f, cell * 0.8f)); drawRect(S_BLUE.s, Offset(X(10.5f), Y(1.5f)), Size(cell * 0.8f, cell * 0.8f)); drawRect(S_BLUE.s, Offset(X(9.7f), Y(2.2f)), Size(cell * 0.8f, cell * 0.8f)); drawRect(S_BLUE.s, Offset(X(9.7f), Y(3.4f)), Size(cell * 0.8f, cell * 0.8f)) }
        name.contains("thought") -> { sDisc(X(14f), Y(3f), cell * 0.5f, GLOW); sDisc(X(16f), Y(1.6f), cell * 0.8f, GLOW); sDisc(X(18f), Y(0f), cell * 1.1f, GLOW) }
        name.contains("lightbulb") -> { sDisc(X(9.5f), Y(1.2f), cell * 1.3f, GOLD); drawRect(GOLDS, Offset(X(9.2f), Y(2.4f)), Size(cell, cell * 0.6f)) }
        name.contains("checkmark") -> listOf(8f to 2f, 8.7f to 2.7f, 9.4f to 2f, 10.1f to 1f, 10.8f to 0f).forEach { drawRect(GRN, Offset(X(it.first), Y(it.second)), Size(cell * 0.8f, cell * 0.8f)) }
        name.contains("xcross") || name.contains("x-cross") -> for (k in -2..2) { drawRect(TER, Offset(X(9.5f + k), Y(1f + k)), Size(cell * 0.8f, cell * 0.8f)); drawRect(TER, Offset(X(9.5f + k), Y(1f - k)), Size(cell * 0.8f, cell * 0.8f)) }
        name.contains("sweat") -> { sDisc(X(13.5f), Y(6f), cell * 0.6f, S_BLUE.o); drawRect(S_BLUE.o, Offset(X(13.4f), Y(5f)), Size(cell * 0.5f, cell * 1.2f)) }
        name.contains("firework") -> listOf(Triple(6f, 1f, S_BLUE.o), Triple(13f, 0f, TER), Triple(10f, 2f, GRN)).forEach { (cx, cy, c) -> for (k in 0 until 8) { val a = k / 8f * S_TAU; sLine(X(cx) + cos(a) * cell, Y(cy) + sin(a) * cell, X(cx) + cos(a) * cell * 2.4f, Y(cy) + sin(a) * cell * 2.4f, c, cell * 0.35f, 0.9f) } }
        name.contains("confetti") -> listOf(Triple(5f, 1f, S_BLUE.o), Triple(8f, 3f, TER), Triple(11f, 0f, GRN), Triple(14f, 2f, S_BLUE.o)).forEach { (x, y, c) -> drawRect(c, Offset(X(x), Y(y)), Size(cell * 0.8f, cell * 0.8f)) }
        name.contains("heart") -> listOf(Triple(10f, 2f, 1f), Triple(13f, 0f, 0.7f)).forEach { (x, y, a) -> drawRect(PINK.copy(alpha = a), Offset(X(x), Y(y + 0.3f)), Size(cell * 0.7f, cell * 0.7f)); drawRect(PINK.copy(alpha = a), Offset(X(x + 0.9f), Y(y + 0.3f)), Size(cell * 0.7f, cell * 0.7f)); drawRect(PINK.copy(alpha = a), Offset(X(x + 0.45f), Y(y + 1f)), Size(cell * 0.7f, cell * 0.7f)) }
        name.contains("zzz") -> listOf(Triple(15f, 4f, 0.7f), Triple(16.5f, 2.5f, 1f), Triple(18f, 0.7f, 1.3f)).forEach { (x, y, s) -> drawRect(GRAY, Offset(X(x), Y(y)), Size(s * cell, cell * 0.4f)); drawRect(GRAY, Offset(X(x + s - 0.4f), Y(y)), Size(cell * 0.4f, s * cell)); drawRect(GRAY, Offset(X(x), Y(y + s - 0.4f)), Size(s * cell, cell * 0.4f)) }
        name.contains("glow") -> ring(9.5f, 9f, 9.0f + sin(p * S_TAU) * 0.5f, GLOW, 1f)
        name.contains("scan") -> drawRect(GLOW.copy(alpha = 0.9f), Offset(X(3f), Y(9f)), Size(cell * 14f, cell * 0.5f))
        name.contains("ripple") -> for (i in 0 until 2) ring(9.5f, 14f, 3f + i * 2 + (p * 3) % 3, S_BLUE.o, (0.7f - i * 0.3f).coerceAtLeast(0f))
        name.contains("tear") -> drawRect(S_BLUE.o, Offset(X(7f), Y(10.5f)), Size(cell * 0.7f, cell * 1.4f))
        name.contains("flash") || name.contains("camera") -> drawRect(Color.White.copy(alpha = 0.5f), Offset(0f, 0f), Size(size.width, size.height))
    }
}

private fun DrawScope.drawClawScene(scene: ClawScene, p: Float) {
    val cell = size.minDimension / S_VIEW
    val f = SFrame(cell, (size.width - S_VIEW * cell) / 2f, (size.height - S_VIEW * cell) / 2f)
    val sk = if (scene.warn) S_TER else S_BLUE
    val t = bodyTf(scene.motion, p)
    val behind = scene.effect != null && Regex("glow|waves|spinner|loading|progress|thought|zzz").containsMatchIn(scene.effect)
    if (scene.effect != null && behind) effect(f, t, p, scene.effect)
    scene.props.forEach { if (it != "wrench" && it != "music") prop(f, t, p, it, Offset.Zero) }
    sBody(f, t, sk)
    sFace(f, t, sk, scene.eye, scene.mouth, scene.brow)
    val mr = sClaw(f, t, sk, +1f, scene.clawR)
    sClaw(f, t, sk, -1f, scene.clawL)
    scene.props.forEach { if (it == "wrench" || it == "music") prop(f, t, p, it, mr) }
    scene.accessory?.let { costume(f, t, it) }
    if (scene.effect != null && !behind) effect(f, t, p, scene.effect)
}

/** Renders the composed animation for a catalog scenario. Falls back to a calm idle for unknown keys. */
@Composable
fun ClawdSceneView(sceneKey: String, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val scene = remember(sceneKey) { sceneFor(sceneKey) }
    val base = modifier.size(size)
    if (reduceMotion()) { Canvas(base) { drawClawScene(scene, 0.0f) }; return }
    val tr = rememberInfiniteTransition(label = "clawd-scene")
    val p by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Canvas(base) { drawClawScene(scene, p) }
}

// ── scenario registry: a curated, high-frequency set; the long tail composes the same way ──
private fun cp(raise: Float = 0f, open: Float = 0.12f, reach: Float = 1.15f, center: Float = 0f, tip: Boolean = false, vertical: Boolean = false) =
    ClawPose(raise, open, reach, center, tip, vertical)

private val SCENES: Map<String, ClawScene> = mapOf(
    "listening" to ClawScene(eye = "wide", clawR = cp(raise = 1.0f, center = 2.2f, open = 0.9f, reach = 0.5f), effect = "sound-waves", motion = "lean"),
    "thinking" to ClawScene(eye = "dot", clawR = cp(raise = 1.6f, center = 5.0f, open = 0.3f), effect = "thought-bubbles", motion = "idle-bob"),
    "working" to ClawScene(eye = "dot", accessory = "hardhat", props = listOf("wrench"), clawR = cp(raise = 0.5f), effect = "spinner-dots", motion = "tap"),
    "typing" to ClawScene(eye = "squint", props = listOf("monitor"), clawL = cp(raise = -0.4f), clawR = cp(raise = -0.4f), effect = "spinner-dots", motion = "tap"),
    "searching" to ClawScene(eye = "wide", props = listOf("magnifier"), clawR = cp(raise = 0.6f, tip = true, reach = 1.6f), effect = "scan-line", motion = "lean"),
    "loading" to ClawScene(eye = "dot", effect = "loading-ring", motion = "idle-bob"),
    "navigating" to ClawScene(eye = "wide", props = listOf("map", "compass"), motion = "walk"),
    "music" to ClawScene(eye = "happy", mouth = "grin", accessory = "headphones", props = listOf("music"), effect = "glow-pulse", motion = "groove"),
    "photo" to ClawScene(eye = "happy", props = listOf("camera"), clawR = cp(raise = 0.6f), effect = "camera-flash", motion = "idle-bob"),
    "message" to ClawScene(eye = "dot", props = listOf("envelope"), clawR = cp(raise = 0.4f), motion = "idle-bob"),
    "call" to ClawScene(eye = "wide", props = listOf("phonecall"), effect = "ripple", motion = "idle-bob"),
    "success" to ClawScene(eye = "happy", mouth = "grin", clawL = cp(raise = 2.0f, open = 0.8f), clawR = cp(raise = 2.0f, open = 0.8f), effect = "fireworks", motion = "hop"),
    "celebrate" to ClawScene(eye = "star", mouth = "grin", clawL = cp(raise = 2.5f, open = 0.7f), clawR = cp(raise = 2.5f, open = 0.7f), effect = "confetti", motion = "hop"),
    "done" to ClawScene(eye = "happy", mouth = "grin", clawR = cp(raise = 1.6f, tip = true, reach = 0.4f), effect = "checkmark-pop", motion = "nod"),
    "error" to ClawScene(eye = "x", mouth = "tongue", clawL = cp(raise = -0.2f), clawR = cp(raise = -0.2f), effect = "x-cross-pop", motion = "wobble", warn = true),
    "warning" to ClawScene(eye = "wide", brow = "v", clawL = cp(raise = 0.6f, center = 3.5f, vertical = true), effect = "exclamation", motion = "idle-bob", warn = true),
    "confused" to ClawScene(eye = "side", clawR = cp(raise = 1.6f, center = 5.0f, open = 0.3f), effect = "question-mark", motion = "shake-head"),
    "idea" to ClawScene(eye = "star", mouth = "grin", clawR = cp(raise = 1.8f, tip = true), effect = "lightbulb", motion = "hop"),
    "sleeping" to ClawScene(eye = "closed", clawL = cp(raise = -0.3f), clawR = cp(raise = -0.3f), effect = "zzz", motion = "idle-bob"),
    "cold" to ClawScene(eye = "squint", props = listOf("sun"), effect = "shiver-lines", motion = "wobble"),
    "sad" to ClawScene(eye = "half", mouth = "frown", brow = "up", effect = "tear-pixel", motion = "idle-bob"),
    "greeting" to ClawScene(eye = "happy", mouth = "grin", clawR = cp(raise = 2.2f, open = 0.7f), effect = "sparkles", motion = "idle-bob"),
    "blocked" to ClawScene(eye = "x", brow = "v", clawL = cp(raise = 0.6f, center = 3.5f, vertical = true), motion = "shake-head", warn = true),
    "battery_low" to ClawScene(eye = "half", props = listOf("battery"), motion = "idle-bob", warn = true),
    "idle" to ClawScene(eye = "dot", motion = "idle-bob"),
)

/**
 * Conservative status→scene for the LIVE overlay. Returns a rich scene key only for activities the 6
 * hand-tuned hero [ClawdState]s (Idle/Listening/Working/Navigating/Success/Error) don't already cover
 * well; "" means "let the verified hero ClawdView handle it". So the core flow keeps its polished
 * animations and the scene engine adds reactions for the long tail — driven by the brain's own status.
 */
fun overlayScene(status: String): String {
    val k = status.lowercase()
    return when {
        Regex("search|find|look\\s*up|brows|web result").containsMatchIn(k) -> "searching"
        Regex("\\bmap|route|direction|navigat|driv|arriv|nearby").containsMatchIn(k) -> "navigating"
        Regex("music|song|\\bplay|track|video|media|podcast").containsMatchIn(k) -> "music"
        Regex("photo|camera|screenshot|selfie|picture").containsMatchIn(k) -> "photo"
        Regex("\\bcall|ring|dial|phone").containsMatchIn(k) -> "call"
        Regex("messag|\\btext\\b|\\bsms|email|compos|reply").containsMatchIn(k) -> "message"
        Regex("sleep|standby|drowsy|nap").containsMatchIn(k) -> "sleeping"
        Regex("greet|welcome|hello|hi there|goodbye").containsMatchIn(k) -> "greeting"
        Regex("calculat|math|comput").containsMatchIn(k) -> "thinking"
        else -> "" // core activity → hero ClawdView keeps its verified animation
    }
}

/** Map a catalog scenario key (or a loose status keyword) → a composed scene. */
fun sceneFor(key: String): ClawScene {
    val k = key.lowercase()
    SCENES[k]?.let { return it }
    // loose keyword fallback so brain status text still picks a sensible scene
    val hit = SCENES.keys.firstOrNull { k.contains(it) }
    if (hit != null) return SCENES.getValue(hit)
    return when {
        Regex("listen|hear|voice|transcrib|mic").containsMatchIn(k) -> SCENES.getValue("listening")
        Regex("think|reason|plan|comput|process").containsMatchIn(k) -> SCENES.getValue("thinking")
        Regex("search|find|look|browse|read").containsMatchIn(k) -> SCENES.getValue("searching")
        Regex("nav|map|route|direction|travel|arriv").containsMatchIn(k) -> SCENES.getValue("navigating")
        Regex("type|writ|file|edit|calendar|email|note|todo|app").containsMatchIn(k) -> SCENES.getValue("working")
        Regex("music|play|video|media|audio").containsMatchIn(k) -> SCENES.getValue("music")
        Regex("photo|camera|screenshot|capture").containsMatchIn(k) -> SCENES.getValue("photo")
        Regex("call|ring|phone").containsMatchIn(k) -> SCENES.getValue("call")
        Regex("message|text|sms|chat|share|social").containsMatchIn(k) -> SCENES.getValue("message")
        Regex("done|complete|sent|saved|ok|success|verified").containsMatchIn(k) -> SCENES.getValue("done")
        Regex("error|fail|crash").containsMatchIn(k) -> SCENES.getValue("error")
        Regex("warn|confirm|sure|hardstop|consequen|permission").containsMatchIn(k) -> SCENES.getValue("warning")
        Regex("block|denied|unsupported|suspicious").containsMatchIn(k) -> SCENES.getValue("blocked")
        Regex("sleep|standby|idle|drowsy|rest").containsMatchIn(k) -> SCENES.getValue("sleeping")
        Regex("greet|welcome|hello|launch|goodbye").containsMatchIn(k) -> SCENES.getValue("greeting")
        Regex("load|wait|queue|connect").containsMatchIn(k) -> SCENES.getValue("loading")
        else -> SCENES.getValue("idle")
    }
}
