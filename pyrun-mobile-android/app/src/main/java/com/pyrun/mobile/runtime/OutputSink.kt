package com.pyrun.mobile.runtime

/**
 * Receives text from Python (runner.py calls sink.write(stream, text) via Chaquopy).
 * Batches writes so a print() loop does not flood Binder.
 * IMPORTANT: public methods are invoked reflectively from Python; keep them public and
 * keep this class in proguard-rules.pro.
 */
class OutputSink(private val emit: (stream: Int, text: String) -> Unit) {
    private val lock = Any()
    private val sb = StringBuilder()
    private var currentStream = Protocol.STREAM_STDOUT
    private var total = 0L
    private var truncated = false

    fun write(stream: Int, text: String) {
        synchronized(lock) {
            if (truncated) return
            if (stream != currentStream && sb.isNotEmpty()) flushLocked()
            currentStream = stream
            total += text.length
            if (total > Protocol.MAX_OUTPUT_CHARS) {
                truncated = true
                sb.append("\n[output truncated: limit reached]\n")
                flushLocked()
                return
            }
            sb.append(text)
            if (sb.length >= 8192) flushLocked()
        }
    }

    fun flush() = synchronized(lock) { flushLocked() }

    private fun flushLocked() {
        if (sb.isEmpty()) return
        val text = sb.toString()
        sb.setLength(0)
        // Keep each Binder message far below the 1 MB transaction limit.
        text.chunked(16_384).forEach { emit(currentStream, it) }
    }
}
