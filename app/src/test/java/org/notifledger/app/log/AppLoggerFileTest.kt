package org.notifledger.app.log

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * AppLogger is a global singleton shared across the JVM test run, so every test
 * detaches the sink and clears the buffer afterwards.
 */
class AppLoggerFileTest {

    private val dir = Files.createTempDirectory("notifledger-log-test").toFile()

    @After
    fun tearDown() {
        AppLogger.initLogFile(null)
        AppLogger.clear()
        dir.deleteRecursively()
    }

    @Test
    fun writesEntriesToFile() {
        val file = File(dir, "notifledger.log")
        AppLogger.initLogFile(file)

        AppLogger.info("T", "hello")
        AppLogger.warn("T", "oops")

        val text = file.readText()
        assertTrue(text.contains("hello"))
        assertTrue(text.contains("oops"))
        assertTrue(text.contains("INFO"))
        assertTrue(text.contains("WARN"))
        assertTrue(text.contains("T"))
    }

    @Test
    fun clearTruncatesFile() {
        val file = File(dir, "notifledger.log")
        AppLogger.initLogFile(file)
        AppLogger.info("T", "hello")

        AppLogger.clear()

        assertEquals("", file.readText())
    }

    @Test
    fun rotatesFileOverOneMegabyte() {
        val file = File(dir, "notifledger.log")
        val oldContent = "x".repeat(1_000_001)
        file.writeText(oldContent)

        AppLogger.initLogFile(file)

        val backup = File(dir, "notifledger.log.1")
        assertTrue(backup.exists())
        assertEquals(oldContent, backup.readText())
        assertTrue(file.length() < oldContent.length)
    }

    @Test
    fun detachedSinkDoesNotWrite() {
        val file = File(dir, "notifledger.log")
        AppLogger.initLogFile(file)
        AppLogger.info("T", "before")
        val before = file.readText()

        AppLogger.initLogFile(null)
        AppLogger.info("T", "after")

        assertEquals(before, file.readText())
        assertFalse(file.readText().contains("after"))
    }
}
