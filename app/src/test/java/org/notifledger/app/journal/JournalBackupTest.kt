package org.notifledger.app.journal

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalBackupTest {

    @Test
    fun `namesToDelete deletes the two oldest of twelve and nothing else`() {
        val names = listOf(
            "journal_backup_20260112_141414_141.txt",
            "journal_backup_20260101_090000_000.txt",
            "journal_backup_20260107_010203_004.txt",
            "journal_backup_20260102_120000_500.txt",
            "journal_backup_20260110_121212_121.txt",
            "journal_backup_20260103_174500_999.txt",
            "journal_backup_20260111_131313_131.txt",
            "journal_backup_20260104_235959_001.txt",
            "journal_backup_20260108_101010_010.txt",
            "journal_backup_20260105_083000_250.txt",
            "journal_backup_20260109_111111_111.txt",
            "journal_backup_20260106_000000_000.txt",
        )

        val deleted = JournalBackup.namesToDelete(names, keep = 10)

        assertEquals(
            listOf(
                "journal_backup_20260101_090000_000.txt",
                "journal_backup_20260102_120000_500.txt",
            ),
            deleted,
        )
    }

    @Test
    fun `namesToDelete returns empty when fewer names than keep`() {
        val names = (0 until 5).map { JournalBackup.fileName(LocalDateTime.of(2026, 1, 1, 9, 0, it)) }

        assertEquals(emptyList<String>(), JournalBackup.namesToDelete(names, keep = 10))
    }

    @Test
    fun `namesToDelete returns empty when names exactly equal keep`() {
        val names = (0 until 10).map { JournalBackup.fileName(LocalDateTime.of(2026, 1, 1, 9, 0, it)) }

        assertEquals(emptyList<String>(), JournalBackup.namesToDelete(names, keep = 10))
    }

    @Test
    fun `fileName sorts lexicographically in chronological order`() {
        val earlier = JournalBackup.fileName(LocalDateTime.of(2026, 12, 31, 23, 59, 59, 999_000_000))
        val later = JournalBackup.fileName(LocalDateTime.of(2027, 1, 1, 0, 0, 0, 0))

        assertEquals("journal_backup_20261231_235959_999.txt", earlier)
        assertTrue(earlier < later)
    }

    @Test
    fun `namesToDelete never touches files that are not backups`() {
        val names = listOf(
            "notes.txt",
            "journal_backup_manual.txt",
            "journal_backup_20260101_090000_000.txt.bak",
            "journal_backup_20260101_090000_000.txt",
            "journal_backup_20260102_090000_000.txt",
        )

        assertEquals(
            listOf("journal_backup_20260101_090000_000.txt"),
            JournalBackup.namesToDelete(names, keep = 1),
        )
    }
}
