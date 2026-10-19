package org.notifledger.app.journal

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Timestamped journal backup names.
 *
 * The timestamp format is fixed-width, so lexicographic order is chronological
 * order — keeping the newest N is `sorted().dropLast(N)`.
 */
internal object JournalBackup {

    private const val PREFIX = "journal_backup_"
    private const val SUFFIX = ".txt"
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
    private val namePattern = Regex("""$PREFIX\d{8}_\d{6}_\d{3}\.txt""")

    fun fileName(timestamp: LocalDateTime): String =
        "$PREFIX${timestamp.format(timestampFormat)}$SUFFIX"

    /** Only names this object generates: rotation must never delete a stray file. */
    fun namesToDelete(names: List<String>, keep: Int): List<String> =
        names.filter { namePattern.matches(it) }.sorted().dropLast(keep)
}
