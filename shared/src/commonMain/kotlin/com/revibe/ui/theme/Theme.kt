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
import org.jetbrains.compose.resources.Font
import revibe.shared.generated.resources.Res
import revibe.shared.generated.resources.inter_bold
import revibe.shared.generated.resources.inter_extrabold
import revibe.shared.generated.resources.inter_medium
import revibe.shared.generated.resources.inter_regular
import revibe.shared.generated.resources.inter_semibold

/**
 * Revibe's own visual language: near-black layered surfaces, a lime signal
 * colour and typography that leans on tight, heavy display weights. Deliberately
 * not a Material 3 default look.
 *
 * Typeface: Spotify's Circular is proprietary and cannot be redistributed.
 * Revibe bundles **Inter** (SIL Open Font License) as the closest open
 * substitute and matches Spotify's sizes, weights, line heights and opacities.
 */
object RevibeColors {
    val Base = Color(0xFF000000)
    val Sidebar = Color(0xFF121212)
    val Surface = Color(0xFF121212)
    val SurfaceRaised = Color(0xFF1F1F1F)
    val SurfaceHover = Color(0xFF2A2A2A)
    val SurfaceActive = Color(0xFF333333)
    val Outline = Color(0xFF2A2A2A)
    val OutlineSoft = Color(0x14FFFFFF)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextTertiary = Color(0xFF7A7A7A)
    val Accent = Color(0xFFC8F45E)
    val AccentHover = Color(0xFFD7FF79)
    val OnAccent = Color(0xFF101408)
    val Danger = Color(0xFFFF7A72)
    val PlayerBar = Color(0xFF000000)
    val Overlay = Color(0xCC000000)

    /** Spotify player-bar metrics, measured against the supplied reference. */
    val PlayerMuted = Color(0xFFD6D6D6)
    val PlayerTime = Color(0xFFB3B3B3)
    val PlayerTrack = Color(0x4DFFFFFF)
}

object RevibeType {
    private var family: FontFamily = FontFamily.SansSerif

    /** Called once by [RevibeTheme] before content is composed. */
    fun useFamily(fontFamily: FontFamily) {
        family = fontFamily
    }

    val hero get() = TextStyle(
        fontFamily = family,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.4).sp,
    )
    val display get() = TextStyle(
        fontFamily = family,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.7).sp,
    )
    val heading get() = TextStyle(
        fontFamily = family,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    )
    val title get() = TextStyle(
        fontFamily = family,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val body get() = TextStyle(
        fontFamily = family,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    )
    val bodyStrong get() = body.copy(fontWeight = FontWeight.SemiBold)
    val label get() = TextStyle(
        fontFamily = family,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
    )
    val caption get() = TextStyle(
        fontFamily = family,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    )
    val micro get() = TextStyle(
        fontFamily = family,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    )

    // Spotify player-bar type scale (measured from the reference capture).
    val playerTitle get() = TextStyle(
        fontFamily = family,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.1).sp,
    )
    val playerArtist get() = TextStyle(
        fontFamily = family,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
    val playerTime get() = TextStyle(
        fontFamily = family,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
}

val LocalAccent = staticCompositionLocalOf { RevibeColors.Accent }

@Composable
fun RevibeTheme(content: @Composable () -> Unit) {
    val inter = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold, FontWeight.Bold),
        Font(Res.font.inter_extrabold, FontWeight.ExtraBold),
    )
    RevibeType.useFamily(inter)

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
