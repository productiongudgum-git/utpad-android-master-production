package com.example.gudgum_prod_flow.util

import java.util.Locale

fun isDuplicateKeyConflict(statusCode: Int, responseBody: String): Boolean =
    statusCode == 409 && responseBody.lowercase(Locale.ROOT).let { normalized ->
        "\"code\":\"23505\"" in normalized ||
            "\"code\": \"23505\"" in normalized ||
            "duplicate key" in normalized ||
            "unique constraint" in normalized
    }

fun isInsertAccepted(
    statusCode: Int,
    responseBody: String,
    treatDuplicateAsSuccess: Boolean = true,
): Boolean =
    statusCode in 200..299 || (treatDuplicateAsSuccess && isDuplicateKeyConflict(statusCode, responseBody))
