package dev.kris.clyde.ui

import androidx.compose.ui.graphics.Color

/**
 * Clyde — "Clay & Sky" palette.
 * Terracotta #D97757 (Anthropic primary) + Blizzard Blue #56C1DE (complement),
 * on warm paper #FAF9F5 / ink #141413.
 */
object ClydeColor {
    // neutrals (warm)
    val Paper = Color(0xFFFAF9F5)
    val PaperFrame = Color(0xFFECE7DB)
    val Panel = Color(0xFFFFFFFF)
    val Panel2 = Color(0xFFF6F2E9)
    val Line = Color(0xFFE8E2D5)
    val Line2 = Color(0xFFD8CFBD)
    val Ink = Color(0xFF141413)
    val Muted = Color(0xFF73706A)
    val Faint = Color(0xFFA8A296)

    // brand
    val Terracotta = Color(0xFFD97757)
    val TerracottaDeep = Color(0xFFBE5D3E)
    val TerracottaTint = Color(0xFFF7E6DD)

    // complement
    val Blue = Color(0xFF56C1DE)
    val BlueDeep = Color(0xFF2E89A6)
    val BlueTint = Color(0xFFE4F2F7)

    // tier ramp: cool → hot (safe → root/danger)
    val Tier0 = Color(0xFF56C1DE) // blizzard blue — intents (always on)
    val Tier1 = Color(0xFF3FA6A0) // teal — accessibility
    val Tier2 = Color(0xFFE0A33C) // amber — shizuku / ADB
    val Tier3 = Color(0xFFC0492F) // terracotta-red — root

    val OnTerracotta = Color(0xFFFFFFFF)
    val Danger = Color(0xFFC0492F)
    val Success = Color(0xFF3FA6A0)
}

/** Tier accent color by index 0..3. */
fun tierColor(tier: Int): Color = when (tier) {
    0 -> ClydeColor.Tier0
    1 -> ClydeColor.Tier1
    2 -> ClydeColor.Tier2
    else -> ClydeColor.Tier3
}

/** Readable on-color for a filled tier chip. */
fun tierOnColor(tier: Int): Color = when (tier) {
    0 -> Color(0xFF0E3C4B)
    1 -> Color(0xFF05312E)
    2 -> Color(0xFF4A3208)
    else -> Color(0xFFFFFFFF)
}
