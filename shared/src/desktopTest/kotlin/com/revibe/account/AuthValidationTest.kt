package com.revibe.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthValidationTest {

    @Test
    fun `login requires an email and a password`() {
        val empty = AuthValidation.validateLogin("", "")
        assertTrue(empty.hasErrors)
        assertEquals("Enter your email address.", empty.email)
        assertEquals("Enter your password.", empty.password)

        val malformed = AuthValidation.validateLogin("listener@revibe", "secret123")
        assertEquals("Enter a valid email address.", malformed.email)
        assertNull(malformed.password)

        val trimmed = AuthValidation.validateLogin("  listener@revibe.app  ", "secret123")
        assertFalse(trimmed.hasErrors)
    }

    @Test
    fun `signup enforces the eight character minimum and matching confirmation`() {
        val short = AuthValidation.validateSignUp("listener@revibe.app", "1234567", "1234567")
        assertEquals("Use at least 8 characters for your password.", short.password)
        assertNull(short.confirmation)

        val mismatch = AuthValidation.validateSignUp("listener@revibe.app", "12345678", "12345679")
        assertEquals("Passwords do not match.", mismatch.confirmation)

        val missingConfirmation = AuthValidation.validateSignUp("listener@revibe.app", "12345678", "")
        assertEquals("Confirm your password.", missingConfirmation.confirmation)

        val valid = AuthValidation.validateSignUp("listener@revibe.app", "12345678", "12345678")
        assertFalse(valid.hasErrors)
        assertNull(valid.firstMessage)
    }

    @Test
    fun `email format accepts normal addresses and rejects malformed ones`() {
        assertTrue(AuthValidation.isValidEmail("someone@example.com"))
        assertTrue(AuthValidation.isValidEmail("first.last+tag@mail.example.co.uk"))
        assertFalse(AuthValidation.isValidEmail("someone@example"))
        assertFalse(AuthValidation.isValidEmail("someone.example.com"))
        assertFalse(AuthValidation.isValidEmail("someone @example.com"))
        assertFalse(AuthValidation.isValidEmail(""))
    }
}
