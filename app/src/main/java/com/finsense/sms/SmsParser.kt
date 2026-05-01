package com.finsense.sms

import com.finsense.data.entity.TransactionType
import com.finsense.data.preferences.UserPreferences
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedTransaction(
    val amount: Double,
    val type: TransactionType,
    val vendor: String,
    val description: String,
    val date: Long? = null,
    val currency: String
)

@Singleton
class SmsParser @Inject constructor(private val userPreferences: UserPreferences) {

    // Sender IDs that look like bank/fintech alphanumeric codes (not regular phone numbers)
    private val financialSenderPattern = Regex("""^[A-Z]{2}-[A-Z0-9]{3,8}$|^[A-Z]{3,8}$""")

    private val debitKeywords = listOf(
        "debited", "debit", "spent", "paid", "payment made", "purchase",
        "withdrawn", "withdrawal", "charged", "deducted", "sent", "transferred","سحب"
    )
    private val creditKeywords = listOf(
        "credited", "credit", "received", "refund", "deposited",
        "added", "reversed", "cashback", "reward","رد"
    )

    // Vendor extraction — ordered from most specific to least
    private val vendorPatterns = listOf(
        Regex("""at\s+([0-9][A-Za-z0-9 &.\-'_]{1,49}?)\s+on\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:at|At)\s+([A-Z][A-Za-z0-9 &.\-'_]{2,40}?)(?:\s+on\b|\s+via\b|\s+\(|\.|;|\|)"""),
        Regex("""(?:paid to|Paid to|to UPI|to)\s+([A-Za-z0-9 &.\-'_@]{3,40}?)(?:\s+(?:via|on|for)|\s*[.;|]|$)""", RegexOption.IGNORE_CASE),
        Regex("""Info:\s*([A-Za-z0-9 &.\-'_]{2,40}?)(?:\s*[;|]|$)""", RegexOption.IGNORE_CASE),
        Regex("""merchant[:\s]+([A-Za-z0-9 &.\-'_]{2,40}?)(?:[.;,]|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:from|From)\s+([A-Z][A-Za-z0-9 &.\-'_]{2,40}?)(?:\s+on\b|\s*[.;|]|$)""")
    )

    // Generic fallback: catches "USD 50.00", "EUR 1,200.00", "GBP 45" etc.
    // Tried only after preferred-currency patterns fail.
    private val intlCurrencyBeforeAmount = Regex("""\b([A-Z]{3})\s+((\.)*[0-9,]+(?:\.[0-9]{1,2})?)""")
    private val intlAmountBeforeCurrency = Regex("""((\.)*[0-9,]+(?:\.[0-9]{1,2})?)\s*\b([A-Z]{3})\b""")

    // Last-resort: amount without any currency marker — used with preferred currency as default.
    private val amountOnlyPatterns = listOf(
        Regex("""(?:debited?|credited?|spent|paid|payment|withdrawn|charged|amount)\s+(?:of\s+)?([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([0-9,]+(?:\.[0-9]{1,2})?)\s+(?:has been|was)\s+(?:debited|credited|charged)""", RegexOption.IGNORE_CASE)
    )

    // Matches "DD/MM/YY at hh:mm", e.g. "14/04/26 at 16:08"
    private val datePatterns = listOf(
        Regex("""(\d{2}/\d{2}/\d{2})\s+at\s+(\d{2}:\d{2})""", RegexOption.IGNORE_CASE)
    )

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yy 'at' HH:mm")

    fun isFinancialSms(sender: String, body: String): Boolean {
        val looksLikeBank = financialSenderPattern.matches(sender.trim())
        val hasFinancialContent = Regex(
            """debited|credited|spent|payment|withdrawn|refund|EGP|egp\.|INR|balance|transaction|USD|EUR|GBP""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(body)
        return looksLikeBank && hasFinancialContent
    }

    fun parse(body: String, sender: String): ParsedTransaction? {
        val (amount, currency) = extractAmountAndCurrency(body) ?: return null
        val type = extractType(body) ?: return null
        val vendor = extractVendor(body) ?: cleanSender(sender)
        return ParsedTransaction(
            amount = amount,
            type = type,
            vendor = vendor,
            description = body.take(160),
            date = extractDate(body),
            currency = currency
        )
    }

    private fun extractAmountAndCurrency(body: String): Pair<Double, String>? {
        val preferred = userPreferences.currency
        // 1. ISO code before amount, e.g. "EGP 1,500.00" or "USD 50.00"
        intlCurrencyBeforeAmount.find(body)?.let { m ->
            val amount = m.groupValues[2].replace(",", "").toDoubleOrNull()
            if (amount != null) return amount to m.groupValues[1]
        }
        // 2. Amount before ISO code, e.g. "50.00 USD"
        intlAmountBeforeCurrency.find(body)?.let { m ->
            val amount = m.groupValues[1].replace(",", "").toDoubleOrNull()
            if (amount != null) return amount to m.groupValues[2]
        }
        // 3. Preferred-currency symbol patterns, e.g. "₹500", "Rs. 500"
        for (pattern in preferred.amountPatterns) {
            val match = pattern.find(body) ?: continue
            val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            return amount to preferred.name
        }
        // 4. Last resort: no currency marker — extract bare amount, use preferred currency
        for (pattern in amountOnlyPatterns) {
            val match = pattern.find(body) ?: continue
            val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            return amount to preferred.name
        }
        return null
    }

    private fun extractType(body: String): TransactionType? {
        val lower = body.lowercase()
        val hasDebit = debitKeywords.any { lower.contains(it) }
        val hasCredit = creditKeywords.any { lower.contains(it) }
        return when {
            hasDebit && !hasCredit -> TransactionType.DEBIT
            hasCredit && !hasDebit -> TransactionType.CREDIT
            hasDebit -> TransactionType.DEBIT  // debit takes priority if both found
            else -> null
        }
    }

    private fun extractVendor(body: String): String? {
        for (pattern in vendorPatterns) {
            val match = pattern.find(body) ?: continue
            val candidate = match.groupValues[1].trim()
            if (candidate.length >= 2 && !candidate.all { it.isDigit() || it == ' ' }) {
                return candidate
            }
        }
        return null
    }

    private fun extractDate(body: String): Long? {
        for (pattern in datePatterns) {
            val match = pattern.find(body) ?: continue
            val dateStr = "${match.groupValues[1]} at ${match.groupValues[2]}"
            return try {
                LocalDateTime.parse(dateStr, dateFormatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun cleanSender(sender: String): String =
        sender.replace(Regex("""^[A-Z]{2}-"""), "").trim()
}
