package org.notifledger.app.log

import java.io.File

/**
 * A single entry in the in-memory log buffer.
 */
data class LogEntry(
    val sequence: Long,         // monotonic id, unique within the process
    val timestamp: String,      // ISO-8601 local date-time
    val level: LogLevel,
    val tag: String,
    val message: String,
)

enum class LogLevel { INFO, WARN, ERROR }

/**
 * Central in-memory ring buffer for app logs.
 *
 * Thread-safe. Capped at [MAX_ENTRIES]; oldest entries drop off.
 * Listener callbacks are invoked *outside* the monitor so a slow listener
 * can't stall other log calls.
 */
object AppLogger {

    private const val MAX_ENTRIES = 500

    private val _entries = mutableListOf<LogEntry>()
    private val listeners = mutableListOf<(List<LogEntry>) -> Unit>()
    private var nextSequence = 0L
    private var logFile: File? = null
    private val lock = Any()
    private val fileLock = Any()

    /** Snapshot of current entries. */
    fun entries(): List<LogEntry> = synchronized(lock) { _entries.toList() }

    /**
     * Attach (or detach, with null) a file sink. Rotates once to "<name>.1" if the
     * existing file already exceeds ~1 MB.
     */
    fun initLogFile(file: File?) {
        synchronized(lock) { logFile = file }
        if (file == null) return
        synchronized(fileLock) {
            // ponytail: rotation is best-effort — a disk error must not crash app startup.
            runCatching {
                if (file.length() > 1_000_000) {
                    file.copyTo(File(file.parentFile, "${file.name}.1"), overwrite = true)
                    file.writeText("")
                }
            }
        }
    }

    /** Current file sink, or null when detached. */
    fun logFile(): File? = synchronized(lock) { logFile }

    /** Subscribe to every change. The callback receives a snapshot. */
    fun subscribe(onChange: (List<LogEntry>) -> Unit) {
        val snapshot = synchronized(lock) {
            listeners.add(onChange)
            _entries.toList()
        }
        onChange(snapshot)
    }

    fun unsubscribe(onChange: (List<LogEntry>) -> Unit) {
        synchronized(lock) { listeners.remove(onChange) }
    }

    fun info(tag: String, message: String) = append(LogLevel.INFO, tag, message)

    fun warn(tag: String, message: String) = append(LogLevel.WARN, tag, message)

    fun error(tag: String, message: String) = append(LogLevel.ERROR, tag, message)

    fun clear() {
        val (snapshot, file) = synchronized(lock) {
            _entries.clear()
            _entries.toList() to logFile
        }
        notifyListeners(snapshot)
        if (file != null) {
            synchronized(fileLock) { runCatching { file.writeText("") } }
        }
    }

    private fun append(level: LogLevel, tag: String, message: String) {
        val ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val snapshot = synchronized(lock) {
            _entries.add(LogEntry(sequence = nextSequence++, timestamp = ts, level = level, tag = tag, message = message))
            if (_entries.size > MAX_ENTRIES) _entries.removeAt(0)
            _entries.toList()
        }
        notifyListeners(snapshot)
        writeToFile("$ts $level $tag $message\n")
    }

    private fun writeToFile(line: String) {
        val file = synchronized(lock) { logFile } ?: return
        synchronized(fileLock) {
            // ponytail: best-effort sink — a failed write (full disk, revoked path) drops
            // one log line instead of crashing the app. Revisit if durable logs are needed.
            runCatching { file.appendText(line) }
        }
    }

    private fun notifyListeners(snapshot: List<LogEntry>) {
        // Iterate a copy so a callback adding/removing a listener doesn't CME us.
        val toNotify = synchronized(lock) { listeners.toList() }
        for (l in toNotify) l(snapshot)
    }
}
