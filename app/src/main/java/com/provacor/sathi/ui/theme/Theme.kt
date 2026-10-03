package com.provacor.sathi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// "River at night": deep blue-slate ground, marigold for the voice (the one bold
// colour), river teal for things that worked. Light theme keeps the same roles.
private val Dark = darkColorScheme(
    primary = Color(0xFFF4A93B),
    onPrimary = Color(0xFF241500),
    primaryContainer = Color(0xFF4A3410),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFF5EC7BE),
    onSecondary = Color(0xFF00201E),
    secondaryContainer = Color(0xFF123C3A),
    onSecondaryContainer = Color(0xFFB8F0EA),
    background = Color(0xFF0D1520),
    onBackground = Color(0xFFE7EEF6),
    surface = Color(0xFF0D1520),
    onSurface = Color(0xFFE7EEF6),
    surfaceVariant = Color(0xFF1A2836),
    onSurfaceVariant = Color(0xFF9DB0C4),
    surfaceContainer = Color(0xFF142030),
    surfaceContainerHigh = Color(0xFF1A2836),
    outline = Color(0xFF3A4D62),
    outlineVariant = Color(0xFF26364A),
    error = Color(0xFFFF8A7A),
    onError = Color(0xFF3B0904),
)

private val Light = lightColorScheme(
    primary = Color(0xFFA65E00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB0),
    onPrimaryContainer = Color(0xFF2B1700),
    secondary = Color(0xFF0B6E67),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC4EEE9),
    onSecondaryContainer = Color(0xFF00201E),
    background = Color(0xFFF3F6F9),
    onBackground = Color(0xFF0F1A24),
    surface = Color(0xFFF3F6F9),
    onSurface = Color(0xFF0F1A24),
    surfaceVariant = Color(0xFFE3EAF1),
    onSurfaceVariant = Color(0xFF475869),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEAF0F5),
    outline = Color(0xFFA9B7C4),
    outlineVariant = Color(0xFFD3DDE6),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val base = Typography()

private val SathiTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium),
)

/** Monospace style for the developer log. */
val LogTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)

@Composable
fun SathiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = SathiTypography,
        content = content,
    )
}

/** Colour for a step state, kept apart from the marigold voice accent where possible. */
object StatusColors {
    fun done(c: ColorScheme) = c.secondary
    fun attention(c: ColorScheme) = c.primary
    fun failed(c: ColorScheme) = c.error
    fun idle(c: ColorScheme) = c.outline
}
