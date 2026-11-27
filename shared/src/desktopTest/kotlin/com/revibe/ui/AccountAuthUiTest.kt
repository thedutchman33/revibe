package com.revibe.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.revibe.ui.screens.AuthCardContent
import com.revibe.ui.theme.RevibeTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AccountAuthUiTest {

    @Composable
    private fun AuthHarness(
        busy: Boolean = false,
        error: String? = null,
        setupError: String? = null,
        onSignIn: (String, String) -> Unit = { _, _ -> },
        onSignUp: (String, String) -> Unit = { _, _ -> },
        onGoogle: () -> Unit = {},
        onForgot: (String) -> Unit = {},
    ) {
        RevibeTheme {
            Column(Modifier.width(420.dp).verticalScroll(rememberScrollState())) {
                AuthCardContent(
                    busy = busy,
                    error = error,
                    notice = null,
                    onSignIn = onSignIn,
                    onSignUp = onSignUp,
                    onGoogle = onGoogle,
                    onForgotPassword = onForgot,
                    setupError = setupError,
                )
            }
        }
    }

    @Test
    fun `login is the default tab and sign up reveals the confirmation field`() = runComposeUiTest {
        setContent { AuthHarness() }

        onNodeWithText("Log in to Revibe").assertIsDisplayed()
        onNodeWithText("Continue with Google").assertIsDisplayed()
        onNodeWithText("OR USE EMAIL").assertIsDisplayed()
        onNodeWithText("Forgot password?").assertExists()
        onNodeWithText("Use at least 8 characters.", substring = true).assertDoesNotExist()

        onNodeWithText("Sign up").performScrollTo().performClick()

        onNodeWithText("Create your account").assertExists()
        onNodeWithText("Sign up with Google").assertExists()
        onNodeWithText("Use at least 8 characters.", substring = true).assertExists()
        onNodeWithContentDescription("Confirm password").assertExists()
        onNodeWithText("Forgot password?").assertDoesNotExist()

        onNodeWithText("Login").performScrollTo().performClick()
        onNodeWithText("Log in to Revibe").assertExists()
        onNodeWithContentDescription("Confirm password").assertDoesNotExist()
    }

    @Test
    fun `empty login is rejected before any request is sent`() = runComposeUiTest {
        var attempts = 0
        setContent { AuthHarness(onSignIn = { _, _ -> attempts++ }) }

        onNodeWithText("Log in").performScrollTo().performClick()

        onNodeWithText("Enter your email address.").assertExists()
        onNodeWithText("Enter your password.").assertExists()
        assertEquals(0, attempts)
    }

    @Test
    fun `signup validates email password length and confirmation then submits trimmed values`() = runComposeUiTest {
        var submitted: Pair<String, String>? = null
        setContent { AuthHarness(onSignUp = { email, password -> submitted = email to password }) }

        onNodeWithText("Sign up").performScrollTo().performClick()
        onNodeWithContentDescription("Email").performScrollTo().performTextInput("listener@revibe")
        onNodeWithContentDescription("Password").performScrollTo().performTextInput("short")
        onNodeWithContentDescription("Confirm password").performScrollTo().performTextInput("different")
        onNodeWithText("Create account").performScrollTo().performClick()

        onNodeWithText("Enter a valid email address.").assertExists()
        onNodeWithText("Use at least 8 characters for your password.").assertExists()
        onNodeWithText("Passwords do not match.").assertExists()
        assertEquals(null, submitted)

        onNodeWithContentDescription("Email").performScrollTo().performTextClearance()
        onNodeWithContentDescription("Email").performTextInput("  listener@revibe.app  ")
        onNodeWithContentDescription("Password").performScrollTo().performTextClearance()
        onNodeWithContentDescription("Password").performTextInput("secret123")
        onNodeWithContentDescription("Confirm password").performScrollTo().performTextClearance()
        onNodeWithContentDescription("Confirm password").performTextInput("secret123")
        onNodeWithText("Create account").performScrollTo().performClick()

        assertEquals("listener@revibe.app" to "secret123", submitted)
    }

    @Test
    fun `show and hide controls toggle each password field independently`() = runComposeUiTest {
        setContent { AuthHarness() }

        onNodeWithText("Sign up").performScrollTo().performClick()
        onNodeWithContentDescription("Password").performScrollTo().performTextInput("secret123")

        onNodeWithContentDescription("Show password").performScrollTo().performClick()
        onNodeWithContentDescription("Hide password").assertExists()

        onNodeWithContentDescription("Show confirmation password").performScrollTo().performClick()
        onNodeWithContentDescription("Hide confirmation password").assertExists()
        // The main field is still revealed; toggling one never reveals the other.
        onNodeWithContentDescription("Hide password").assertExists()
    }

    @Test
    fun `loading states replace the action label for login and signup`() = runComposeUiTest {
        val busy = mutableStateOf(false)
        setContent { AuthHarness(busy = busy.value) }

        onNodeWithText("Sign up").performScrollTo().performClick()
        busy.value = true
        waitForIdle()
        onNodeWithText("Creating account…").assertExists()

        busy.value = false
        waitForIdle()
        onNodeWithText("Login").performScrollTo().performClick()
        busy.value = true
        waitForIdle()
        onNodeWithText("Logging in…").assertExists()
    }

    @Test
    fun `server errors and setup errors are surfaced`() = runComposeUiTest {
        val error = mutableStateOf<String?>(null)
        setContent {
            AuthHarness(
                error = error.value,
                setupError = "Cloud sync is not configured. Set REVIBE_SUPABASE_URL and REVIBE_SUPABASE_ANON_KEY.",
            )
        }

        onNodeWithText("Sign-in is not configured").performScrollTo().assertIsDisplayed()

        error.value = "An account with this email already exists."
        waitForIdle()
        onNodeWithText("An account with this email already exists.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `forgot password opens its own section validates the email then requests a reset`() = runComposeUiTest {
        val resetFor = mutableListOf<String>()
        setContent { AuthHarness(onForgot = { resetFor += it }) }

        onNodeWithText("Forgot password?").performScrollTo().performClick()
        onNodeWithText("Reset your password").assertExists()
        onNodeWithText("Send reset link").performScrollTo().performClick()
        onNodeWithText("Enter your email address.").assertExists()
        assertEquals(emptyList(), resetFor)

        onNodeWithContentDescription("Email").performScrollTo().performTextInput("listener@revibe.app")
        onNodeWithText("Send reset link").performScrollTo().performClick()
        assertEquals(listOf("listener@revibe.app"), resetFor)

        onNodeWithContentDescription("Back to log in").performScrollTo().performClick()
        onNodeWithText("Log in to Revibe").assertExists()
    }

    @Test
    fun `google button starts oauth from both tabs`() = runComposeUiTest {
        var googleCalls = 0
        setContent { AuthHarness(onGoogle = { googleCalls++ }) }

        onNodeWithText("Continue with Google").performScrollTo().performClick()
        assertEquals(1, googleCalls)

        onNodeWithText("Sign up").performScrollTo().performClick()
        onNodeWithText("Sign up with Google").performScrollTo().performClick()
        assertEquals(2, googleCalls)
    }
}
