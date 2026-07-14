package org.notifledger.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.notifledger.app.model.Source

class ParserEngineTest {

    // ── Existing tests (preserved) ──────────────────────────────────────────

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
        assertEquals("", result.postings[1].amount)
    }

    // ── Real-world notification formats ─────────────────────────────────────

    @Test
    fun `vipps notification with amount and recipient`() {
        val result = ParserEngine.parse(
            title = "Vipps",
            text = "150 kr betalt til Ola Nordmann. Kvittering: 3AB2C",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("Vipps", result!!.payee)
        assertEquals("150", result.postings[0].amount)
    }

    @Test
    fun `dnb varekjøp notification`() {
        val result = ParserEngine.parse(
            title = "Varekjøp",
            text = "Varekjøp 549,00 kr hos MENY Løren",
            expenseAccount = "expenses:groceries",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("Varekjøp", result!!.payee)
        assertEquals("549.00", result.postings[0].amount)
    }

    @Test
    fun `paypal payment sent notification`() {
        val result = ParserEngine.parse(
            title = "You sent a payment",
            text = "You sent $25.00 USD to jane@example.com",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:paypal",
        )
        assertNotNull(result)
        assertEquals("You sent a payment", result!!.payee)
        assertEquals("25.00", result.postings[0].amount)
    }

    @Test
    fun `revolut spent notification with euro symbol`() {
        val result = ParserEngine.parse(
            title = "Card payment",
            text = "Spent €12,99 at Starbucks. Available: €184.50",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:revolut",
        )
        assertNotNull(result)
        assertEquals("Card payment", result!!.payee)
        assertEquals("12.99", result.postings[0].amount)
    }

    @Test
    fun `apple pay notification`() {
        val result = ParserEngine.parse(
            title = "Apple Pay",
            text = "Paid $5.99 at McDonald's with Apple Pay",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("Apple Pay", result!!.payee)
        assertEquals("5.99", result.postings[0].amount)
    }

    @Test
    fun `amount with norwegian thousand separator space`() {
        val result = ParserEngine.parse(
            title = "Lønn",
            text = "Innbetalt 12 500,00 kr fra Arbeidsgiver AS",
            expenseAccount = "income:salary",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("Lønn", result!!.payee)
        assertEquals("12500.00", result.postings[0].amount)
    }

    @Test
    fun `amount with currency code after number`() {
        val result = ParserEngine.parse(
            title = "Vipps",
            text = "499.00 NOK betalt til Elkjøp",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("499.00", result!!.postings[0].amount)
    }

    @Test
    fun `amount without any currency indicator`() {
        val result = ParserEngine.parse(
            title = "Test",
            text = "Beløp: 750",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("750", result!!.postings[0].amount)
    }

    @Test
    fun `multiple numbers in text picks first one`() {
        val result = ParserEngine.parse(
            title = "Bestilling",
            text = "Pris 299 kr + frakt 49 kr = 348 kr",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("299", result!!.postings[0].amount)
    }

    @Test
    fun `small amount less than one`() {
        val result = ParserEngine.parse(
            title = "Pant",
            text = "Pant returnert 0.50 kr",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("0.50", result!!.postings[0].amount)
    }

    @Test
    fun `large amount with spaces`() {
        val result = ParserEngine.parse(
            title = "Overføring",
            text = "Overført 1 234 567,89 kr",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("1234567.89", result!!.postings[0].amount)
    }

    @Test
    fun `negative amount in text extracts positive number`() {
        val result = ParserEngine.parse(
            title = "Tilbakebetaling",
            text = "-250 kr refundert til konto",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        // The regex finds "250" since it starts with \d, not "-"
        assertNotNull(result)
        assertEquals("250", result!!.postings[0].amount)
    }

    @Test
    fun `title contains numbers but text has amount`() {
        val result = ParserEngine.parse(
            title = "Kjøp 123",
            text = "Betalt 99 kr",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        // "Kjøp 123" is the payee (title), but combined = "Kjøp 123 Betalt 99 kr"
        // First number found is "123" from the title
        assertNotNull(result)
        assertEquals("Kjøp 123", result!!.payee)
        assertEquals("123", result.postings[0].amount)
    }

    @Test
    fun `empty text with number only in title`() {
        val result = ParserEngine.parse(
            title = "Kjøp 249",
            text = "",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("Kjøp 249", result!!.payee)
        assertEquals("249", result.postings[0].amount)
    }

    @Test
    fun `short single-character title`() {
        val result = ParserEngine.parse(
            title = "X",
            text = "Betalte 89 kr",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("X", result!!.payee)
        assertEquals("89", result.postings[0].amount)
    }

    @Test
    fun `amount with dot as thousand separator is not parsable`() {
        val result = ParserEngine.parse(
            title = "Overføring",
            text = "Overført 1.234,56 kr",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        // Regex finds "1.234,56" -> replace comma -> "1.234.56"
        // toDoubleOrNull returns null for multiple dots
        assertNull(result)
    }

    @Test
    fun `amount at very start of text`() {
        val result = ParserEngine.parse(
            title = "Kjøp",
            text = "99 kr ble trukket fra konto",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("99", result!!.postings[0].amount)
    }

    @Test
    fun `single digit amount`() {
        val result = ParserEngine.parse(
            title = "avis",
            text = "5 kr for dagens avis",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("5", result!!.postings[0].amount)
    }

    @Test
    fun `amount with trailing text after number`() {
        val result = ParserEngine.parse(
            title = "Coinbase",
            text = "You bought 50.00 USDC with $50.00 USD",
            expenseAccount = "expenses:crypto",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("Coinbase", result!!.payee)
        // First number found is "50.00" (USDC amount)
        assertEquals("50.00", result.postings[0].amount)
    }

    @Test
    fun `text with only whitespace and no title`() {
        val result = ParserEngine.parse(
            title = "",
            text = " ",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNull(result)
    }

    @Test
    fun `amount with currency symbol prefix`() {
        val result = ParserEngine.parse(
            title = "Amazon",
            text = "€19.99 purchase at Amazon.de",
            expenseAccount = "expenses:shopping",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("19.99", result!!.postings[0].amount)
    }

    @Test
    fun `amount with decimal and leading zero`() {
        val result = ParserEngine.parse(
            title = "Straffegebyr",
            text = "Gebyret er på 0.75 kr",
            expenseAccount = "expenses:fees",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("0.75", result!!.postings[0].amount)
    }

    @Test
    fun `amount with GBP symbol`() {
        val result = ParserEngine.parse(
            title = "Payment",
            text = "Charged £8.50 for Netflix",
            expenseAccount = "expenses:subscription",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("8.50", result!!.postings[0].amount)
    }

    @Test
    fun `amount at end of text with no trailing text`() {
        val result = ParserEngine.parse(
            title = "Strømregning",
            text = "Trekker 1 245",
            expenseAccount = "expenses:utilities",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("1245", result!!.postings[0].amount)
    }

    @Test
    fun `amount with multiple consecutive spaces`() {
        val result = ParserEngine.parse(
            title = "Husleie",
            text = "Betalt  9 500  kr  for  leie",
            expenseAccount = "expenses:housing",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("9500", result!!.postings[0].amount)
    }

    @Test
    fun `swish notification swedish format`() {
        val result = ParserEngine.parse(
            title = "Swish",
            text = "Swish 45 kr från Anna Andersson",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:swish",
        )
        assertNotNull(result)
        assertEquals("Swish", result!!.payee)
        assertEquals("45", result.postings[0].amount)
    }

    @Test
    fun `klarna payment reminder`() {
        val result = ParserEngine.parse(
            title = "Klarna",
            text = "Faktura på 299,00 kr forfaller 15. mars",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("Klarna", result!!.payee)
        assertEquals("299.00", result.postings[0].amount)
    }

    @Test
    fun `number inside parentheses is still found`() {
        val result = ParserEngine.parse(
            title = "Billett",
            text = "Betalte (450 kr) for konsert",
            expenseAccount = "expenses:entertainment",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("450", result!!.postings[0].amount)
    }

    @Test
    fun `notification text contains only number without any letters`() {
        val result = ParserEngine.parse(
            title = "SMS",
            text = "250",
            expenseAccount = "expenses:unknown",
            moneyAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("250", result!!.postings[0].amount)
    }
}
