package org.notifledger.app.model

/**
 * A parser rule for a single notification source app.
 *
 * Designed to be obvious: you tell the app what text identifies a transaction
 * notification, where the amount is, and where the merchant/payee is.
 *
 * The YAML looks like:
 *   app: DNB
 *   contains: "Betalt"
 *   amount: first_number
 *   payee: before_amount
 *   currency: NOK
 */
data class ParserRule(
    val appName: String,               // human-readable, e.g. "DNB", "Vipps"
    val containsText: String,          // text that identifies this as a transaction notification
    val amountExtractor: AmountExtractor,  // how to find the amount
    val payeeExtractor: PayeeExtractor,    // how to find the payee/merchant
    val amountPrefix: String = "",      // used with AfterText extractors (e.g. "kr", "$")
    val currency: String,
)

enum class AmountExtractor {
    FirstNumber,
    LastNumber,
    AfterText;

    companion object {
        fun fromString(s: String): AmountExtractor = when (s) {
            "first_number" -> FirstNumber
            "last_number" -> LastNumber
            "after_text" -> AfterText
            else -> FirstNumber
        }

        fun toRuleString(e: AmountExtractor): String = when (e) {
            FirstNumber -> "first_number"
            LastNumber -> "last_number"
            AfterText -> "after_text"
        }
    }
}

enum class PayeeExtractor {
    BeforeAmount,
    WholeText,
    AfterText;

    companion object {
        fun fromString(s: String): PayeeExtractor = when (s) {
            "before_amount" -> BeforeAmount
            "whole_text" -> WholeText
            "after_text" -> AfterText
            else -> BeforeAmount
        }

        fun toRuleString(e: PayeeExtractor): String = when (e) {
            BeforeAmount -> "before_amount"
            WholeText -> "whole_text"
            AfterText -> "after_text"
        }
    }
}
