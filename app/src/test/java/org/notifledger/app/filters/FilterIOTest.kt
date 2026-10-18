package org.notifledger.app.filters

import org.junit.Assert.assertEquals
import org.junit.Test
import org.notifledger.app.model.FilterMode
import org.notifledger.app.model.TransactionFilter
import java.io.File

class FilterIOTest {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "notifledger-filter-test-${System.currentTimeMillis()}")

    @Test
    fun `save and load filters round-trips order label account and mode`() {
        val dir = File(tempDir, "roundtrip")
        dir.mkdirs()
        val filters = listOf(
            TransactionFilter(label = "Groceries", account = "expenses:groceries", mode = FilterMode.INCLUDE),
            TransactionFilter(label = "Travel", account = "expenses:travel", mode = FilterMode.EXCLUDE),
            TransactionFilter(label = "Off", account = "expenses:off", mode = FilterMode.OFF),
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
    fun `legacy active true loads as include`() {
        val dir = File(tempDir, "legacy-true")
        dir.mkdirs()
        File(dir, "filters.yaml").writeText(
            """
            - label: Legacy
              account: expenses:legacy
              active: true
            """.trimIndent()
        )

        val loaded = FilterIO.loadFilters(dir)

        assertEquals(
            listOf(TransactionFilter(label = "Legacy", account = "expenses:legacy", mode = FilterMode.INCLUDE)),
            loaded,
        )
    }

    @Test
    fun `legacy active false loads as off`() {
        val dir = File(tempDir, "legacy-false")
        dir.mkdirs()
        File(dir, "filters.yaml").writeText(
            """
            - label: Legacy
              account: expenses:legacy
              active: false
            """.trimIndent()
        )

        val loaded = FilterIO.loadFilters(dir)

        assertEquals(
            listOf(TransactionFilter(label = "Legacy", account = "expenses:legacy", mode = FilterMode.OFF)),
            loaded,
        )
    }

    @Test
    fun `entry with neither mode nor active defaults to include`() {
        val dir = File(tempDir, "default-include")
        dir.mkdirs()
        File(dir, "filters.yaml").writeText(
            """
            - label: Default
              account: expenses:default
            """.trimIndent()
        )

        val loaded = FilterIO.loadFilters(dir)

        assertEquals(
            listOf(TransactionFilter(label = "Default", account = "expenses:default", mode = FilterMode.INCLUDE)),
            loaded,
        )
    }

    @Test
    fun `unknown mode string falls back to include`() {
        val dir = File(tempDir, "bogus-mode")
        dir.mkdirs()
        File(dir, "filters.yaml").writeText(
            """
            - label: Bogus
              account: expenses:bogus
              mode: bogus
            """.trimIndent()
        )

        val loaded = FilterIO.loadFilters(dir)

        assertEquals(
            listOf(TransactionFilter(label = "Bogus", account = "expenses:bogus", mode = FilterMode.INCLUDE)),
            loaded,
        )
    }
}
