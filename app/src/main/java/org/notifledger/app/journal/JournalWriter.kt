package org.notifledger.app.journal

import org.notifledger.app.model.Posting
import org.notifledger.app.model.Transaction

/**
 * Reads and writes hledger journal text.
 *
 * Pure text operations — no file I/O. The ViewModel handles reading/writing
 * through Android's ContentResolver (so content:// URIs work).
 */
object JournalWriter {

    /**
     * Format a transaction as an hledger journal entry string.
     *
     * Auto-balances: if exactly one posting has an empty amount, the
     * negative total of the other postings is computed and used.
     *
     *   2026-07-12 Rema 1000
     *       expenses:groceries              184.50 NOK
     *       assets:bank:checking           -184.50 NOK
     */
    fun format(tx: Transaction): String {
        val postings = tx.postings

        // Auto-balance: find the one empty amount posting and fill it
        val resolved = if (postings.count { it.amount.isBlank() } == 1) {
            val nonEmptyTotal = postings
                .filter { it.amount.isNotBlank() }
                .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
            val balanceAmount = String.format("%.2f", -nonEmptyTotal).replace(",", ".")
            postings.map { p ->
                if (p.amount.isBlank()) p.copy(amount = balanceAmount) else p
            }
        } else {
            postings
        }

        val dateLine = buildString {
            append(tx.date)
            append(" ")
            append(tx.payee)
            if (tx.note.isNotBlank()) {
                append("  ; ")
                append(tx.note)
            }
        }
        val postingLines = resolved.joinToString("\n") { p ->
            val amountPart = if (p.amount.isNotBlank()) "${p.amount} ${p.currency}" else ""
            "    ${p.account}${pad(p.account, 35)}$amountPart"
        }
        return "$dateLine\n$postingLines"
    }

    /**
     * Append a transaction to existing journal content.
     */
    fun appendToContent(existingContent: String, tx: Transaction): String {
        val entry = format(tx)
        return existingContent.trimEnd() + "\n\n" + entry + "\n"
    }

    /**
     * Replace a transaction at a given line offset in the journal content.
     */
    fun replaceInContent(content: String, lineOffset: Int, tx: Transaction): String {
        val lines = content.lines().toMutableList()
        val entryLineCount = countEntryLines(lines, lineOffset)
        val newEntry = format(tx).lines()

        // Remove old entry lines
        repeat(entryLineCount) { if (lineOffset < lines.size) lines.removeAt(lineOffset) }
        // Insert new entry lines
        newEntry.reversed().forEach { lines.add(lineOffset, it) }

        return lines.joinToString("\n")
    }

    /**
     * Parse all transactions from journal text content.
     */
    fun readAll(content: String): List<JournalEntry> {
        if (content.isBlank()) return emptyList()
        val lines = content.lines()
        val entries = mutableListOf<JournalEntry>()

        var i = 0
        while (i < lines.size) {
            val entry = parseEntry(lines, i)
            if (entry != null) {
                entries.add(entry.toJournalEntry())
                i = entry.nextLineIndex
            } else {
                i++
            }
        }
        return entries
    }

    /** Parse a single journal entry starting at line i. Returns null if line i is not a date line. */
    private fun parseEntry(lines: List<String>, startIndex: Int): ParsedEntry? {
        val line = lines[startIndex].trim()
        val dateMatch = Regex("^(\\d{4}-\\d{2}-\\d{2})\\s+(.*)").find(line) ?: return null
        val date = dateMatch.groupValues[1]
        val payeeRaw = dateMatch.groupValues[2].substringBefore("  ;").trim()

        val (postingLines, nextIndex) = collectPostingLines(lines, startIndex + 1)
        val postings = postingLines.mapNotNull { parsePosting(it) }

        return ParsedEntry(
            date = date,
            payee = payeeRaw.ifBlank { postings.firstOrNull()?.account ?: "" },
            postings = postings,
            lineOffset = startIndex,
            nextLineIndex = nextIndex,
        )
    }

    /** Collect posting lines from `startIndex` until a blank line or next date line. */
    private fun collectPostingLines(lines: List<String>, startIndex: Int): Pair<List<String>, Int> {
        val postingLines = mutableListOf<String>()
        var j = startIndex
        while (j < lines.size) {
            val next = lines[j]
            if (next.isBlank()) { j++; break }
            if (next.matches(Regex("^\\s{4,}.*")) || next.matches(Regex("^\\t+.*")) || next.matches(Regex("^\\s+[a-z].*"))) {
                postingLines.add(next.trim())
                j++
            } else {
                break
            }
        }
        return Pair(postingLines, j)
    }

    /** Internal holder for a parsed entry, including the next line index. */
    private data class ParsedEntry(
        val date: String,
        val payee: String,
        val postings: List<Posting>,
        val lineOffset: Int,
        val nextLineIndex: Int,
    ) {
        fun toJournalEntry() = JournalEntry(date, payee, postings, lineOffset)
    }

    /**
     * Parse a single posting line into a Posting.
     * Returns null if the line does not contain at least an account name.
     */
    private fun parsePosting(line: String): Posting? {
        val parts = line.split(Regex("\\s{2,}|\\t")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val account = parts[0].trim()
        if (parts.size < 2) {
            // Account only, no amount
            return Posting(account = account, amount = "", currency = "")
        }
        val amountStr = parts[1].trim()
        val parsed = parsePostingAmount(amountStr)
        return if (parsed != null) {
            val (amt, cur) = parsed
            Posting(account = account, amount = amt, currency = cur)
        } else {
            // The second part wasn't a valid amount — treat as empty amount
            Posting(account = account, amount = "", currency = "")
        }
    }

    /**
     * Parse an amount string like "184.50 NOK" or "-184.50 NOK".
     * Returns a pair of (amountString, currency) or null if no valid amount found.
     */
    private fun parsePostingAmount(amountStr: String): Pair<String, String>? {
        val amountMatch = Regex("^(-?)([\\d.,]+)\\s*([a-zA-Z]+)$").find(amountStr)
            ?: Regex("([\\d.,]+)\\s*([a-zA-Z]+)$").find(amountStr)
        if (amountMatch != null) {
            val neg = if (amountMatch.groupValues[1] == "-") "-" else ""
            val num = amountMatch.groupValues[2].replace(",", ".")
            val cur = amountMatch.groupValues[3]
            return Pair(neg + num, cur)
        }
        // Try just a number without currency
        val numMatch = Regex("^(-?)([\\d.,]+)$").find(amountStr)
        if (numMatch != null) {
            val neg = if (numMatch.groupValues[1] == "-") "-" else ""
            val num = numMatch.groupValues[2].replace(",", ".")
            return Pair(neg + num, "")
        }
        return null
    }

    fun countEntryLines(lines: List<String>, startOffset: Int): Int {
        var count = 1
        for (k in (startOffset + 1) until lines.size) {
            val l = lines[k]
            if (l.isBlank()) { count++; break }
            if (l.matches(Regex("^\\d{4}-\\d{2}-\\d{2}\\s"))) break
            count++
        }
        return count
    }

    private fun pad(s: String, target: Int): String {
        val needed = (target - s.length).coerceAtLeast(1)
        return " ".repeat(needed)
    }
}

data class JournalEntry(
    val date: String,
    val payee: String,
    val postings: List<Posting>,
    val lineOffset: Int,
)
