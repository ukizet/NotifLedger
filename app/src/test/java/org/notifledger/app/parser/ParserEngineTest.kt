package org.notifledger.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.notifledger.app.model.Posting
import org.notifledger.app.model.Source

class ParserEngineTest {

    @Test
    fun `parse extracts first number as amount and title as payee`() {
        val result = ParserEngine.parse(
            title = "Betalt",
            text = "Betalt 184.50 kr hos Rema 1000",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("Betalt", result!!.payee)
        assertEquals("184.50", result.postings[0].amount)
        assertEquals("expenses:unknown", result.postings[0].account)
        assertEquals("assets:bank:checking", result.postings[1].account)
    }

    @Test
    fun `parse returns null when no number found`() {
        val result = ParserEngine.parse(
            title = "Hei",
            text = "Ingen tall her",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNull(result)
    }

    @Test
    fun `parse handles norwegian comma in amount`() {
        val result = ParserEngine.parse(
            title = "Betalt",
            text = "Betalt 184,50 kr",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("184.50", result!!.postings[0].amount)
    }

    @Test
    fun `parse uses title as payee regardless of text`() {
        val result = ParserEngine.parse(
            title = "Payment received",
            text = "Du har mottatt 200 kr fra Ola Nordmann",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("Payment received", result!!.payee)
        assertEquals("200", result.postings[0].amount)
    }

    @Test
    fun `parse returns transaction with Notification source`() {
        val result = ParserEngine.parse(
            title = "Betalt",
            text = "Betalt 99 kr",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals(Source.Notification, result!!.source)
    }

    @Test
    fun `parse creates two postings with auto-balance`() {
        val result = ParserEngine.parse(
            title = "Kjøp",
            text = "Kjøpt 349 kr",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals(2, result!!.postings.size)
        assertEquals("expenses:unknown", result.postings[0].account)
        assertEquals("assets:bank:checking", result.postings[1].account)
        // Second posting has empty amount for auto-balance
        assertEquals("", result.postings[1].amount)
    }
}
