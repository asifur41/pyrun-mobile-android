package com.pyrun.mobile.runtime

/** Constants shared by the UI process and the :python worker process. */
object Protocol {
    // client -> service. arg1 = runId. data = {KEY_CODE, KEY_FILENAME, KEY_WORKDIR}. replyTo = client messenger.
    const val MSG_RUN = 1
    // service -> client. arg1 = runId, arg2 = worker pid.
    const val MSG_STARTED = 2
    // service -> client. arg1 = stream (STREAM_*), arg2 = runId, data = {KEY_TEXT}.
    const val MSG_OUT = 3
    // service -> client. arg1 = runId, arg2 = exit code (0 ok, RC_MEMORY, or Python exit code).
    const val MSG_DONE = 4

    const val KEY_CODE = "code"
    const val KEY_FILENAME = "filename"
    const val KEY_WORKDIR = "workdir"
    const val KEY_TEXT = "text"

    const val STREAM_STDOUT = 0
    const val STREAM_STDERR = 1

    const val RC_MEMORY = -2

    const val TIMEOUT_MS = 30_000L          // hard wall-clock limit per run
    const val SERVICE_BACKSTOP_MS = 35_000L // service kills itself if the client never did
    const val MAX_MEMORY_MB = 150           // PSS growth above post-init baseline
    const val MAX_OUTPUT_CHARS = 1_000_000  // output beyond this is dropped
    const val WATCHDOG_INTERVAL_MS = 500L
    const val FLUSH_INTERVAL_MS = 50L
}
