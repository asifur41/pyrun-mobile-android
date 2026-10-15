package com.pyrun.mobile.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock

/**
 * All methods must be called on the main thread. Listener callbacks arrive on the main thread.
 * Public API: warmUp(), execute(), stop(), isRunning, cleanup().
 */
class PythonEngine(private val context: Context) {

    enum class Stream { STDOUT, STDERR }

    interface Listener {
        fun onOutput(stream: Stream, text: String)
        fun onFinished(result: ExecutionResult)
    }

    private class Run(val id: Int, val request: Bundle, val listener: Listener) {
        var sent = false
        var pid = 0
        var startedAtMs = 0L
    }

    private val ui = Handler(Looper.getMainLooper())
    private val reply = Messenger(Handler(Looper.getMainLooper()) { onReply(it); true })
    private var service: Messenger? = null
    private var bound = false
    private var active: Run? = null
    private var guard: TimeoutGuard? = null
    private var nextId = 1

    val isRunning: Boolean get() = active != null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = Messenger(binder)
            sendIfReady()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            finish(ExecutionStatus.CRASHED, -1) // no-op if nothing is running
        }
    }

    /** Start the worker process early so the first Run tap is fast. */
    fun warmUp() = ensureBound()

    /** @return false if a run is already active. */
    fun execute(code: String, filename: String, workdir: String, listener: Listener): Boolean {
        if (active != null) return false
        val req = Bundle().apply {
            putString(Protocol.KEY_CODE, code)
            putString(Protocol.KEY_FILENAME, filename)
            putString(Protocol.KEY_WORKDIR, workdir)
        }
        active = Run(nextId++, req, listener)
        ensureBound()
        sendIfReady()
        return true
    }

    fun stop() = terminate(ExecutionStatus.CANCELLED)

    fun cleanup() {
        active?.let { terminate(ExecutionStatus.CANCELLED) }
        resetConnection()
    }

    private fun ensureBound() {
        if (bound) return
        bound = context.bindService(
            Intent(context, ExecutionService::class.java), connection, Context.BIND_AUTO_CREATE
        )
    }

    private fun sendIfReady() {
        val run = active ?: return
        val svc = service ?: return
        if (run.sent) return
        run.sent = true
        run.startedAtMs = SystemClock.elapsedRealtime()
        val msg = Message.obtain(null, Protocol.MSG_RUN, run.id, 0).apply {
            data = run.request
            replyTo = reply
        }
        try {
            svc.send(msg)
        } catch (e: RemoteException) {
            finish(ExecutionStatus.CRASHED, -1)
            return
        }
        guard = TimeoutGuard(ui, Protocol.TIMEOUT_MS) { terminate(ExecutionStatus.TIMEOUT) }.also { it.start() }
    }

    private fun onReply(msg: Message) {
        val run = active ?: return
        when (msg.what) {
            Protocol.MSG_STARTED -> if (msg.arg1 == run.id) run.pid = msg.arg2
            Protocol.MSG_OUT -> if (msg.arg2 == run.id) {
                val stream = if (msg.arg1 == Protocol.STREAM_STDERR) Stream.STDERR else Stream.STDOUT
                run.listener.onOutput(stream, msg.data.getString(Protocol.KEY_TEXT).orEmpty())
            }
            Protocol.MSG_DONE -> if (msg.arg1 == run.id) {
                val rc = msg.arg2
                val pid = run.pid
                val status = when {
                    rc == Protocol.RC_MEMORY -> ExecutionStatus.MEMORY
                    rc == 0 -> ExecutionStatus.SUCCESS
                    else -> ExecutionStatus.ERROR
                }
                finish(status, rc)
                if (status == ExecutionStatus.MEMORY) { killWorker(pid); resetConnection() }
            }
        }
    }

    /** Hard-stop the current run by killing the worker process, then allow a fresh bind. */
    private fun terminate(status: ExecutionStatus) {
        val run = active ?: return
        val pid = run.pid
        finish(status, -1)
        killWorker(pid)
        resetConnection()
    }

    private fun killWorker(pid: Int) {
        if (pid > 0) Process.killProcess(pid)
    }

    private fun resetConnection() {
        if (bound) {
            try { context.unbindService(connection) } catch (_: IllegalArgumentException) {}
            bound = false
        }
        service = null
    }

    private fun finish(status: ExecutionStatus, rc: Int) {
        val run = active ?: return
        active = null
        guard?.cancel()
        guard = null
        val duration = if (run.startedAtMs > 0) SystemClock.elapsedRealtime() - run.startedAtMs else 0L
        run.listener.onFinished(ExecutionResult(status, rc, duration))
    }
}
