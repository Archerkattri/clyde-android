package dev.kris.clyde.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.kris.clyde.R

// Bundled fonts (res/font). Bricolage + Hanken are variable; Compose applies the
// requested weight via the wght axis on API 26+.
val Display = FontFamily(
    Font(R.font.bricolage_grotesque, FontWeight.Medium),
    Font(R.font.bricolage_grotesque, FontWeight.Bold),
    Font(R.font.bricolage_grotesque, FontWeight.ExtraBold),
)
val Body = FontFamily(
    Font(R.font.hanken_grotesk, FontWeight.Normal),
    Font(R.font.hanken_grotesk, FontWeight.Medium),
    Font(R.font.hanken_grotesk, FontWeight.SemiBold),
)
val Mono = FontFamily(
    Font(R.font.ibm_plex_mono, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)
// Claude's answer voice — warm serif (Source Serif 4, variable).
val Serif = FontFamily(
    Font(R.font.source_serif, FontWeight.Normal),
    Font(R.font.source_serif, FontWeight.Medium),
    Font(R.font.source_serif, FontWeight.SemiBold),
)

val ClydeTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = (-0.02).em),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.14.em),
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 0.12.em),
)
