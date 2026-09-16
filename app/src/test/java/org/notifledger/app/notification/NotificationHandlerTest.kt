package org.notifledger.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.notifledger.app.model.CategorizationRule
import org.notifledger.app.model.Source

class NotificationHandlerTest {

    // ── shouldProcess ────────────────────────────────────────────────────────

    @Test
    fun `empty allowed set returns false`() {
        assertFalse(NotificationHandler.shouldProcess("com.example.bank", emptySet()))
    }

    @Test
    fun `package in allowed set returns true`() {
        assertTrue(NotificationHandler.shouldProcess("com.example.bank", setOf("com.example.bank")))
    }

    @Test
    fun `package not in allowed set returns false`() {
        assertFalse(NotificationHandler.shouldProcess("com.example.bank", setOf("com.other.app")))
    }

    @Test
    fun `multiple allowed packages matches correctly`() {
        val allowed = setOf("com.a", "com.example.bank", "com.b")
        assertTrue(NotificationHandler.shouldProcess("com.example.bank", allowed))
        assertFalse(NotificationHandler.shouldProcess("com.not.allowed", allowed))
    }

    // ── parseTransaction ─────────────────────────────────────────────────────

    @Test
    fun `parseTransaction returns transaction for valid notification`() {
        val result = NotificationHandler.parseTransaction(
            title = "Betalt",
            text = "Betalt 184.50 kr hos Rema 1000",
            defaultAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("Betalt", result!!.payee)
        assertEquals("184.50", result.postings[0].amount)
        assertEquals("expenses:unknown", result.postings[0].account)
        assertEquals("assets:bank:checking", result.postings[1].account)
    }

    @Test
    fun `parseTransaction returns null for notification without number`() {
        val result = NotificationHandler.parseTransaction(
            title = "Hei",
            text = "Ingen tall her",
            defaultAccount = "assets:bank:checking",
        )
        assertNull(result)
    }

    @Test
    fun `parseTransaction handles norwegian comma`() {
        val result = NotificationHandler.parseTransaction(
            title = "Betalt",
            text = "Betalt 184,50 kr",
            defaultAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("184.50", result!!.postings[0].amount)
    }

    // ── createBestEffortTransaction ──────────────────────────────────────────

    @Test
    fun `best-effort transaction has correct structure`() {
        val tx = NotificationHandler.createBestEffortTransaction(
            title = "Unknown payment",
            defaultAccount = "assets:bank:checking",
            date = "2026-07-20",
        )
        assertEquals("2026-07-20", tx.date)
        assertEquals("Unknown payment", tx.payee)
        assertEquals(2, tx.postings.size)
        assertEquals("expenses:unknown", tx.postings[0].account)
        assertEquals("", tx.postings[0].amount)
        assertEquals("", tx.postings[0].currency)
        assertEquals("assets:bank:checking", tx.postings[1].account)
        assertEquals("", tx.postings[1].amount)
        assertEquals("", tx.postings[1].currency)
        assertEquals(Source.Notification, tx.source)
    }

    @Test
    fun `best-effort transaction uses different default account`() {
        val tx = NotificationHandler.createBestEffortTransaction(
            title = "Test",
            defaultAccount = "assets:paypal",
            date = "2026-07-20",
        )
        assertEquals("assets:paypal", tx.postings[1].account)
    }

    @Test
    fun `best-effort transaction uses today date by default`() {
        val today = java.time.LocalDate.now().toString()
        val tx = NotificationHandler.createBestEffortTransaction(
            title = "Test",
            defaultAccount = "assets:bank:checking",
        )
        assertEquals(today, tx.date)
    }

    // ── processNotification (integration) ────────────────────────────────────

    @Test
    fun `processNotification returns null when package not allowed`() {
        val result = NotificationHandler.processNotification(
            packageName = "com.malicious.app",
            title = "Betalt",
            text = "Betalt 100 kr",
            allowedPackages = setOf("com.actual.bank"),
            defaultAccount = "assets:bank:checking",
        )
        assertNull(result)
    }

    @Test
    fun `processNotification returns parsed transaction when number found`() {
        val result = NotificationHandler.processNotification(
            packageName = "com.example.bank",
            title = "Varekjøp",
            text = "Varekjøp 549,00 kr hos MENY",
            allowedPackages = setOf("com.example.bank"),
            defaultAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("Varekjøp", result!!.payee)
        assertEquals("549.00", result.postings[0].amount)
        assertEquals(Source.Notification, result.source)
    }

    @Test
    fun `processNotification returns best-effort when no number found`() {
        val result = NotificationHandler.processNotification(
            packageName = "com.example.bank",
            title = "Hei",
            text = "Ingen pengeinformasjon",
            allowedPackages = setOf("com.example.bank"),
            defaultAccount = "assets:bank:checking",
        )
        assertNotNull(result)
        assertEquals("Hei", result!!.payee)
        assertEquals("expenses:unknown", result.postings[0].account)
        assertEquals("", result.postings[0].amount)
        assertEquals("assets:bank:checking", result.postings[1].account)
        assertEquals(Source.Notification, result.source)
    }

    @Test
    fun `processNotification best-effort entry is written for empty text`() {
        val result = NotificationHandler.processNotification(
            packageName = "com.example.bank",
            title = "SMS",
            text = "",
            allowedPackages = setOf("com.example.bank"),
            defaultAccount = "assets:bank:checking",
        )
        // No number in empty text + title "SMS" = no number found → best-effort
        assertNotNull(result)
        assertEquals("SMS", result!!.payee)
        assertEquals("", result.postings[0].amount)
        assertEquals(Source.Notification, result.source)
    }

    // ── needsListenerAttention ───────────────────────────────────────────────

    @Test
    fun `no attention needed when permission granted and listener never connected`() {
        assertFalse(NotificationHandler.needsListenerAttention(
            isPermissionGranted = true,
            lastConnectedAt = null,
            now = 1_000_000_000_000L,
        ))
    }

    @Test
    fun `no attention needed when permission granted with recent timestamp`() {
        val now = 1_000_000_000_000L
        assertFalse(NotificationHandler.needsListenerAttention(
            isPermissionGranted = true,
            lastConnectedAt = now - 1000L, // 1 second ago
            now = now,
        ))
    }

    @Test
    fun `no attention needed when permission false even with null timestamp`() {
        assertFalse(NotificationHandler.needsListenerAttention(
            isPermissionGranted = false,
            lastConnectedAt = null,
            now = 1_000_000_000_000L,
        ))
    }

    @Test
    fun `no attention needed when permission false with old timestamp`() {
        assertFalse(NotificationHandler.needsListenerAttention(
            isPermissionGranted = false,
            lastConnectedAt = 1_000_000L,
            now = 1_000_000_000_000L,
        ))
    }

    @Test
    fun `needs attention when timestamp is older than threshold`() {
        val now = 1_000_000_000_000L
        val threshold = 24L * 60 * 60 * 1000
        assertTrue(NotificationHandler.needsListenerAttention(
            isPermissionGranted = true,
            lastConnectedAt = now - threshold - 1, // just past the threshold
            now = now,
        ))
    }

    @Test
    fun `no attention needed when timestamp is exactly at threshold`() {
        val now = 1_000_000_000_000L
        val threshold = 24L * 60 * 60 * 1000
        assertFalse(NotificationHandler.needsListenerAttention(
            isPermissionGranted = true,
            lastConnectedAt = now - threshold,
            now = now,
        ))
    }

    // ── matchCategory ────────────────────────────────────────────────────────

    @Test
    fun `matchCategory returns default when no rules`() {
        val account = NotificationHandler.matchCategory("Extra Porsgrunn", emptyList())
        assertEquals("expenses:unknown", account)
    }

    @Test
    fun `matchCategory returns default when no rule matches`() {
        val rules = listOf(CategorizationRule(match = "REMA", account = "expenses:groceries"))
        val account = NotificationHandler.matchCategory("Extra Porsgrunn", rules)
        assertEquals("expenses:unknown", account)
    }

    @Test
    fun `matchCategory returns matching rule account`() {
        val rules = listOf(CategorizationRule(match = "Extra", account = "expenses:groceries"))
        val account = NotificationHandler.matchCategory("Extra Porsgrunn", rules)
        assertEquals("expenses:groceries", account)
    }

    @Test
    fun `matchCategory is case-insensitive`() {
        val rules = listOf(CategorizationRule(match = "coop", account = "expenses:groceries"))
        val account = NotificationHandler.matchCategory("COOP Mega", rules)
        assertEquals("expenses:groceries", account)
    }

    @Test
    fun `matchCategory first matching rule wins`() {
        val rules = listOf(
            CategorizationRule(match = "REMA", account = "expenses:rema"),
            CategorizationRule(match = "Extra", account = "expenses:extra"),
            CategorizationRule(match = "Extra", account = "expenses:extra2"),
        )
        val account = NotificationHandler.matchCategory("Extra Porsgrunn", rules)
        assertEquals("expenses:extra", account)
    }

    @Test
    fun `matchCategory skips empty match pattern`() {
        val rules = listOf(
            CategorizationRule(match = "", account = "expenses:fallback"),
            CategorizationRule(match = "Extra", account = "expenses:groceries"),
        )
        val account = NotificationHandler.matchCategory("Extra", rules)
        assertEquals("expenses:groceries", account)
    }

    @Test
    fun `matchCategory skips malformed regex`() {
        val rules = listOf(
            CategorizationRule(match = "[invalid", account = "expenses:broken"),
            CategorizationRule(match = "Extra", account = "expenses:groceries"),
        )
        val account = NotificationHandler.matchCategory("Extra Porsgrunn", rules)
        assertEquals("expenses:groceries", account)
    }

    // ── parseTransaction with rules ──────────────────────────────────────────

    @Test
    fun `parseTransaction uses rule-matched account`() {
        val rules = listOf(CategorizationRule(match = "Rema", account = "expenses:rema"))
        val result = NotificationHandler.parseTransaction(
            title = "Rema 1000",
            text = "Betalt 184.50 kr",
            defaultAccount = "assets:bank:checking",
            rules = rules,
        )
        assertNotNull(result)
        assertEquals("expenses:rema", result!!.postings[0].account)
    }

    @Test
    fun `parseTransaction uses default account when no rule matches`() {
        val rules = listOf(CategorizationRule(match = "Coop", account = "expenses:coop"))
        val result = NotificationHandler.parseTransaction(
            title = "Extra",
            text = "Betalt 99 kr",
            defaultAccount = "assets:bank:checking",
            rules = rules,
        )
        assertNotNull(result)
        assertEquals("expenses:unknown", result!!.postings[0].account)
    }

    // ── createBestEffortTransaction with rules ───────────────────────────────

    @Test
    fun `best-effort uses rule-matched account`() {
        val rules = listOf(CategorizationRule(match = "Kiwi", account = "expenses:kiwi"))
        val tx = NotificationHandler.createBestEffortTransaction(
            title = "Kiwi",
            defaultAccount = "assets:bank:checking",
            rules = rules,
            date = "2026-07-20",
        )
        assertEquals("expenses:kiwi", tx.postings[0].account)
    }

    // ── processNotification with rules ───────────────────────────────────────

    @Test
    fun `processNotification uses rules for parsed transaction`() {
        val rules = listOf(CategorizationRule(match = "Meny", account = "expenses:meny"))
        val result = NotificationHandler.processNotification(
            packageName = "com.example.bank",
            title = "Meny Løren",
            text = "Betalt 549,00 kr",
            allowedPackages = setOf("com.example.bank"),
            defaultAccount = "assets:bank:checking",
            rules = rules,
        )
        assertNotNull(result)
        assertEquals("expenses:meny", result!!.postings[0].account)
        assertEquals("549.00", result.postings[0].amount)
    }

    @Test
    fun `processNotification uses rules for best-effort transaction`() {
        val rules = listOf(CategorizationRule(match = "Hei", account = "expenses:greeting"))
        val result = NotificationHandler.processNotification(
            packageName = "com.example.bank",
            title = "Hei",
            text = "Ingen tall",
            allowedPackages = setOf("com.example.bank"),
            defaultAccount = "assets:bank:checking",
            rules = rules,
        )
        assertNotNull(result)
        assertEquals("expenses:greeting", result!!.postings[0].account)
        assertEquals("", result.postings[0].amount)
    }

    private fun assertFalse(actual: Boolean) {
        org.junit.Assert.assertFalse(actual)
    }

    private fun assertTrue(actual: Boolean) {
        org.junit.Assert.assertTrue(actual)
    }
}
