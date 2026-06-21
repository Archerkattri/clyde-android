package dev.kris.clyde.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ClydeColorScheme = lightColorScheme(
    primary = ClydeColor.Terracotta,
    onPrimary = ClydeColor.OnTerracotta,
    primaryContainer = ClydeColor.TerracottaTint,
    onPrimaryContainer = ClydeColor.TerracottaDeep,
    secondary = ClydeColor.Blue,
    onSecondary = ClydeColor.Ink,
    secondaryContainer = ClydeColor.BlueTint,
    onSecondaryContainer = ClydeColor.BlueDeep,
    background = ClydeColor.Paper,
    onBackground = ClydeColor.Ink,
    surface = ClydeColor.Panel,
    onSurface = ClydeColor.Ink,
    surfaceVariant = ClydeColor.Panel2,
    onSurfaceVariant = ClydeColor.Muted,
    outline = ClydeColor.Line2,
    outlineVariant = ClydeColor.Line,
    error = ClydeColor.Danger,
    onError = ClydeColor.OnTerracotta,
)

@Composable
fun ClydeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClydeColorScheme,
        typography = ClydeTypography,
        shapes = ClydeShapes,
        content = content,
    )
}
