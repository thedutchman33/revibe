package com.revibe.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revibe.account.AccountState
import com.revibe.account.AccountUser
import com.revibe.account.AuthFormErrors
import com.revibe.account.AuthValidation
import com.revibe.imports.ImportHistoryItem
import com.revibe.platform.relativeTime
import com.revibe.sync.SyncPhase
import com.revibe.sync.SyncStatus
import com.revibe.ui.AppViewModel
import com.revibe.ui.components.Artwork
import com.revibe.ui.components.ButtonKind
import com.revibe.ui.components.HDivider
import com.revibe.ui.components.Modal
import com.revibe.ui.components.RevibeButton
import com.revibe.ui.components.RevibeTextField
import com.revibe.ui.components.ToggleRow
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeType

/**
 * The supplied authentication reference uses a red primary action. Everything
 * else follows Revibe's own dark surface palette; no Spotify branding is used.
 */
private val AuthAccent = Color(0xFFE1332B)
private val AuthAccentHover = Color(0xFFF04A42)
private val GoogleSurface = Color(0xFFFFFFFF)
private val GoogleSurfaceHover = Color(0xFFF2F2F2)
private val GoogleInk = Color(0xFF1F1F1F)

private enum class AuthMode { LogIn, SignUp, ForgotPassword }

@Composable
fun AccountScreen(vm: AppViewModel) {
    val state by vm.accountState.collectAsState()
    val sync by vm.syncStatus.collectAsState()
    val current = state

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Account", style = RevibeType.display.copy(fontSize = 32.sp), color = RevibeColors.TextPrimary)
            }
            when (current) {
                AccountState.Restoring -> item {
                    CenteredPanel(460.dp) { LoadingCard("Restoring your session…") }
                }
                is AccountState.NotConfigured -> item {
                    CenteredPanel(760.dp) {
                        SignedOutAccountCard(
                            vm = vm,
                            sync = sync,
                            setupError = current.reason,
                            onLogin = vm::openAuthModal,
                        )
                    }
                }
                is AccountState.SignedOut -> item {
                    CenteredPanel(760.dp) {
                        SignedOutAccountCard(
                            vm = vm,
                            sync = sync,
                            stateMessage = current.message,
                            onLogin = vm::openAuthModal,
                        )
                    }
                }
                is AccountState.AwaitingConfirmation -> item {
                    CenteredPanel(460.dp) { ConfirmationCard(vm, current.email) }
                }
                is AccountState.SignedIn -> item {
                    CenteredPanel(760.dp) { SignedInContent(vm, current.user, sync) }
                }
            }
            item { CenteredPanel(760.dp) { ImportSection(vm) } }
        }
    }
}

/** App-level wrapper so the login/sign-up dialog can open from the profile menu. */
@Composable
fun AccountAuthModal(vm: AppViewModel) {
    val state by vm.accountState.collectAsState()
    LaunchedEffect(state) {
        if (state is AccountState.SignedIn || state is AccountState.AwaitingConfirmation) {
            vm.showAuthModal = false
        }
    }
    if (!vm.showAuthModal) return
    AuthModal(
        vm = vm,
        state = state,
        onDismiss = vm::closeAuthModal,
    )
}

@Composable
private fun CenteredPanel(maxWidth: Dp, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = maxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun SignedOutAccountCard(
    vm: AppViewModel,
    sync: SyncStatus,
    onLogin: () -> Unit,
    stateMessage: String? = null,
    setupError: String? = null,
) {
    AccountCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(RevibeColors.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Person, null, tint = RevibeColors.TextSecondary, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Account & sync", style = RevibeType.heading.copy(fontSize = 20.sp), color = RevibeColors.TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(7.dp).clip(CircleShape).background(syncColor(sync.phase)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(syncLabel(sync.phase), style = RevibeType.caption, color = syncColor(sync.phase))
                    if (sync.pendingChanges > 0) {
                        Text(
                            " · ${sync.pendingChanges} pending",
                            style = RevibeType.caption,
                            color = RevibeColors.TextTertiary,
                        )
                    }
                }
            }
            RevibeButton("Log in", onLogin, enabled = !vm.authBusy)
        }
        val message = vm.authError ?: vm.accountNotice ?: stateMessage
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            MessageBanner(message, isError = vm.authError != null)
        }
        if (setupError != null) {
            Spacer(Modifier.height(12.dp))
            SetupNotice(setupError)
        }
    }
}

