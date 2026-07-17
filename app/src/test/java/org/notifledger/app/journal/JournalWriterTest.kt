package org.notifledger.app.journal

import org.junit.Assert.assertEquals
import org.junit.Test
import org.notifledger.app.model.Posting
import org.notifledger.app.model.Transaction

class JournalWriterTest {

    @Test
    fun `format two postings with amount`() {
        val tx = Transaction(
            date = "2026-07-12",
            payee = "Rema 1000",
            postings = listOf(
                Posting("expenses:groceries", "184.50", "NOK"),
                Posting("assets:bank:checking", "-184.50", "NOK"),
            ),
        )
        val result = JournalWriter.format(tx)
        assertEquals(true, result.startsWith("2026-07-12 Rema 1000"))
        assertEquals(true, result.contains("expenses:groceries"))
        assertEquals(true, result.contains("184.50 NOK"))
        assertEquals(true, result.contains("-184.50 NOK"))
    }

    @Test
    fun `format auto-balances empty posting`() {
        val tx = Transaction(
            date = "2026-07-12",
            payee = "Rema 1000",
            postings = listOf(
                Posting("expenses:groceries", "184.50", "NOK"),
                Posting("assets:bank:checking", "", "NOK"),
            ),
        )
        val result = JournalWriter.format(tx)
        assertEquals(true, result.contains("184.50 NOK"))
        assertEquals(true, result.contains("-184.50 NOK"))
    }

    @Test
    fun `format empty posting with multiple filled postings`() {
        val tx = Transaction(
            date = "2026-07-12",
            payee = "Split purchase",
            postings = listOf(
                Posting("expenses:groceries", "50.00", "NOK"),
                Posting("expenses:snack", "30.00", "NOK"),
                Posting("assets:bank:checking", "", "NOK"),
            ),
        )
        val result = JournalWriter.format(tx)
        assertEquals(true, result.contains("50.00 NOK"))
        assertEquals(true, result.contains("30.00 NOK"))
        assertEquals(true, result.contains("-80.00 NOK"))
    }

    @Test
    fun `readAll parses simple journal`() {
        val content = """
            |2026-07-12 Rema 1000
            |    expenses:groceries              184.50 NOK
            |    assets:bank:checking           -184.50 NOK
        """.trimMargin()
        val entries = JournalWriter.readAll(content)
        assertEquals(1, entries.size)
        assertEquals("2026-07-12", entries[0].date)
        assertEquals("Rema 1000", entries[0].payee)
        assertEquals(2, entries[0].postings.size)
    }

    @Test
    fun `readAll parses multi-line entries`() {
        val content = """
            |2026-07-12 Rema 1000
            |    expenses:groceries              184.50 NOK
            |    assets:bank:checking           -184.50 NOK
            |
            |2026-07-13 Kiwi
            |    expenses:groceries               95.00 NOK
            |    assets:bank:checking
        """.trimMargin()
        val entries = JournalWriter.readAll(content)
        assertEquals(2, entries.size)
        assertEquals("Kiwi", entries[1].payee)
    }

    @Test
    fun `readAll parses entry with missing amount`() {
        val content = """
            |2026-07-12 Rema 1000
            |    expenses:groceries              184.50 NOK
            |    assets:bank:checking
        """.trimMargin()
        val entries = JournalWriter.readAll(content)
        assertEquals(1, entries.size)
        assertEquals("", entries[0].postings[1].amount)
    }

    @Test
    fun `appendToContent adds entry`() {
        val existing = "2026-07-12 Rema 1000\n    expenses:groceries  184.50 NOK\n    assets:bank:checking  -184.50 NOK\n"
        val tx = Transaction(
            date = "2026-07-13",
            payee = "Kiwi",
            postings = listOf(
                Posting("expenses:groceries", "95.00", "NOK"),
                Posting("assets:bank:checking", "", "NOK"),
            ),
        )
        val result = JournalWriter.appendToContent(existing, tx)
        assertEquals(true, result.contains("2026-07-13 Kiwi"))
        assertEquals(true, result.contains("95.00 NOK"))
        assertEquals(true, result.contains("-95.00 NOK"))
    }

    @Test
    fun `replaceInContent updates entry`() {
        val content = "2026-07-12 Rema 1000\n    expenses:groceries  184.50 NOK\n    assets:bank:checking  -184.50 NOK\n"
        val tx = Transaction(
            date = "2026-07-12",
            payee = "Rema 1000",
            postings = listOf(
                Posting("expenses:groceries", "200.00", "NOK"),
                Posting("assets:bank:checking", "", "NOK"),
            ),
        )
        val result = JournalWriter.replaceInContent(content, 0, tx)
        assertEquals(true, result.contains("200.00 NOK"))
        assertEquals(true, result.contains("-200.00 NOK"))
        assertEquals(false, result.contains("184.50"))
    }

