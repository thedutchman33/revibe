package com.revibe.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeType
import kotlinx.coroutines.coroutineScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

@Composable
fun HoverSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    backgroundColor: Color = Color.Transparent,
    hoverColor: Color = RevibeColors.SurfaceHover,
    content: @Composable (hovered: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val clickModifier = when {
        onClick != null -> Modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
        else -> Modifier.hoverable(interaction)
    }
    Box(
        modifier
            .clip(shape)
            .background(if (hovered && hoverColor != Color.Transparent) hoverColor else backgroundColor)
            .then(clickModifier),
    ) {
        content(hovered)
    }
}

@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    tint: Color = RevibeColors.TextSecondary,
    hoverTint: Color = RevibeColors.TextPrimary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .hoverable(interaction)
            .then(
                if (enabled) {
                    Modifier
                        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                        .pointerHoverIcon(PointerIcon.Hand)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> RevibeColors.TextTertiary.copy(alpha = 0.45f)
                hovered -> hoverTint
                else -> tint
            },
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun RevibeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Accent,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    /** Centres the label, for full-width calls to action. */
    centered: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        !enabled -> RevibeColors.SurfaceActive
        kind == ButtonKind.Accent -> if (hovered) RevibeColors.AccentHover else RevibeColors.Accent
        hovered -> RevibeColors.SurfaceHover
        else -> Color.Transparent
    }
    val textColor = when {
        !enabled -> RevibeColors.TextTertiary
        kind == ButtonKind.Accent -> RevibeColors.OnAccent
        else -> RevibeColors.TextPrimary
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .then(
                if (kind == ButtonKind.Outline) Modifier.background(Color.Transparent) else Modifier,
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.spacedBy(8.dp),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
        }
        Text(
            text = text,
            style = RevibeType.label.copy(fontWeight = FontWeight.Bold),
            color = textColor,
        )
    }
}

enum class ButtonKind { Accent, Ghost, Outline }

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 14.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        selected -> RevibeColors.TextPrimary
        hovered -> RevibeColors.SurfaceHover
        else -> RevibeColors.SurfaceRaised
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = horizontalPadding, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = RevibeType.label.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = if (selected) RevibeColors.OnAccent else RevibeColors.TextPrimary,
            maxLines = 1,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    eyebrow: String? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            eyebrow?.let {
                Text(
                    it,
                    style = RevibeType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = RevibeColors.TextPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(
                text = title,
                style = RevibeType.heading.copy(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
                color = RevibeColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = RevibeType.caption, color = RevibeColors.TextSecondary)
            }
        }
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = RevibeType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = RevibeColors.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onAction)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(if (focused) RevibeColors.SurfaceRaised else RevibeColors.Surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = if (focused) RevibeColors.TextPrimary else RevibeColors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = RevibeType.body.copy(color = RevibeColors.TextPrimary),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(RevibeColors.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text("What do you want to play?", style = RevibeType.body, color = RevibeColors.TextTertiary)
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            IconAction(
                icon = Icons.Rounded.Close,
                contentDescription = "Clear search",
                onClick = { onValueChange("") },
                size = 24.dp,
                iconSize = 15.dp,
            )
        }
        trailing?.let {
            Box(Modifier.width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.18f)))
            it()
        }
    }
}

/**
 * Custom seek bar. Material's Slider cannot be made to look like Revibe's thin
 * track with a hover thumb without fighting the component's internals.
 */
@Composable
fun SeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = RevibeColors.Accent,
    enabled: Boolean = true,
    trackHeight: Dp = 4.dp,
    trackColor: Color = Color.White.copy(alpha = 0.16f),
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val onChange by rememberUpdatedState(onValueChange)
    val shown = value.coerceIn(0f, 1f)
    val thumbVisible = hovered || dragging

    Box(
        modifier
            .fillMaxWidth()
            .height(18.dp)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                coroutineScope {
                    launch {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                onChange((offset.x / size.width).coerceIn(0f, 1f))
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                            onHorizontalDrag = { change, _ ->
                                onChange((change.position.x / size.width).coerceIn(0f, 1f))
                                change.consume()
                            },
                        )
                    }
                    launch {
                        detectTapGestures { offset ->
                            onChange((offset.x / size.width).coerceIn(0f, 1f))
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(if (dragging) 6.dp else trackHeight)) {
            val barHeight = size.height
            val radius = CornerRadius(barHeight / 2f, barHeight / 2f)
            drawRoundRect(
                color = if (enabled) trackColor else Color.White.copy(alpha = 0.07f),
                size = Size(size.width, barHeight),
                cornerRadius = radius,
            )
            if (shown > 0f) {
                drawRoundRect(
                    color = if (enabled) accent else RevibeColors.TextTertiary,
                    size = Size(size.width * shown, barHeight),
                    cornerRadius = radius,
                )
            }
            if (thumbVisible && enabled) {
                drawCircle(
                    color = Color.White,
                    radius = 5.5.dp.toPx(),
                    center = Offset(size.width * shown, barHeight / 2f),
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = RevibeType.title, color = RevibeColors.TextPrimary)
        Text(message, style = RevibeType.body, color = RevibeColors.TextSecondary, maxLines = 3)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            RevibeButton(action, onAction, kind = ButtonKind.Outline)
        }
    }
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(RevibeColors.SurfaceRaised, RevibeColors.Surface, RevibeColors.SurfaceRaised),
                ),
            ),
    )
}

@Composable
fun CaptionLabel(text: String, modifier: Modifier = Modifier, color: Color = RevibeColors.TextSecondary) {
    Text(
        text = text.uppercase(),
        style = RevibeType.micro,
        color = color,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun MiniBadge(text: String, modifier: Modifier = Modifier, color: Color = RevibeColors.TextSecondary) {
    Text(
        text = text.uppercase(),
        style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
fun HDivider(modifier: Modifier = Modifier, color: Color = RevibeColors.OutlineSoft) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun VDivider(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    Spacer(modifier.width(1.dp).height(height).background(RevibeColors.Outline))
}

/** Lightweight modal overlay. Avoids platform dialog behaviour differences. */
@Composable
fun Modal(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val backdrop = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.66f))
            .clickable(interactionSource = backdrop, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RevibeColors.SurfaceRaised)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(22.dp),
        ) {
            content()
        }
    }
}

@Composable
fun RevibeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(RevibeColors.Surface)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            textStyle = RevibeType.body.copy(color = RevibeColors.TextPrimary),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(RevibeColors.Accent),
            visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, style = RevibeType.body, color = RevibeColors.TextTertiary)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
fun RevibeSwitch(checked: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .width(40.dp)
            .height(22.dp)
            .clip(CircleShape)
            .background(if (checked) RevibeColors.Accent else RevibeColors.SurfaceActive)
            .clickable(onClick = onToggle)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) RevibeColors.OnAccent else RevibeColors.TextSecondary),
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
            subtitle?.let { Text(it, style = RevibeType.caption, color = RevibeColors.TextSecondary) }
        }
        RevibeSwitch(checked, onToggle)
    }
}
