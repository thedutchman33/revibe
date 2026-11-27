package com.revibe.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Player-bar glyphs traced from the Spotify desktop reference. These are
 * intentionally not Material icons: the angled microphone, outlined queue pill,
 * single-wave speaker, picture-in-picture square and corner brackets differ
 * from the platform set at this size.
 */
private fun playerIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

private fun ImageVector.Builder.strokePath(block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}

val PlayerLyricsIcon: ImageVector = playerIcon("PlayerLyrics") {
    strokePath {
        // Outlined bulb and capsule handle, open where they meet just like the
        // reference microphone. A plain stroked line reads as a magnifying glass
        // at player-bar size.
        moveTo(15.25f, 4.35f)
        arcTo(4.3f, 4.3f, 0f, false, true, 15.25f, 12.95f)
        arcTo(4.3f, 4.3f, 0f, false, true, 15.25f, 4.35f)
        close()
        moveTo(13.97f, 12.75f)
        lineTo(6.01f, 20.81f)
        arcTo(2.0f, 2.0f, 0f, false, true, 3.19f, 17.99f)
        lineTo(11.15f, 9.93f)
    }
}

val PlayerQueueIcon: ImageVector = playerIcon("PlayerQueue") {
    strokePath {
        // Top row is an outlined pill; the two rows below are solid lines.
        moveTo(6.4f, 4.5f)
        lineTo(17.6f, 4.5f)
        arcTo(1.9f, 1.9f, 0f, false, true, 19.5f, 6.4f)
        lineTo(19.5f, 6.6f)
        arcTo(1.9f, 1.9f, 0f, false, true, 17.6f, 8.5f)
        lineTo(6.4f, 8.5f)
        arcTo(1.9f, 1.9f, 0f, false, true, 4.5f, 6.6f)
        lineTo(4.5f, 6.4f)
        arcTo(1.9f, 1.9f, 0f, false, true, 6.4f, 4.5f)
        close()
        moveTo(5.2f, 12.3f)
        lineTo(18.8f, 12.3f)
        moveTo(5.2f, 16.4f)
        lineTo(18.8f, 16.4f)
    }
}

val PlayerVolumeIcon: ImageVector = playerIcon("PlayerVolume") {
    strokePath {
        moveTo(3.8f, 9.0f)
        lineTo(7.2f, 9.0f)
        lineTo(12.0f, 4.9f)
        lineTo(12.0f, 19.1f)
        lineTo(7.2f, 15.0f)
        lineTo(3.8f, 15.0f)
        close()
        moveTo(15.2f, 9.2f)
        curveTo(16.1f, 9.9f, 16.6f, 10.9f, 16.6f, 12.0f)
        curveTo(16.6f, 13.1f, 16.1f, 14.1f, 15.2f, 14.8f)
    }
}

val PlayerVolumeMutedIcon: ImageVector = playerIcon("PlayerVolumeMuted") {
    strokePath {
        moveTo(3.8f, 9.0f)
        lineTo(7.2f, 9.0f)
        lineTo(12.0f, 4.9f)
        lineTo(12.0f, 19.1f)
        lineTo(7.2f, 15.0f)
        lineTo(3.8f, 15.0f)
        close()
        moveTo(15.2f, 9.5f)
        lineTo(20.0f, 14.5f)
        moveTo(20.0f, 9.5f)
        lineTo(15.2f, 14.5f)
    }
}

val PlayerMiniplayerIcon: ImageVector = playerIcon("PlayerMiniplayer") {
    strokePath {
        moveTo(12.0f, 19.5f)
        lineTo(6.0f, 19.5f)
        arcTo(1.5f, 1.5f, 0f, false, true, 4.5f, 18.0f)
        lineTo(4.5f, 6.0f)
        arcTo(1.5f, 1.5f, 0f, false, true, 6.0f, 4.5f)
        lineTo(18.0f, 4.5f)
        arcTo(1.5f, 1.5f, 0f, false, true, 19.5f, 6.0f)
        lineTo(19.5f, 11.0f)
    }
    path(fill = SolidColor(Color.Black)) {
        moveTo(12.0f, 12.0f)
        lineTo(19.5f, 12.0f)
        lineTo(19.5f, 19.5f)
        lineTo(12.0f, 19.5f)
        close()
    }
}

val PlayerFullscreenIcon: ImageVector = playerIcon("PlayerFullscreen") {
    strokePath {
        moveTo(9.3f, 4.5f)
        lineTo(6.0f, 4.5f)
        arcTo(1.5f, 1.5f, 0f, false, false, 4.5f, 6.0f)
        lineTo(4.5f, 9.3f)
        moveTo(14.7f, 4.5f)
        lineTo(18.0f, 4.5f)
        arcTo(1.5f, 1.5f, 0f, false, true, 19.5f, 6.0f)
        lineTo(19.5f, 9.3f)
        moveTo(9.3f, 19.5f)
        lineTo(6.0f, 19.5f)
        arcTo(1.5f, 1.5f, 0f, false, true, 4.5f, 18.0f)
        lineTo(4.5f, 14.7f)
        moveTo(14.7f, 19.5f)
        lineTo(18.0f, 19.5f)
        arcTo(1.5f, 1.5f, 0f, false, false, 19.5f, 18.0f)
        lineTo(19.5f, 14.7f)
    }
}
