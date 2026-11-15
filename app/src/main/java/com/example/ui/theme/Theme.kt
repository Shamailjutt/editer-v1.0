package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

@Immutable
data class SemanticColors(
    val verified: Color,
    val verifiedContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val surfaceAlt: Color
)

val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(
        verified = VerifiedGreenLight,
        verifiedContainer = VerifiedGreenContainerLight,
        warning = WarnAmberLight,
        warningContainer = WarnAmberContainerLight,
        surfaceAlt = SlateSurfaceAltLight
    )
}

private val LightColors = lightColorScheme(
    primary = CobaltBlue,
    onPrimary = Color.White,
    primaryContainer = CobaltContainerLight,
    onPrimaryContainer = SlateInk,
    secondary = SlateMuted,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = SlateInk,
    tertiary = VerifiedGreenLight,
    onTertiary = Color.White,
    tertiaryContainer = VerifiedGreenContainerLight,
    onTertiaryContainer = SlateInk,
    background = SlateBgLight,
    onBackground = SlateInk,
    surface = SlateSurfaceLight,
    onSurface = SlateInk,
    surfaceVariant = SlateSurfaceAltLight,
    onSurfaceVariant = SlateMuted,
    outline = SlateOutlineLight,
    outlineVariant = Color(0xFFE2E8F0),
    error = DangerRedLight,
    onError = Color.White,
    errorContainer = DangerRedContainerLight,
    onErrorContainer = DangerRedLight
)

private val DarkColors = darkColorScheme(
    primary = CobaltBlueDark,
    onPrimary = Color.White,
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = SlateTextDark,
    secondary = Color(0xFF94A3B8),
    onSecondary = SlateInk,
    secondaryContainer = Color(0xFF273549),
    onSecondaryContainer = SlateTextDark,
    tertiary = VerifiedGreenDark,
    onTertiary = SlateInk,
    tertiaryContainer = VerifiedGreenContainerDark,
    onTertiaryContainer = SlateTextDark,
    background = SlateBgDark,
    onBackground = SlateTextDark,
    surface = SlateSurfaceDark,
    onSurface = SlateTextDark,
    surfaceVariant = SlateSurfaceAltDark,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = SlateOutlineDark,
    outlineVariant = Color(0xFF273549),
    error = DangerRedDark,
    onError = SlateInk,
    errorContainer = DangerRedContainerDark,
    onErrorContainer = DangerRedDark
)

val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun ShamailEditorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val semanticColors = if (darkTheme) {
        SemanticColors(
            verified = VerifiedGreenDark,
            verifiedContainer = VerifiedGreenContainerDark,
            warning = WarnAmberDark,
            warningContainer = WarnAmberContainerDark,
            surfaceAlt = SlateSurfaceAltDark
        )
    } else {
        SemanticColors(
            verified = VerifiedGreenLight,
            verifiedContainer = VerifiedGreenContainerLight,
            warning = WarnAmberLight,
            warningContainer = WarnAmberContainerLight,
            surfaceAlt = SlateSurfaceAltLight
        )
    }

    CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
