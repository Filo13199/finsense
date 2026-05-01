package com.finsense.data.preferences

import java.text.NumberFormat
import java.util.Locale

enum class AppCurrency(
    val displayName: String,
    val symbol: String,
    val amountPatterns: List<Regex>
) {
    EGP(
        displayName = "Egyptian Pound (EGP)",
        symbol = "EGP",
        amountPatterns = listOf(
            Regex("""(?:egp\.?|EGP)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:egp\.?|EGP)""", RegexOption.IGNORE_CASE)
        )
    ),
    INR(
        displayName = "Indian Rupee (INR)",
        symbol = "₹",
        amountPatterns = listOf(
            Regex("""(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:Rs\.?|INR|₹)""", RegexOption.IGNORE_CASE)
        )
    );

    private val numberFmt: NumberFormat
        get() = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

    fun formatAmount(amount: Double): String = "$symbol ${numberFmt.format(amount)}"

    fun formatCompact(amount: Double): String {
        val abs = kotlin.math.abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            abs >= 1_000_000.0 -> {
                val v = abs / 1_000_000.0
                val str = if (v % 1.0 == 0.0) "%.0f".format(v) else "%.1f".format(v)
                "$symbol $sign${str}M"
            }
            abs >= 10_000.0 -> {
                val v = abs / 1_000.0
                val str = if (v % 1.0 == 0.0) "%.0f".format(v) else "%.1f".format(v)
                "$symbol $sign${str}K"
            }
            else -> formatAmount(amount)
        }
    }
}