@Composable
private fun AuthModal(
    vm: AppViewModel,
    state: AccountState,
    onDismiss: () -> Unit,
) {
    Modal(onDismiss = { if (!vm.authBusy) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close login",
                    tint = RevibeColors.TextSecondary,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !vm.authBusy, onClick = onDismiss)
                        .padding(6.dp),
                )
            }
            AuthCardContent(
                busy = vm.authBusy,
                error = vm.authError,
                notice = vm.accountNotice,
                onSignIn = vm::signIn,
                onSignUp = vm::signUp,
                onGoogle = vm::signInWithGoogle,
                onForgotPassword = vm::requestPasswordReset,
                onClearMessages = vm::clearAccountMessages,
                stateMessage = (state as? AccountState.SignedOut)?.message,
                setupError = (state as? AccountState.NotConfigured)?.reason,
                showCard = false,
            )
        }
    }
}

@Composable
private fun ImportSection(vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AccountCard {
            Text("Import your music", style = RevibeType.heading.copy(fontSize = 20.sp), color = RevibeColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RevibeButton("Import from Spotify", vm::beginSpotifyImport, icon = Icons.Rounded.FileDownload)
                RevibeButton("Import a playlist file", vm::beginPlaylistImport, kind = ButtonKind.Outline)
            }
        }
        ImportHistoryCard(vm)
    }
}

/**
 * Stateless login/sign-up card so tab switching, validation and busy states can
 * be exercised without a live Supabase project.
 */
