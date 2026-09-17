package org.notifledger.app.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.notifledger.app.model.TransactionFilter
import java.io.File

class FilterIOTest {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "notifledger-filter-test-${System.currentTimeMillis()}")

    @Test
    fun `save and load filters round-trips order label account and active flag`() {
        val dir = File(tempDir, "roundtrip")
        dir.mkdirs()
        val filters = listOf(
            TransactionFilter(label = "Groceries", account = "expenses:groceries", isActive = true),
            TransactionFilter(label = "Travel", account = "expenses:travel", isActive = false),
            TransactionFilter(label = "Subscriptions", account = "expenses:subscriptions", isActive = true),
        )

        FilterIO.saveFilters(dir, filters)
        val loaded = FilterIO.loadFilters(dir)

        assertEquals(filters, loaded)
    }

    @Test
    fun `missing file returns empty list`() {
        val dir = File(tempDir, "missing")
        assertEquals(emptyList<TransactionFilter>(), FilterIO.loadFilters(dir))
    }

    @Test
    fun `malformed yaml returns empty list without throwing`() {
        val dir = File(tempDir, "malformed")
        dir.mkdirs()
        File(dir, "filters.yaml").writeText("label: [unclosed")

        assertEquals(emptyList<TransactionFilter>(), FilterIO.loadFilters(dir))
    }

    @Test
    fun `entries missing label or account are skipped`() {
        val dir = File(tempDir, "incomplete")
        dir.mkdirs()
        File(dir, "filters.yaml").writeText(
            """
            - label: No Account
            - account: expenses:no-label
            - label: Valid
              account: expenses:valid
            """.trimIndent()
        )

        val loaded = FilterIO.loadFilters(dir)

        assertEquals(listOf(TransactionFilter(label = "Valid", account = "expenses:valid")), loaded)
    }

    @Test
    fun `entry without active key defaults to active`() {
        val dir = File(tempDir, "default-active")
        dir.mkdirs()
        File(dir, "filters.yaml").writeText(
            """
            - label: Default Active
              account: expenses:default
            """.trimIndent()
        )

        val loaded = FilterIO.loadFilters(dir)

        assertEquals(1, loaded.size)
        assertTrue(loaded[0].isActive)
    }
}
