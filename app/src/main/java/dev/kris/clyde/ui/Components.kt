package dev.kris.clyde.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.kris.clyde.R

/** The Claude sunburst mark, worn in Clyde's blue (or any tint). */
@Composable
fun ClydeLogo(modifier: Modifier = Modifier, tint: Color = ClydeColor.Blue, size: Dp = 40.dp) {
    Icon(
        painter = painterResource(R.drawable.ic_clyde_logo),
        contentDescription = "Clyde",
        tint = tint,
        modifier = modifier.size(size),
    )
}

/** A blue hero ring with the sunburst inside — the login/first-run mark. */
@Composable
fun HeroMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(74.dp)
            .background(ClydeColor.BlueTint, CircleShape)
            .border(1.dp, ClydeColor.Blue.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        ClydeLogo(size = 38.dp)
    }
}

/**
 * A tappable region that is always ≥48dp (WCAG 2.5.5) and announces itself to TalkBack as a button
 * with [label]. Use instead of bare `Modifier.clickable` on custom (non-Button) tap targets.
 */
@Composable
fun Modifier.pressable(label: String? = null, role: Role = Role.Button, onClick: () -> Unit): Modifier =
    this
        .minimumInteractiveComponentSize()
        .clickable(role = role, onClickLabel = label, onClick = onClick)

/** True when the user has turned off animations in system settings — gate infinite/decorative motion. */
@Composable
fun reduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = ClydeColor.Muted) {
    Text(
        text = text.uppercase(),
        fontFamily = Mono,
        fontSize = 11.sp,
        letterSpacing = 0.16.em,
        color = color,
        modifier = modifier,
    )
}

/** Primary CTA — the one blue fill per screen. Dark teal-ink label for AA contrast. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ClydeColor.Blue,
            contentColor = Color(0xFF06303C),
            disabledContainerColor = ClydeColor.Blue.copy(alpha = 0.45f),
            disabledContentColor = Color(0xFF06303C).copy(alpha = 0.6f),
        ),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(text, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/** A de-emphasized secondary link (muted, centered). */
@Composable
fun SecondaryLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, mono: Boolean = false) {
    Text(
        text = text,
        fontFamily = if (mono) Mono else Body,
        fontSize = if (mono) 11.sp else 13.sp,
        color = if (mono) ClydeColor.Faint else ClydeColor.Muted,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
    )
}

enum class CheckState { Ok, Pending, Fail }

/** Verify-screen check row: a square box (green when ok, dashed-blue tint when pending). */
@Composable
fun CheckRow(state: CheckState, title: String, note: String, modifier: Modifier = Modifier) {
    val boxColor = when (state) {
        CheckState.Ok -> ClydeColor.Verda
        CheckState.Pending -> ClydeColor.BlueTint
        CheckState.Fail -> ClydeColor.TerracottaTint
    }
    val borderColor = when (state) {
        CheckState.Ok -> Color.Transparent
        CheckState.Pending -> ClydeColor.Blue
        CheckState.Fail -> ClydeColor.Terracotta
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        val boxBorder = if (state == CheckState.Pending) {
            Modifier.drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(7.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))),
                )
            }
        } else {
            Modifier.border(1.dp, borderColor, RoundedCornerShape(7.dp))
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(boxColor, RoundedCornerShape(7.dp))
                .then(boxBorder),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                CheckState.Ok -> Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                CheckState.Fail -> Text("!", color = ClydeColor.TerracottaDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                CheckState.Pending -> Unit
            }
        }
        Text(
            title,
            fontFamily = Body,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = if (state == CheckState.Pending) ClydeColor.Muted else ClydeColor.Ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            note,
            fontFamily = Mono,
            fontSize = 11.sp,
            color = if (state == CheckState.Ok) ClydeColor.VerdaDeep else ClydeColor.Muted,
        )
    }
}
