package org.notifledger.app.log

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
    private val lock = Any()

    /** Snapshot of current entries. */
    fun entries(): List<LogEntry> = synchronized(lock) { _entries.toList() }

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
        val snapshot = synchronized(lock) {
            _entries.clear()
            _entries.toList()
        }
        notifyListeners(snapshot)
    }

    private fun append(level: LogLevel, tag: String, message: String) {
        val snapshot = synchronized(lock) {
            val ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            _entries.add(LogEntry(sequence = nextSequence++, timestamp = ts, level = level, tag = tag, message = message))
            if (_entries.size > MAX_ENTRIES) _entries.removeAt(0)
            _entries.toList()
        }
        notifyListeners(snapshot)
    }

    private fun notifyListeners(snapshot: List<LogEntry>) {
        // Iterate a copy so a callback adding/removing a listener doesn't CME us.
        val toNotify = synchronized(lock) { listeners.toList() }
        for (l in toNotify) l(snapshot)
    }
}