@Composable
internal fun AuthCardContent(
    busy: Boolean,
    error: String?,
    notice: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onGoogle: () -> Unit,
    onForgotPassword: (String) -> Unit,
    onClearMessages: () -> Unit = {},
    stateMessage: String? = null,
    setupError: String? = null,
    showCard: Boolean = true,
) {
    var mode by remember { mutableStateOf(AuthMode.LogIn) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(AuthFormErrors()) }

    fun switchTo(next: AuthMode) {
        if (mode == next) return
        mode = next
        password = ""
        confirmation = ""
        showPassword = false
        showConfirmation = false
        errors = AuthFormErrors()
        onClearMessages()
    }

    fun submit() {
        if (mode == AuthMode.ForgotPassword) {
            val problem = AuthValidation.validateEmail(email)
            errors = AuthFormErrors(email = problem)
            if (problem == null) {
                onClearMessages()
                onForgotPassword(email.trim())
            }
            return
        }
        val result = when (mode) {
            AuthMode.LogIn -> AuthValidation.validateLogin(email, password)
            AuthMode.SignUp -> AuthValidation.validateSignUp(email, password, confirmation)
            AuthMode.ForgotPassword -> AuthFormErrors()
        }
        errors = result
        if (result.hasErrors) return
        onClearMessages()
        if (mode == AuthMode.LogIn) onSignIn(email.trim(), password) else onSignUp(email.trim(), password)
    }

    fun openForgotPassword() {
        mode = AuthMode.ForgotPassword
        errors = AuthFormErrors()
        showPassword = false
        showConfirmation = false
        onClearMessages()
    }

    AuthCardFrame(showCard) {
        if (mode == AuthMode.ForgotPassword) {
            Text(
                "← Back to log in",
                style = RevibeType.label.copy(fontWeight = FontWeight.SemiBold),
                color = RevibeColors.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = !busy) { switchTo(AuthMode.LogIn) }
                    .pointerHoverIcon(if (busy) PointerIcon.Default else PointerIcon.Hand)
                    .padding(vertical = 4.dp, horizontal = 2.dp)
                    .semantics { contentDescription = "Back to log in" },
            )
        } else {
            AuthSegmented(
                mode = mode,
                enabled = !busy,
                onSelect = ::switchTo,
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            when (mode) {
                AuthMode.LogIn -> "Log in to Revibe"
                AuthMode.SignUp -> "Create your account"
                AuthMode.ForgotPassword -> "Reset your password"
            },
            style = RevibeType.heading.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            color = RevibeColors.TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when (mode) {
                AuthMode.LogIn -> "Welcome back. Your playlists, likes and listening history stay in step across devices."
                AuthMode.SignUp -> "One account for your playlists, likes, follows and listening history."
                AuthMode.ForgotPassword -> "Enter the email for your Revibe account and we'll send you a link to reset your password."
            },
            style = RevibeType.caption,
            color = RevibeColors.TextSecondary,
        )
        Spacer(Modifier.height(20.dp))
        if (mode != AuthMode.ForgotPassword) {
            GoogleButton(
                text = if (mode == AuthMode.LogIn) "Continue with Google" else "Sign up with Google",
                enabled = !busy,
                onClick = {
                    onClearMessages()
                    onGoogle()
                },
            )
            Spacer(Modifier.height(18.dp))
            OrDivider()
            Spacer(Modifier.height(18.dp))
        }
        AuthField(
            label = "Email",
            value = email,
            onValueChange = { email = it; errors = errors.copy(email = null) },
            placeholder = "name@example.com",
            error = errors.email,
            enabled = !busy,
            keyboardType = KeyboardType.Email,
            imeAction = if (mode == AuthMode.ForgotPassword) ImeAction.Done else ImeAction.Next,
        )
        if (mode != AuthMode.ForgotPassword) {
            Spacer(Modifier.height(12.dp))
            AuthField(
                label = "Password",
                value = password,
                onValueChange = { password = it; errors = errors.copy(password = null) },
                placeholder = "Your password",
                error = errors.password,
                helper = if (mode == AuthMode.SignUp) "Use at least ${AuthValidation.MIN_PASSWORD_LENGTH} characters." else null,
                isPassword = true,
                visible = showPassword,
                onToggleVisibility = { showPassword = !showPassword },
                enabled = !busy,
                imeAction = if (mode == AuthMode.LogIn) ImeAction.Done else ImeAction.Next,
            )
            if (mode == AuthMode.LogIn) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Forgot password?",
                    style = RevibeType.label.copy(fontWeight = FontWeight.SemiBold),
                    color = RevibeColors.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = !busy, onClick = ::openForgotPassword)
                        .pointerHoverIcon(if (busy) PointerIcon.Default else PointerIcon.Hand)
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .semantics { contentDescription = "Forgot password" },
                )
            } else {
                Spacer(Modifier.height(12.dp))
                AuthField(
                    label = "Confirm password",
                    value = confirmation,
                    onValueChange = { confirmation = it; errors = errors.copy(confirmation = null) },
                    placeholder = "Repeat your password",
                    error = errors.confirmation,
                    isPassword = true,
                    visible = showConfirmation,
                    visibilityLabel = "confirmation password",
                    onToggleVisibility = { showConfirmation = !showConfirmation },
                    enabled = !busy,
                    imeAction = ImeAction.Done,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        AuthPrimaryButton(
            text = when (mode) {
                AuthMode.LogIn -> "Log in"
                AuthMode.SignUp -> "Create account"
                AuthMode.ForgotPassword -> "Send reset link"
            },
            busyText = when (mode) {
                AuthMode.LogIn -> "Logging in…"
                AuthMode.SignUp -> "Creating account…"
                AuthMode.ForgotPassword -> "Sending reset link…"
            },
            busy = busy,
            onClick = ::submit,
        )
        val message = error ?: notice ?: stateMessage
        if (message != null) {
            Spacer(Modifier.height(14.dp))
            MessageBanner(message, isError = error != null || (stateMessage != null && notice == null && error == null))
        }
        if (setupError != null) {
            Spacer(Modifier.height(14.dp))
            SetupNotice(setupError)
        }
    }
}

