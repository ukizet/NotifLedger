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
    fun `format fills multiple blank amounts with zero`() {
        val tx = Transaction(
            date = "2026-07-12",
            payee = "SMS",
            postings = listOf(
                Posting("expenses:unknown", "", ""),
                Posting("assets:bank:checking", "", ""),
            ),
        )
        val entry = JournalWriter.format(tx)
        assertEquals(true, entry.contains("0.00"))

        val entries = JournalWriter.readAll(entry)
        assertEquals(1, entries.size)
        assertEquals("0.00", entries[0].postings[0].amount)
        assertEquals("0.00", entries[0].postings[1].amount)
    }

    @Test
    fun `format handles blank payee and readAll parses it`() {
        val tx = Transaction(
            date = "2026-07-12",
            payee = "",
            postings = listOf(
                Posting("expenses:groceries", "184.50", "NOK"),
                Posting("assets:bank:checking", "-184.50", "NOK"),
            ),
        )
        val entry = JournalWriter.format(tx)
        assertEquals(true, entry.startsWith("2026-07-12"))

        val entries = JournalWriter.readAll(entry)
        assertEquals(1, entries.size)
        assertEquals("2026-07-12", entries[0].date)
        // No payee on the line — parseEntry falls back to the first account.
        assertEquals("expenses:groceries", entries[0].payee)
    }

    @Test
    fun `format blank payee with note round-trips note`() {
        val tx = Transaction(
            date = "2026-07-13",
            payee = "",
            postings = listOf(
                Posting("expenses:groceries", "184.50", "NOK"),
                Posting("assets:bank:checking", "-184.50", "NOK"),
            ),
            note = "groceries",
        )
        val entry = JournalWriter.format(tx)
        assertEquals(true, entry.startsWith("2026-07-13  ; groceries"))

        val parsed = JournalWriter.readAll(entry)
        assertEquals(1, parsed.size)
        assertEquals("groceries", parsed[0].note)
    }

    @Test
    fun `format auto-balances comma decimal amounts`() {
        val tx = Transaction(
            date = "2026-07-12",
            payee = "Rema 1000",
            postings = listOf(
                Posting("expenses:groceries", "184,50", "NOK"),
                Posting("assets:bank:checking", "", "NOK"),
            ),
        )
        val result = JournalWriter.format(tx)
        assertEquals(true, result.contains("-184.50 NOK"))
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
        val result = JournalWriter.appendToContent(existing, tx).content
        assertEquals(true, result.contains("2026-07-13 Kiwi"))
        assertEquals(true, result.contains("95.00 NOK"))
        assertEquals(true, result.contains("-95.00 NOK"))
    }

    @Test
    fun `appendToContentRaw matches appendToContent content`() {
        val existing = "2026-07-12 Rema 1000\n    expenses:groceries  184.50 NOK\n    assets:bank:checking  -184.50 NOK\n"
        val tx = Transaction(
            date = "2026-07-13",
            payee = "Kiwi",
            postings = listOf(
                Posting("expenses:groceries", "95.00", "NOK"),
                Posting("assets:bank:checking", "", "NOK"),
            ),
        )
        assertEquals(
            JournalWriter.appendToContent(existing, tx).content,
            JournalWriter.appendToContentRaw(existing, tx),
        )
    }

    @Test
    fun `appendToContent returns auto-balanced posting amounts`() {
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
        val appended = result.entries.last()
        assertEquals("2026-07-13", appended.date)
        assertEquals("Kiwi", appended.payee)
        assertEquals("-95.00", appended.postings[1].amount)
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
        val result = JournalWriter.replaceInContent(content, 0, tx).content
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
        val result = JournalWriter.replaceInContent(content, 0, tx).content
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
        val result = JournalWriter.replaceInContent(content, 4, tx).content
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
        val result = JournalWriter.replaceInContent(content, 4, tx).content
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

    @Test
    fun `deleteFromContent removes entry and following blank line`() {
        val content = ("2026-07-12 Rema 1000\n" +
                "    expenses:groceries              184.50 NOK\n" +
                "    assets:bank:checking           -184.50 NOK\n" +
                "\n" +
                "2026-07-13 Kiwi\n" +
                "    expenses:groceries               95.00 NOK\n" +
                "    assets:bank:checking             -95.00 NOK\n")
        val result = JournalWriter.deleteFromContent(content, 0).content
        assertEquals(false, result.contains("Rema 1000"))
        assertEquals(true, result.contains("2026-07-13 Kiwi"))
        // No blank line before the remaining entry
        val lines = result.lines()
        assertEquals("2026-07-13 Kiwi", lines[0])
    }

    @Test
    fun `deleteFromContent removes last entry and trailing blank line`() {
        val content = ("2026-07-12 Rema 1000\n" +
                "    expenses:groceries              184.50 NOK\n" +
                "    assets:bank:checking           -184.50 NOK\n" +
                "\n" +
                "2026-07-13 Kiwi\n" +
                "    expenses:groceries               95.00 NOK\n" +
                "    assets:bank:checking             -95.00 NOK\n")
        val result = JournalWriter.deleteFromContent(content, 4).content
        assertEquals(true, result.contains("2026-07-12 Rema 1000"))
        assertEquals(false, result.contains("Kiwi"))
        // Result should not end with a blank line
        assertEquals(false, result.endsWith("\n"))
    }

    @Test
    fun `deleteFromContent removes last remaining entry and trailing blank line`() {
        val content = "2026-07-12 Rema 1000\n    expenses:groceries  184.50 NOK\n    assets:bank:checking  -184.50 NOK\n"
        val result = JournalWriter.deleteFromContent(content, 0).content
        assertEquals("", result)
    }

    @Test
    fun `deleteFromContent single entry without trailing newline`() {
        val content = "2026-07-12 Rema 1000\n    expenses:groceries  184.50 NOK\n    assets:bank:checking  -184.50 NOK"
val result = JournalWriter.deleteFromContent(content, 0).content
        assertEquals("", result)
    }

    @Test
    fun `deleteFromContent middle entry removes both blank lines`() {
        val content = ("2026-07-12 Rema 1000\n" +
                "    expenses:groceries              184.50 NOK\n" +
                "    assets:bank:checking           -184.50 NOK\n" +
                "\n" +
                "2026-07-13 Kiwi\n" +
                "    expenses:groceries               95.00 NOK\n" +
                "    assets:bank:checking             -95.00 NOK\n" +
                "\n" +
                "2026-07-14 Extra\n" +
                "    expenses:groceries               50.00 NOK\n" +
                "    assets:bank:checking             -50.00 NOK\n")
        val result = JournalWriter.deleteFromContent(content, 4).content
        assertEquals(false, result.contains("Kiwi"))
        assertEquals(true, result.contains("2026-07-12 Rema 1000"))
        assertEquals(true, result.contains("2026-07-14 Extra"))
        val lines = result.lines()
        val remaIdx = lines.indexOfFirst { it.startsWith("2026-07-12") }
        val extraIdx = lines.indexOfFirst { it.startsWith("2026-07-14") }
        assertEquals(3, extraIdx - remaIdx)
        assertEquals("2026-07-14 Extra", lines[remaIdx + 3])
    }

    @Test
    fun `replaceManyInContent updates multiple entries`() {
        val content = ("2026-07-12 Rema 1000\n" +
                "    expenses:groceries              184.50 NOK\n" +
                "    assets:bank:checking           -184.50 NOK\n" +
                "\n" +
                "2026-07-13 Kiwi\n" +
                "    expenses:groceries               95.00 NOK\n" +
                "    assets:bank:checking             -95.00 NOK\n" +
                "\n" +
                "2026-07-14 Extra\n" +
                "    expenses:groceries               50.00 NOK\n" +
                "    assets:bank:checking             -50.00 NOK\n")
        val first = Transaction(
            date = "2026-08-01",
            payee = "Rema New",
            postings = listOf(
                Posting("expenses:groceries", "184.50", "NOK"),
                Posting("assets:bank:checking", "-184.50", "NOK"),
            ),
        )
        val last = Transaction(
            date = "2026-07-14",
            payee = "Extra",
            postings = listOf(
                Posting("expenses:groceries", "50.00", "EUR"),
                Posting("assets:bank:checking", "-50.00", "EUR"),
            ),
        )
        val result = JournalWriter.replaceManyInContent(content, mapOf(0 to first, 8 to last))
        assertEquals(true, result.content.contains("2026-08-01 Rema New"))
        assertEquals(true, result.content.contains("EUR"))
        assertEquals(true, result.content.contains("2026-07-13 Kiwi"))
        assertEquals(true, result.content.contains("95.00 NOK"))
        assertEquals(3, result.entries.size)
        assertEquals("2026-08-01", result.entries[0].date)
        assertEquals("Rema New", result.entries[0].payee)
        assertEquals("Kiwi", result.entries[1].payee)
        assertEquals("EUR", result.entries[2].postings[0].currency)
        assertEquals(0, result.entries[0].lineOffset)
        assertEquals(4, result.entries[1].lineOffset)
        assertEquals(8, result.entries[2].lineOffset)
    }

    @Test
    fun `replaceManyInContent empty map returns unchanged content`() {
        val content = ("2026-07-12 Rema 1000\n" +
                "    expenses:groceries              184.50 NOK\n" +
                "    assets:bank:checking           -184.50 NOK\n")
        val result = JournalWriter.replaceManyInContent(content, emptyMap())
        assertEquals(content, result.content)
        assertEquals(JournalWriter.readAll(content), result.entries)
    }

    @Test
    fun `replaceManyInContent handles different line counts`() {
        val content = ("2026-07-12 Rema 1000\n" +
                "    expenses:groceries              184.50 NOK\n" +
                "    assets:bank:checking           -184.50 NOK\n" +
                "\n" +
                "2026-07-13 Kiwi\n" +
                "    expenses:groceries               95.00 NOK\n" +
                "    assets:bank:checking             -95.00 NOK\n")
        val first = Transaction(
            date = "2026-07-12",
            payee = "Rema 1000",
            postings = listOf(
                Posting("expenses:groceries", "100.00", "NOK"),
                Posting("expenses:snack", "84.50", "NOK"),
                Posting("assets:bank:checking", "-184.50", "NOK"),
            ),
        )
        val second = Transaction(
            date = "2026-07-14",
            payee = "Kiwi Updated",
            postings = listOf(
                Posting("expenses:groceries", "95.00", "EUR"),
                Posting("assets:bank:checking", "-95.00", "EUR"),
            ),
        )
        val result = JournalWriter.replaceManyInContent(content, mapOf(0 to first, 4 to second))
        assertEquals(JournalWriter.readAll(result.content), result.entries)
        val entries = JournalWriter.readAll(result.content)
        assertEquals(2, entries.size)
        assertEquals("Rema 1000", entries[0].payee)
        assertEquals(3, entries[0].postings.size)
        assertEquals("Kiwi Updated", entries[1].payee)
        assertEquals("EUR", entries[1].postings[0].currency)
    }

    @Test
    fun `countEntryLines stops at adjacent date line`() {
        val lines = listOf(
            "2026-07-12 A",
            "    expenses:groceries              10.00 NOK",
            "    assets:bank:checking           -10.00 NOK",
            "2026-07-13 B",
            "    expenses:groceries              20.00 NOK",
            "    assets:bank:checking           -20.00 NOK",
        )
        val count = JournalWriter.countEntryLines(lines, 0)
        assertEquals(3, count)
    }

    @Test
    fun `countEntryLines stops at adjacent date-only line`() {
        val lines = listOf(
            "2026-07-12 A",
            "    expenses:groceries              10.00 NOK",
            "    assets:bank:checking           -10.00 NOK",
            "2026-07-13",
            "    expenses:groceries              20.00 NOK",
            "    assets:bank:checking           -20.00 NOK",
        )
        val count = JournalWriter.countEntryLines(lines, 0)
        assertEquals(3, count)
    }

    @Test
    fun `delete then edit preserves following adjacent entry`() {
        val content = ("2026-07-12 First\n" +
                "    expenses:a                       10.00 NOK\n" +
                "    assets:bank:checking           -10.00 NOK\n" +
                "\n" +
                "2026-07-13 Second\n" +
                "    expenses:b                       20.00 NOK\n" +
                "    assets:bank:checking           -20.00 NOK\n" +
                "\n" +
                "2026-07-14 Third\n" +
                "    expenses:c                       30.00 NOK\n" +
                "    assets:bank:checking           -30.00 NOK\n")
        val afterDelete = JournalWriter.deleteFromContent(content, 4).content
        val edited = Transaction(
            date = "2026-07-12",
            payee = "First Changed",
            postings = listOf(
                Posting("expenses:a", "10.00", "NOK"),
                Posting("assets:bank:checking", "-10.00", "NOK"),
            ),
        )
        val afterEdit = JournalWriter.replaceInContent(afterDelete, 0, edited).content
        assertEquals(true, afterEdit.contains("2026-07-14 Third"))
        assertEquals(false, afterEdit.contains("Second"))
        assertEquals(true, afterEdit.contains("First Changed"))
    }

    @Test
    fun `replaceInContent returns entry offsets consistent with readAll`() {
        val content = ("2026-07-12 First\n" +
                "    expenses:a                       10.00 NOK\n" +
                "    assets:bank:checking           -10.00 NOK\n" +
                "2026-07-13 Second\n" +
                "    expenses:b                       20.00 NOK\n" +
                "    assets:bank:checking           -20.00 NOK\n")
        val first = Transaction(
            date = "2026-07-12",
            payee = "First",
            postings = listOf(
                Posting("expenses:a", "15.00", "NOK"),
                Posting("assets:bank:checking", "-15.00", "NOK"),
            ),
        )
        val result = JournalWriter.replaceInContent(content, 0, first)
        assertEquals(JournalWriter.readAll(result.content), result.entries)
        assertEquals(4, result.entries[1].lineOffset)
    }

    @Test
    fun `readAll parses note and replaceInContent preserves it`() {
        val content = ("2026-07-13 B  ; groceries\n" +
                "    expenses:groceries              184.50 NOK\n" +
                "    assets:bank:checking           -184.50 NOK\n")
        val parsed = JournalWriter.readAll(content)
        assertEquals("groceries", parsed[0].note)

        val replacement = Transaction(
            date = "2026-07-13",
            payee = "B",
            postings = listOf(
                Posting("expenses:groceries", "184.50", "NOK"),
                Posting("assets:bank:checking", "-184.50", "NOK"),
            ),
            note = "memo",
        )
        val result = JournalWriter.replaceInContent(content, 0, replacement)
        assertEquals(true, result.content.contains("memo"))
        assertEquals("memo", JournalWriter.readAll(result.content)[0].note)
    }
}