    @Test
    fun `readAll empty content returns empty list`() {
        assertEquals(emptyList<JournalEntry>(), JournalWriter.readAll(""))
        assertEquals(emptyList<JournalEntry>(), JournalWriter.readAll("   "))
    }

    @Test
    fun `readAll handles blank lines and spacing`() {
        val content = """
            |
            |2026-07-12  Rema 1000
            |    expenses:groceries              184.50 NOK
            |    assets:bank:checking           -184.50 NOK
            |
            |
        """.trimMargin()
        val entries = JournalWriter.readAll(content)
        assertEquals(1, entries.size)
    }

    @Test
    fun `replaceInContent with multi-entry content preserves blank line separator`() {
        // Content: 8 newlines → .lines() produces 9 elements (including trailing empties)
        val content = ("2026-07-12 Rema 1000\n" +
                "    expenses:groceries              184.50 NOK\n" +
                "    assets:bank:checking           -184.50 NOK\n" +
                "\n" +
                "2026-07-13 Kiwi\n" +
                "    expenses:groceries               95.00 NOK\n" +
                "    assets:bank:checking             -95.00 NOK\n" +
                "\n")
        val tx = Transaction(
            date = "2026-07-12",
            payee = "Rema 1000",
            postings = listOf(
                Posting("expenses:groceries", "200.00", "NOK"),
                Posting("assets:bank:checking", "-200.00", "NOK"),
            ),
        )
        val result = JournalWriter.replaceInContent(content, 0, tx)
        val lines = result.lines()
        assertEquals("blank line separator between entries must be preserved", "", lines[3])
        assertEquals("second entry should follow the blank separator", "2026-07-13 Kiwi", lines[4])
        // Old amount should be gone, new amount present
        assertEquals(false, result.contains("184.50"))
        assertEquals(true, result.contains("200.00"))
    }

    @Test
    fun `replaceInContent with last entry adds blank line separator`() {
        // Content: 8 newlines → .lines() produces 9 elements
        val content = ("2026-07-12 Rema 1000\n" +
                "    expenses:groceries              184.50 NOK\n" +
                "    assets:bank:checking           -184.50 NOK\n" +
                "\n" +
                "2026-07-13 Kiwi\n" +
                "    expenses:groceries               95.00 NOK\n" +
                "    assets:bank:checking             -95.00 NOK\n" +
                "\n")
        val tx = Transaction(
            date = "2026-07-13",
            payee = "Kiwi",
            postings = listOf(
                Posting("expenses:groceries", "100.00", "NOK"),
                Posting("assets:bank:checking", "-100.00", "NOK"),
            ),
        )
        val result = JournalWriter.replaceInContent(content, 4, tx)
        val lines = result.lines()
        assertEquals("blank line separator between entries must be preserved", "", lines[3])
        assertEquals("first entry unchanged", "2026-07-12 Rema 1000", lines[0])
        assertEquals("second entry updated", "2026-07-13 Kiwi", lines[4])
        // Old amount should be gone, new amount present
        assertEquals(false, result.contains("95.00"))
        assertEquals(true, result.contains("100.00"))
    }

    @Test
    fun `replaceInContent with entry at end of file without trailing newline`() {
        // Content ends without \n → .lines() produces 7 elements (no trailing empty)
        val content = ("2026-07-12 Rema 1000\n" +
                "    expenses:groceries              184.50 NOK\n" +
                "    assets:bank:checking           -184.50 NOK\n" +
                "\n" +
                "2026-07-13 Kiwi\n" +
                "    expenses:groceries               95.00 NOK\n" +
                "    assets:bank:checking             -95.00 NOK")
        val tx = Transaction(
            date = "2026-07-13",
            payee = "Kiwi",
            postings = listOf(
                Posting("expenses:groceries", "100.00", "NOK"),
                Posting("assets:bank:checking", "-100.00", "NOK"),
            ),
        )
        val result = JournalWriter.replaceInContent(content, 4, tx)
        val lines = result.lines()
        assertEquals(7, lines.size)
        assertEquals("blank line separator between entries must be preserved", "", lines[3])
        assertEquals("2026-07-13 Kiwi", lines[4])
        // Old amount should be gone, new amount present
        assertEquals(false, result.contains("95.00"))
        assertEquals(true, result.contains("100.00"))
    }

    @Test
    fun `countEntryLines does not count blank line`() {
        val lines = listOf(
            "2026-07-12 Rema 1000",
            "    expenses:groceries              184.50 NOK",
            "    assets:bank:checking           -184.50 NOK",
            "",
            "2026-07-13 Kiwi",
            "    expenses:groceries               95.00 NOK",
            "    assets:bank:checking             -95.00 NOK",
        )
        val count = JournalWriter.countEntryLines(lines, 0)
        assertEquals(3, count)  // date line + 2 posting lines; blank line NOT counted
    }
}
