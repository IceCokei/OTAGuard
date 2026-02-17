package com.coke.otaguard.data

import java.text.SimpleDateFormat
import java.util.*

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val time: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val message: String
) {
    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeStr: String get() = sdf.format(Date(time))
    val tag: String get() = level.name
}

object AppLogger {

    private val _logs = mutableListOf<LogEntry>()
    val logs: List<LogEntry> get() = _logs.toList()

    private var onChange: (() -> Unit)? = null

    fun setListener(listener: () -> Unit) { onChange = listener }

    fun info(msg: String) = add(LogLevel.INFO, msg)
    fun warn(msg: String) = add(LogLevel.WARN, msg)
    fun error(msg: String) = add(LogLevel.ERROR, msg)

    private fun add(level: LogLevel, msg: String) {
        _logs.add(LogEntry(level = level, message = msg))
        onChange?.invoke()
    }

    fun clear() {
        _logs.clear()
        onChange?.invoke()
    }
}
