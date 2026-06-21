package dev.kris.clyde.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * Makes a one-screen panel fit ANY Android device — no scrolling. Two jobs, both needed for the
 * "cropped on the Galaxy S24 Ultra" report:
 *
 *  1. **Insets.** With target SDK 35+, Android draws the app edge-to-edge: content goes *behind* the
 *     status bar, camera cutout, and gesture/nav bar unless you pad for them. Without this the top
 *     (Clawd + title) hides under the punch-hole and the bottom button hides under the nav bar.
 *  2. **Scale-to-fit.** If the content is taller than the space left — small screens, or the larger
 *     display/font size many phones (Samsung especially) ship by default — it's uniformly scaled
 *     DOWN so every pixel stays on screen. Never scaled up, so roomy screens look exactly as designed.
 *
 * The content is measured at the available width and its natural height, then centered. Pass a single
 * root composable (a Column); it's laid out as one block so the scale is uniform.
 */
@Composable
fun FitToScreen(
    modifier: Modifier = Modifier,
    insets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable () -> Unit,
) {
    Layout(
        // Wrap in a width-filling Box so there's always exactly one block to measure & scale.
        content = { Box(Modifier.fillMaxWidth()) { content() } },
        modifier = modifier
            .fillMaxSize()
            .background(ClydeColor.Paper)
            .windowInsetsPadding(insets),
    ) { measurables, constraints ->
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        // Fixed width = the space we have; unbounded height so the block reports the height it WANTS.
        val childConstraints = Constraints(minWidth = maxW, maxWidth = maxW, minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(childConstraints) }
        val contentH = placeables.maxOfOrNull { it.height } ?: 0
        // Only ever shrink. If it already fits, scale stays 1f and it renders pixel-for-pixel.
        val scale = if (contentH > maxH && contentH > 0) maxH.toFloat() / contentH else 1f
        layout(maxW, maxH) {
            placeables.forEach { p ->
                val w = p.width * scale
                val h = p.height * scale
                val x = ((maxW - w) / 2f).roundToInt()
                val y = ((maxH - h) / 2f).roundToInt()
                p.placeWithLayer(x, y) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f) // pivot top-left so (x,y) is the block's corner
                }
            }
        }
    }
}
