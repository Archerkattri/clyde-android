package dev.kris.clyde.overlay

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import kotlin.math.cos
import kotlin.math.sin

/** Clawd's task states → bundled GIF + skin (blue for live, orange for warn). */
enum class ClawdState(val asset: String, val blue: Boolean) {
    Idle("clawd-idle.gif", true),
    Listening("clawd-mini-peek.gif", true),
    Working("clawd-building.gif", true),
    Navigating("clawd-mini-crabwalk.gif", true),
    Success("clawd-happy.gif", true),
    Error("clawd-error.gif", false),
}

@Composable
fun ClawdView(state: ClawdState, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val ctx = LocalContext.current
    val loader = remember {
        ImageLoader.Builder(ctx)
            .components { add(ImageDecoderDecoder.Factory()) }
            .build()
    }
    val filter = if (state.blue) ColorFilter.colorMatrix(hueRotation(177f)) else null
    AsyncImage(
        model = ImageRequest.Builder(ctx)
            .data("file:///android_asset/clawd/${state.asset}")
            .crossfade(false)
            .build(),
        imageLoader = loader,
        contentDescription = "Clawd",
        colorFilter = filter,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

/** Standard hue-rotation color matrix (turns the orange crab Clyde-blue). */
private fun hueRotation(deg: Float): ColorMatrix {
    val r = Math.toRadians(deg.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
    return ColorMatrix(
        floatArrayOf(
            lr + c * (1 - lr) + s * (-lr), lg + c * (-lg) + s * (-lg), lb + c * (-lb) + s * (1 - lb), 0f, 0f,
            lr + c * (-lr) + s * (0.143f), lg + c * (1 - lg) + s * (0.140f), lb + c * (-lb) + s * (-0.283f), 0f, 0f,
            lr + c * (-lr) + s * (-(1 - lr)), lg + c * (-lg) + s * (lg), lb + c * (1 - lb) + s * (lb), 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
}
