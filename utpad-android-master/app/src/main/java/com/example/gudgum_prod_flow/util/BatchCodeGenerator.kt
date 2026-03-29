package com.example.gudgum_prod_flow.util

import java.util.Calendar
import java.util.Date

/**
 * Generates the daily batch code used across Production, Packing, and Dispatch.
 *
 * Format: {day-digits-as-letters}{MM}{YY}
 *   Digit map: 0=A 1=B 2=C 3=D 4=E 5=F 6=G 7=H 8=I 9=J
 *
 * Example: 18-03-2026  →  day=18 → "BI",  month=03,  year=26  →  "BI0326"
 *
 * One batch code per calendar day — all modules share it.
 */
object BatchCodeGenerator {

    private val DIGIT_TO_LETTER = mapOf(
        '0' to 'A', '1' to 'B', '2' to 'C', '3' to 'D', '4' to 'E',
        '5' to 'F', '6' to 'G', '7' to 'H', '8' to 'I', '9' to 'J',
    )

    fun generate(date: Date = Date()): String {
        val cal = Calendar.getInstance()
        cal.time = date
        val dd = String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))
        val mm = String.format("%02d", cal.get(Calendar.MONTH) + 1)
        val yy = String.format("%02d", cal.get(Calendar.YEAR) % 100)
        val dayLetters = dd.map { DIGIT_TO_LETTER[it]!! }.joinToString("")
        return "$dayLetters$mm$yy"
    }
}
