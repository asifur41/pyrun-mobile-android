package com.pyrun.mobile.runtime

import android.os.Handler

class TimeoutGuard(
    private val handler: Handler,
    private val timeoutMs: Long,
    private val onTimeout: () -> Unit
) {
    private val task = Runnable { onTimeout() }
    fun start() { handler.postDelayed(task, timeoutMs) }
    fun cancel() { handler.removeCallbacks(task) }
}