@Composable
private fun AuthCardFrame(showCard: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (showCard) {
        AccountCard(content = content)
    } else {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun AuthSegmented(mode: AuthMode, enabled: Boolean, onSelect: (AuthMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(RevibeColors.Surface)
            .padding(4.dp),
    ) {
        AuthSegment("Login", mode == AuthMode.LogIn, enabled, Modifier.weight(1f)) { onSelect(AuthMode.LogIn) }
        AuthSegment("Sign up", mode == AuthMode.SignUp, enabled, Modifier.weight(1f)) { onSelect(AuthMode.SignUp) }
    }
}

@Composable
private fun AuthSegment(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier
            .clip(CircleShape)
            .background(
                when {
                    selected -> RevibeColors.SurfaceActive
                    hovered && enabled -> RevibeColors.SurfaceHover
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !selected, onClick = onClick)
            .pointerHoverIcon(if (enabled && !selected) PointerIcon.Hand else PointerIcon.Default)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text,
            style = RevibeType.label.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
            color = if (selected) RevibeColors.TextPrimary else RevibeColors.TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun GoogleButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(if (hovered && enabled) GoogleSurfaceHover else GoogleSurface)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        GoogleMark(20.dp)
        Spacer(Modifier.size(10.dp))
        Text(
            text,
            style = RevibeType.label.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
            color = GoogleInk,
            maxLines = 1,
        )
    }
}

/** Four-colour Google mark. Revibe's own drawing; no Google asset is redistributed. */
@Composable
private fun GoogleMark(size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.22f
        val radius = (this.size.minDimension - stroke) / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        fun arc(color: Color, start: Float, sweep: Float) {
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
        }
        arc(Color(0xFFEA4335), 206f, 106f)  // red, top
        arc(Color(0xFF4285F4), 0f, 49f)     // blue, below the crossbar
        arc(Color(0xFF34A853), 49f, 111f)   // green, bottom right to bottom left
        arc(Color(0xFFFBBC05), 160f, 46f)   // yellow, left
        drawRect(
            color = Color(0xFF4285F4),
            topLeft = Offset(center.x, center.y - stroke / 2f),
            size = Size(radius + stroke / 2f, stroke),
        )
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HDivider(Modifier.weight(1f), color = RevibeColors.Outline)
        Text(
            "OR USE EMAIL",
            style = RevibeType.micro.copy(fontSize = 10.sp),
            color = RevibeColors.TextTertiary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HDivider(Modifier.weight(1f), color = RevibeColors.Outline)
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    helper: String? = null,
    isPassword: Boolean = false,
    visible: Boolean = false,
    /** Which password this field is, so Show/Hide controls stay distinguishable. */
    visibilityLabel: String = "password",
    onToggleVisibility: (() -> Unit)? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            style = RevibeType.label.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
            color = RevibeColors.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(RevibeColors.Surface)
                .border(
                    width = 1.dp,
                    color = when {
                        error != null -> RevibeColors.Danger
                        focused -> RevibeColors.TextTertiary
                        else -> RevibeColors.Outline
                    },
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = RevibeType.body.copy(color = RevibeColors.TextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(AuthAccent),
                visualTransformation = if (isPassword && !visible) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(placeholder, style = RevibeType.body.copy(fontSize = 15.sp), color = RevibeColors.TextTertiary)
                        }
                        inner()
                    }
                },
            )
            if (isPassword && onToggleVisibility != null) {
                Text(
                    if (visible) "Hide" else "Show",
                    style = RevibeType.caption.copy(fontWeight = FontWeight.Bold),
                    color = RevibeColors.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = enabled, onClick = onToggleVisibility)
                        .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .semantics {
                            contentDescription = if (visible) "Hide $visibilityLabel" else "Show $visibilityLabel"
                        },
                )
            }
        }
        if (error != null) {
            Spacer(Modifier.height(5.dp))
            Text(error, style = RevibeType.caption, color = RevibeColors.Danger)
        } else if (helper != null) {
            Spacer(Modifier.height(5.dp))
            Text(helper, style = RevibeType.caption, color = RevibeColors.TextTertiary)
        }
    }
}

