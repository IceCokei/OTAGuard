package com.coke.otaguard.data

import java.text.SimpleDateFormat
import java.util.*

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val time: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val message: String
) {
    val timeStr: String get() = TIME_FORMAT.get()!!.format(Date(time))
    val tag: String get() = level.name

    companion object {
        private val TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        }
    }
}

object AppLogger {

    private const val MAX_ENTRIES = 500

    private val _logs = mutableListOf<LogEntry>()
    val logs: List<LogEntry> get() = synchronized(_logs) { _logs.toList() }

    private var onChange: (() -> Unit)? = null

    fun setListener(listener: () -> Unit) { onChange = listener }

    fun info(msg: String) = add(LogLevel.INFO, msg)
    fun warn(msg: String) = add(LogLevel.WARN, msg)
    fun error(msg: String) = add(LogLevel.ERROR, msg)

    private fun add(level: LogLevel, msg: String) {
        synchronized(_logs) {
            _logs.add(LogEntry(level = level, message = msg))
            if (_logs.size > MAX_ENTRIES) _logs.removeAt(0)
        }
        onChange?.invoke()
    }

    fun clear() {
        synchronized(_logs) { _logs.clear() }
        onChange?.invoke()
    }
}
