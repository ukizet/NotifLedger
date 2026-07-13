package org.notifledger.app.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import org.notifledger.app.model.CategorizationRule
import java.io.File

class RuleIOTest {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "notifledger-test-${System.currentTimeMillis()}")

    @Test
    fun `save and load categorization rules`() {
        val rules = listOf(
            CategorizationRule(match = "REMA|KIWI", account = "expenses:groceries"),
            CategorizationRule(match = "Spotify", account = "expenses:subscriptions"),
        )
        RuleIO.saveCategorizationRules(tempDir, rules)
        val loaded = RuleIO.loadCategorizationRules(tempDir)
        assertEquals(2, loaded.size)
        assertEquals("REMA|KIWI", loaded[0].match)
        assertEquals("expenses:groceries", loaded[0].account)
        assertEquals("Spotify", loaded[1].match)
        assertEquals("expenses:subscriptions", loaded[1].account)
    }

    @Test
    fun `load empty directory returns empty list`() {
        val dir = File(tempDir, "empty")
        dir.mkdirs()
        assertEquals(emptyList<CategorizationRule>(), RuleIO.loadCategorizationRules(dir))
    }

    @Test
    fun `save and load default rule with empty match`() {
        val rules = listOf(
            CategorizationRule(match = "", account = "expenses:uncategorized"),
        )
        RuleIO.saveCategorizationRules(tempDir, rules)
        val loaded = RuleIO.loadCategorizationRules(tempDir)
        assertEquals(1, loaded.size)
        assertEquals("", loaded[0].match)
        assertEquals("expenses:uncategorized", loaded[0].account)
    }
}