@Composable
private fun AuthPrimaryButton(text: String, busyText: String, busy: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(
                when {
                    busy -> AuthAccent.copy(alpha = 0.65f)
                    hovered -> AuthAccentHover
                    else -> AuthAccent
                },
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = !busy, onClick = onClick)
            .pointerHoverIcon(if (busy) PointerIcon.Default else PointerIcon.Hand),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(17.dp))
            Spacer(Modifier.size(10.dp))
        }
        Text(
            if (busy) busyText else text,
            style = RevibeType.label.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun SetupNotice(reason: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RevibeColors.Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.CloudOff, null, tint = RevibeColors.TextSecondary, modifier = Modifier.size(16.dp))
            Text("Sign-in is not configured", style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
        }
        Text(reason, style = RevibeType.caption, color = RevibeColors.TextSecondary)
        Text(
            "Set REVIBE_SUPABASE_URL and REVIBE_SUPABASE_ANON_KEY, enable the Google provider in Supabase, then restart Revibe.",
            style = RevibeType.caption,
            color = RevibeColors.TextTertiary,
        )
    }
}

@Composable
private fun ImportHistoryCard(vm: AppViewModel) {
    ImportHistoryCardContent(
        items = vm.importHistory,
        loading = vm.importHistoryLoading,
        busy = vm.undoImportBusy,
        onRefresh = vm::refreshImportHistory,
        onUndo = vm::undoImport,
    )
}

