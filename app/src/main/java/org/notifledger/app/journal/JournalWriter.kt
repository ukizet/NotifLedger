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

    private val dateLineRegex = Regex("^(\\d{4}-\\d{2}-\\d{2})\\s+(.*)")
    private val postingLine4SpaceRegex = Regex("^\\s{4,}.*")
    private val postingLineTabRegex = Regex("^\\t+.*")
    private val postingLineLowercaseRegex = Regex("^\\s+[a-z].*")
    private val postingSplitRegex = Regex("\\s{2,}|\\t")
    private val amountWithCurrencyRegex = Regex("^(-?)([\\d.,]+)\\s*([a-zA-Z]+)$")
    private val amountWithCurrencyNoSignRegex = Regex("([\\d.,]+)\\s*([a-zA-Z]+)$")
    private val amountOnlyRegex = Regex("^(-?)([\\d.,]+)$")
    private val entryDateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}\\s")

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
    fun appendToContent(existingContent: String, tx: Transaction): JournalWriteResult {
        val entry = format(tx)
        val content = existingContent.trimEnd() + "\n\n" + entry + "\n"
        val existingEntries = readAll(existingContent)
        val newEntryLineOffset = existingContent.trimEnd().lines().size + 1
        val newEntry = JournalEntry(tx.date, tx.payee, tx.postings, newEntryLineOffset)
        return JournalWriteResult(content, existingEntries + newEntry)
    }

    /**
     * Replace a transaction at a given line offset in the journal content.
     */
    fun replaceInContent(content: String, lineOffset: Int, tx: Transaction): JournalWriteResult {
        val lines = content.lines().toMutableList()
        val oldLineCount = countEntryLines(lines, lineOffset)
        val newEntryStr = format(tx)
        val newLineCount = newEntryStr.lines().size
        val delta = newLineCount - oldLineCount

        // Remove old entry lines
        repeat(oldLineCount) { if (lineOffset < lines.size) lines.removeAt(lineOffset) }
        // Insert new entry lines
        newEntryStr.lines().reversed().forEach { lines.add(lineOffset, it) }

        // Ensure blank line separator after the replaced entry
        val insertEnd = lineOffset + newLineCount
        if (insertEnd < lines.size && lines[insertEnd].isNotBlank()) {
            lines.add(insertEnd, "")
        }

        val newContent = lines.joinToString("\n")

        // Build updated entries list with correct lineOffsets
        val existingEntries = readAll(content)
        val newEntry = JournalEntry(tx.date, tx.payee, tx.postings, lineOffset)
        val newEntries = existingEntries.map { entry ->
            when {
                entry.lineOffset == lineOffset -> newEntry
                entry.lineOffset > lineOffset -> entry.copy(lineOffset = entry.lineOffset + delta)
                else -> entry
            }
        }

        return JournalWriteResult(newContent, newEntries)
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
        val dateMatch = dateLineRegex.find(line) ?: return null
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
            if (next.matches(postingLine4SpaceRegex) || next.matches(postingLineTabRegex) || next.matches(postingLineLowercaseRegex)) {
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
        val parts = line.split(postingSplitRegex).filter { it.isNotBlank() }
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
        val amountMatch = amountWithCurrencyRegex.find(amountStr)
            ?: amountWithCurrencyNoSignRegex.find(amountStr)
        if (amountMatch != null) {
            val neg = if (amountMatch.groupValues[1] == "-") "-" else ""
            val num = amountMatch.groupValues[2].replace(",", ".")
            val cur = amountMatch.groupValues[3]
            return Pair(neg + num, cur)
        }
        // Try just a number without currency
        val numMatch = amountOnlyRegex.find(amountStr)
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
            if (l.isBlank()) { break }
            if (l.matches(entryDateRegex)) break
            count++
        }
        return count
    }

    fun deleteFromContent(content: String, lineOffset: Int): JournalWriteResult {
        val lines = content.lines().toMutableList()
        val entryLineCount = countEntryLines(lines, lineOffset)
        repeat(entryLineCount) { if (lineOffset < lines.size) lines.removeAt(lineOffset) }
        var extraRemoved = 0
        // Remove blank line separator that follows the entry
        if (lineOffset < lines.size && lines[lineOffset].isBlank()) {
            lines.removeAt(lineOffset)
            extraRemoved++
        }
        // Remove blank line separator that precedes the entry
        if (lineOffset > 0 && lines[lineOffset - 1].isBlank()) {
            lines.removeAt(lineOffset - 1)
            extraRemoved++
        }
        val newContent = lines.joinToString("\n")
        val totalRemoved = entryLineCount + extraRemoved

        // Build updated entries list with correct lineOffsets
        val existingEntries = readAll(content)
        val newEntries = existingEntries
            .filter { it.lineOffset != lineOffset }
            .map { entry ->
                if (entry.lineOffset > lineOffset) {
                    entry.copy(lineOffset = entry.lineOffset - totalRemoved)
                } else {
                    entry
                }
            }

        return JournalWriteResult(newContent, newEntries)
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

/**
 * Result of a journal write operation containing updated content and parsed entries.
 */
data class JournalWriteResult(
    val content: String,
    val entries: List<JournalEntry>,
)
