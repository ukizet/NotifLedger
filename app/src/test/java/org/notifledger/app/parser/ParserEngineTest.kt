package org.notifledger.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.notifledger.app.model.AmountExtractor
import org.notifledger.app.model.ParserRule
import org.notifledger.app.model.PayeeExtractor

class ParserEngineTest {

    private val dnbRule = ParserRule(
        appName = "DNB",
        containsText = "Betalt",
        amountExtractor = AmountExtractor.FirstNumber,
        payeeExtractor = PayeeExtractor.AfterText,
        amountPrefix = "hos",
        currency = "NOK",
    )

    private val vippsRule = ParserRule(
        appName = "Vipps",
        containsText = "Vipps",
        amountExtractor = AmountExtractor.LastNumber,
        payeeExtractor = PayeeExtractor.BeforeAmount,
        currency = "NOK",
    )

    @Test
    fun `parse DNB notification`() {
        val result = ParserEngine.parse(
            title = "Betalt",
            text = "Betalt 184.50 kr hos Rema 1000",
            rule = dnbRule,
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("Rema 1000", result!!.payee)
        assertEquals("184.50", result.postings[0].amount)
        assertEquals("expenses:unknown", result.postings[0].account)
        assertEquals("assets:bank:checking", result.postings[1].account)
    }

    @Test
    fun `parse DNB notification ignores case`() {
        val result = ParserEngine.parse(
            title = "BETALT",
            text = "BETALT 99 KR HOS KIWI",
            rule = dnbRule,
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("99", result!!.postings[0].amount)
        assertEquals("KIWI", result.payee)
    }

    @Test
    fun `parse Vipps notification`() {
        val result = ParserEngine.parse(
            title = "Payment received",
            text = "Vipps fra Ola Nordmann på 200 kr",
            rule = vippsRule,
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("Payment received Vipps fra Ola Nordmann på", result!!.payee)
        assertEquals("200", result.postings[0].amount)
    }

    @Test
    fun `parse returns null when text doesnt match`() {
        val result = ParserEngine.parse(
            title = "Something else",
            text = "No relevant notification",
            rule = dnbRule,
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNull(result)
    }

    @Test
    fun `parse returns null when no amount found`() {
        val result = ParserEngine.parse(
            title = "Betalt",
            text = "Betalt kr",
            rule = dnbRule,
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNull(result)
    }

    @Test
    fun `parse handles norwegian comma in amount`() {
        val result = ParserEngine.parse(
            title = "Betalt",
            text = "Betalt 184,50 kr hos Rema",
            rule = dnbRule,
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("184.50", result!!.postings[0].amount)
    }
}