@Composable
internal fun ImportHistoryCardContent(
    items: List<ImportHistoryItem>,
    loading: Boolean,
    busy: Boolean,
    onRefresh: () -> Unit,
    onUndo: (String) -> Unit,
) {
    var confirmUndo by remember { mutableStateOf<String?>(null) }
    AccountCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Import history", style = RevibeType.heading.copy(fontSize = 20.sp), color = RevibeColors.TextPrimary, modifier = Modifier.weight(1f))
            if (loading) {
                CircularProgressIndicator(color = RevibeColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
            RevibeButton("Refresh", onRefresh, kind = ButtonKind.Ghost, enabled = !loading && !busy)
        }
        Spacer(Modifier.height(10.dp))
        if (items.isEmpty()) {
            Text(
                if (loading) "Loading imports…" else "No imports yet.",
                style = RevibeType.body,
                color = RevibeColors.TextTertiary,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { item ->
                    ImportHistoryRow(
                        item = item,
                        busy = busy,
                        confirming = confirmUndo == item.batch.importId,
                        onConfirm = { confirmUndo = item.batch.importId },
                        onCancel = { confirmUndo = null },
                        onUndo = {
                            confirmUndo = null
                            onUndo(item.batch.importId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportHistoryRow(
    item: ImportHistoryItem,
    busy: Boolean,
    confirming: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
) {
    val batch = item.batch
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RevibeColors.Surface)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    importSourceLabel(batch.sourcePlatform),
                    style = RevibeType.bodyStrong,
                    color = RevibeColors.TextPrimary,
                )
                Text(
                    "Imported ${relativeTime(batch.importedAt)}",
                    style = RevibeType.caption,
                    color = RevibeColors.TextSecondary,
                )
            }
            if (item.canUndo) {
                if (confirming) {
                    RevibeButton("Undo import", onUndo, kind = ButtonKind.Outline, enabled = !busy)
                    RevibeButton("Cancel", onCancel, kind = ButtonKind.Ghost, enabled = !busy)
                } else {
                    RevibeButton("Undo", onConfirm, kind = ButtonKind.Ghost, enabled = !busy)
                }
            } else {
                Text(
                    if (item.undone) "Undone" else "No undo data",
                    style = RevibeType.caption,
                    color = RevibeColors.TextTertiary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            importCountSummary(batch),
            style = RevibeType.caption,
            color = RevibeColors.TextSecondary,
        )
        if (confirming) {
            Spacer(Modifier.height(6.dp))
            Text(
                "This cannot be undone after it runs. Only records owned by this import are removed.",
                style = RevibeType.caption,
                color = RevibeColors.Danger,
            )
        }
    }
}

private fun importSourceLabel(platform: String): String = when (platform) {
    "spotify" -> "Spotify account archive"
    "tune_my_music" -> "Playlist file"
    else -> platform.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun importCountSummary(item: com.revibe.imports.ImportBatchRecord): String {
    fun counted(count: Int, singular: String, plural: String = singular + "s"): String =
        "$count ${if (count == 1) singular else plural}"
    val first = listOf(
        counted(item.playlists, "playlist"),
        counted(item.tracks, "track"),
        counted(item.likes, "like"),
        counted(item.artists, "artist"),
    ).joinToString(" · ")
    val second = listOf(
        counted(item.albums, "album"),
        counted(item.history, "listening event"),
        counted(item.searches, "search", "searches"),
    ).joinToString(" · ")
    return "$first\n$second"
}

@Composable
private fun ConfirmationCard(vm: AppViewModel, email: String) {
    AccountCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.CloudDone, null, tint = RevibeColors.Accent, modifier = Modifier.size(24.dp))
            Column {
                Text("Confirm your email", style = RevibeType.heading.copy(fontSize = 20.sp), color = RevibeColors.TextPrimary)
                Text(email, style = RevibeType.caption, color = RevibeColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "We sent a confirmation link. Open it, then come back and log in. Your local library is untouched until the account is active.",
            style = RevibeType.body,
            color = RevibeColors.TextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        RevibeButton("Back to log in", { vm.signOut() }, kind = ButtonKind.Outline)
    }
}

@Composable
private fun SignedInContent(vm: AppViewModel, user: AccountUser, sync: SyncStatus) {
    var editing by remember(user.id) { mutableStateOf(false) }
    var displayName by remember(user.id) { mutableStateOf(user.displayName.orEmpty()) }
    var avatarUrl by remember(user.id) { mutableStateOf(user.avatarUrl.orEmpty()) }

    AccountCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (user.avatarUrl.isNullOrBlank()) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(RevibeColors.SurfaceActive),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(user.initials, style = RevibeType.heading, color = RevibeColors.TextPrimary)
                }
            } else {
                Artwork(user.avatarUrl, Modifier.size(64.dp), cornerRadius = 32.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    user.displayName?.takeIf { it.isNotBlank() } ?: user.email.substringBefore('@'),
                    style = RevibeType.heading.copy(fontSize = 22.sp),
                    color = RevibeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(user.email, style = RevibeType.caption, color = RevibeColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RevibeButton(if (editing) "Close" else "Edit profile", { editing = !editing }, kind = ButtonKind.Outline)
                RevibeButton("Sign out", { vm.signOut() }, kind = ButtonKind.Ghost, icon = Icons.Rounded.Logout, enabled = !vm.authBusy)
            }
        }
        if (editing) {
            Spacer(Modifier.height(14.dp))
            RevibeTextField(displayName, { displayName = it }, "Display name", enabled = !vm.authBusy)
            Spacer(Modifier.height(10.dp))
            RevibeTextField(avatarUrl, { avatarUrl = it }, "Avatar image URL (optional)", enabled = !vm.authBusy)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RevibeButton("Save profile", { vm.updateProfile(displayName, avatarUrl) }, enabled = !vm.authBusy)
                RevibeButton("Cancel", { displayName = user.displayName.orEmpty(); avatarUrl = user.avatarUrl.orEmpty(); editing = false }, kind = ButtonKind.Ghost)
            }
        }
        val message = vm.authError ?: vm.accountNotice
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            MessageBanner(message, isError = vm.authError != null)
        }
    }

    AccountCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SyncIcon(sync.phase)
            Column(Modifier.weight(1f)) {
                Text("Sync", style = RevibeType.heading.copy(fontSize = 20.sp), color = RevibeColors.TextPrimary)
                Text(syncLabel(sync.phase), style = RevibeType.caption, color = syncColor(sync.phase))
            }
            RevibeButton(
                text = if (sync.phase == SyncPhase.SYNCING) "Syncing…" else "Sync now",
                onClick = vm::syncNow,
                kind = ButtonKind.Outline,
                icon = Icons.Rounded.Sync,
                enabled = sync.phase != SyncPhase.SYNCING,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SyncStatTile(
                label = "Last sync",
                value = sync.lastSyncedAt?.let { relativeTime(it) } ?: "Never",
                modifier = Modifier.weight(1f),
            )
            SyncStatTile(
                label = "Pending",
                value = if (sync.pendingChanges == 0) "None" else "${sync.pendingChanges} changes",
                modifier = Modifier.weight(1f),
            )
            SyncStatTile(
                label = "Device",
                value = if (sync.deviceId.isNullOrBlank()) "Not registered" else "This device",
                modifier = Modifier.weight(1f),
            )
        }
        if (!sync.message.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            MessageBanner(sync.message, isError = sync.phase == SyncPhase.ERROR)
        }
        Spacer(Modifier.height(6.dp))
        ToggleRow(
            "Sync search history",
            null,
            sync.searchHistorySynced,
            { vm.setSearchHistorySync(!sync.searchHistorySynced) },
        )
    }
}

@Composable
private fun SyncIcon(phase: SyncPhase) {
    val icon = when (phase) {
        SyncPhase.IDLE -> Icons.Rounded.CloudDone
        SyncPhase.OFFLINE, SyncPhase.ERROR, SyncPhase.NOT_CONFIGURED -> Icons.Rounded.CloudOff
        SyncPhase.SYNCING -> Icons.Rounded.Sync
        else -> Icons.Rounded.CloudDone
    }
    Icon(icon, null, tint = syncColor(phase), modifier = Modifier.size(24.dp))
}

@Composable
private fun SyncStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RevibeColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = RevibeType.caption, color = RevibeColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(value, style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MessageBanner(message: String, isError: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isError) RevibeColors.Danger.copy(alpha = 0.12f) else RevibeColors.SurfaceRaised)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CloudDone,
            null,
            tint = if (isError) RevibeColors.Danger else RevibeColors.Accent,
            modifier = Modifier.size(18.dp),
        )
        Text(message, style = RevibeType.caption, color = if (isError) RevibeColors.Danger else RevibeColors.TextPrimary)
    }
}

@Composable
private fun LoadingCard(message: String) {
    AccountCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = RevibeColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            Text(message, style = RevibeType.body, color = RevibeColors.TextSecondary)
        }
    }
}

@Composable
private fun AccountCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RevibeColors.SurfaceRaised)
            .padding(18.dp),
        content = content,
    )
}

private fun syncLabel(phase: SyncPhase): String = when (phase) {
    SyncPhase.NOT_CONFIGURED -> "Cloud sync is not configured"
    SyncPhase.RESTORING -> "Restoring session"
    SyncPhase.SIGNED_OUT -> "Not signed in"
    SyncPhase.IDLE -> "Up to date"
    SyncPhase.SYNCING -> "Syncing changes…"
    SyncPhase.OFFLINE -> "Offline — changes are queued"
    SyncPhase.ERROR -> "Sync needs attention"
}

private fun syncColor(phase: SyncPhase): Color = when (phase) {
    SyncPhase.IDLE -> RevibeColors.Accent
    SyncPhase.SYNCING -> RevibeColors.TextPrimary
    SyncPhase.OFFLINE -> RevibeColors.TextSecondary
    SyncPhase.ERROR -> RevibeColors.Danger
    else -> RevibeColors.TextTertiary
}
