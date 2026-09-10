package com.github.tropin.ridermcp

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class OutputSession(
    val id: String,
    val type: String,
    var status: String = "running",
    var exitCode: Int? = null,
    var progress: Double = -1.0,
    private val lines: MutableList<String> = mutableListOf(),
    private var readCursor: Int = 0
) {
    @Synchronized
    fun appendLine(line: String) {
        lines.add(line)
    }

    @Synchronized
    fun appendLines(newLines: List<String>) {
        lines.addAll(newLines)
    }

    @Synchronized
    fun getNewLines(): List<String> {
        val result = lines.subList(readCursor, lines.size).toList()
        readCursor = lines.size
        return result
    }

    @Synchronized
    fun getAllLines(): List<String> = lines.toList()
}

object SessionManager {
    private val sessions = ConcurrentHashMap<String, OutputSession>()
    private val counter = AtomicLong(0)

    fun create(type: String): OutputSession {
        val id = "${type}_${counter.incrementAndGet()}"
        val session = OutputSession(id = id, type = type)
        sessions[id] = session
        return session
    }

    fun get(id: String): OutputSession? = sessions[id]

    fun remove(id: String) {
        sessions.remove(id)
    }

    fun listByType(type: String): List<OutputSession> =
        sessions.values.filter { it.type == type }

    // ponytail: cleanup stale sessions older than 10min, call from get if needed
    fun cleanup() {
        sessions.entries.removeIf { it.value.status != "running" }
    }
}
