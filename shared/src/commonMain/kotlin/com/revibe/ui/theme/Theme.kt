package com.revibe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Revibe's own visual language: near-black layered surfaces, a lime signal
 * colour and typography that leans on tight, heavy display weights. Deliberately
 * not a Material 3 default look.
 */
object RevibeColors {
    val Base = Color(0xFF08090A)
    val Sidebar = Color(0xFF0B0D0E)
    val Surface = Color(0xFF111314)
    val SurfaceRaised = Color(0xFF181B1C)
    val SurfaceHover = Color(0xFF212526)
    val SurfaceActive = Color(0xFF272C2D)
    val Outline = Color(0xFF242829)
    val OutlineSoft = Color(0x14FFFFFF)
    val TextPrimary = Color(0xFFF3F5EF)
    val TextSecondary = Color(0xFF9AA39B)
    val TextTertiary = Color(0xFF69726B)
    val Accent = Color(0xFFC8F45E)
    val AccentHover = Color(0xFFD7FF79)
    val OnAccent = Color(0xFF101408)
    val Danger = Color(0xFFFF7A72)
    val PlayerBar = Color(0xFF0C0E0F)
    val Overlay = Color(0xCC000000)
}

object RevibeType {
    private val family = FontFamily.SansSerif

    val hero = TextStyle(
        fontFamily = family,
        fontSize = 52.sp,
        lineHeight = 54.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.8).sp,
    )
    val display = TextStyle(
        fontFamily = family,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.7).sp,
    )
    val heading = TextStyle(
        fontFamily = family,
        fontSize = 21.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    )
    val title = TextStyle(
        fontFamily = family,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val body = TextStyle(
        fontFamily = family,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
    )
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
    val label = TextStyle(
        fontFamily = family,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    )
    val caption = TextStyle(
        fontFamily = family,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    )
    val micro = TextStyle(
        fontFamily = family,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    )
}

val LocalAccent = staticCompositionLocalOf { RevibeColors.Accent }

@Composable
fun RevibeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RevibeColors.Accent,
            onPrimary = RevibeColors.OnAccent,
            background = RevibeColors.Base,
            onBackground = RevibeColors.TextPrimary,
            surface = RevibeColors.Surface,
            onSurface = RevibeColors.TextPrimary,
            surfaceVariant = RevibeColors.SurfaceRaised,
            onSurfaceVariant = RevibeColors.TextSecondary,
            outline = RevibeColors.Outline,
            error = RevibeColors.Danger,
        ),
        typography = Typography(
            displayLarge = RevibeType.hero,
            displayMedium = RevibeType.display,
            headlineMedium = RevibeType.heading,
            titleMedium = RevibeType.title,
            bodyMedium = RevibeType.body,
            labelMedium = RevibeType.label,
        ),
        content = content,
    )
}
