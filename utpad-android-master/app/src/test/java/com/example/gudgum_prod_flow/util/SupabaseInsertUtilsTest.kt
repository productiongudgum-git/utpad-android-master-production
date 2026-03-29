package com.example.gudgum_prod_flow.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseInsertUtilsTest {

    @Test
    fun `duplicate key conflict is detected from postgres code`() {
        val body = """{"code":"23505","message":"duplicate key value violates unique constraint"}"""

        assertTrue(isDuplicateKeyConflict(statusCode = 409, responseBody = body))
    }

    @Test
    fun `duplicate key conflict is not detected for non-conflict status`() {
        val body = """{"code":"23505","message":"duplicate key value violates unique constraint"}"""

        assertFalse(isDuplicateKeyConflict(statusCode = 400, responseBody = body))
    }

    @Test
    fun `insert accepted returns true for successful response`() {
        assertTrue(isInsertAccepted(statusCode = 201, responseBody = ""))
    }

    @Test
    fun `insert accepted treats duplicate as success when enabled`() {
        val body = """{"message":"duplicate key value violates unique constraint"}"""

        assertTrue(isInsertAccepted(statusCode = 409, responseBody = body, treatDuplicateAsSuccess = true))
    }

    @Test
    fun `insert accepted keeps duplicate as failure when disabled`() {
        val body = """{"message":"duplicate key value violates unique constraint"}"""

        assertFalse(isInsertAccepted(statusCode = 409, responseBody = body, treatDuplicateAsSuccess = false))
    }
}
