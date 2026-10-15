package com.pyrun.mobile.runtime

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs in android:process=":python". One run at a time. Never touches UI classes. */
class ExecutionService : Service() {

    private val messenger = Messenger(Handler(Looper.getMainLooper()) { msg -> onMessage(msg); true })
    private val worker = Executors.newSingleThreadExecutor()
    private val timers = Executors.newScheduledThreadPool(2)
    @Volatile private var client: Messenger? = null

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        worker.shutdownNow()
        timers.shutdownNow()
        super.onDestroy()
    }

    private fun onMessage(msg: Message) {
        if (msg.what != Protocol.MSG_RUN) return
        val data = msg.data
        val runId = msg.arg1
        val code = data.getString(Protocol.KEY_CODE).orEmpty()
        val filename = data.getString(Protocol.KEY_FILENAME).orEmpty()
        val workdir = data.getString(Protocol.KEY_WORKDIR).orEmpty()
        client = msg.replyTo
        send(Protocol.MSG_STARTED, runId, Process.myPid())
        worker.execute { runScript(runId, code, filename, workdir) }
    }

    private fun runScript(runId: Int, code: String, filename: String, workdir: String) {
        val sink = OutputSink { stream, text -> send(Protocol.MSG_OUT, stream, runId, text) }
        var exitCode = 1
        val startedAt = SystemClock.elapsedRealtime()
        try {
            if (!Python.isStarted()) Python.start(AndroidPlatform(applicationContext))
            val baselineKb = Debug.getPss()
            val flusher = timers.scheduleWithFixedDelay(
                { sink.flush() }, Protocol.FLUSH_INTERVAL_MS, Protocol.FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS
            )
            val watchdog = timers.scheduleWithFixedDelay({
                val usedMb = (Debug.getPss() - baselineKb) / 1024
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (usedMb > Protocol.MAX_MEMORY_MB) {
                    sink.write(Protocol.STREAM_STDERR, "\nMemoryError: exceeded ${Protocol.MAX_MEMORY_MB} MB limit\n")
                    sink.flush()
                    send(Protocol.MSG_DONE, runId, Protocol.RC_MEMORY)
                    Thread.sleep(100) // let the one-way Binder messages leave
                    Process.killProcess(Process.myPid())
                } else if (elapsed > Protocol.SERVICE_BACKSTOP_MS) {
                    Process.killProcess(Process.myPid()) // client already gave up
                }
            }, Protocol.WATCHDOG_INTERVAL_MS, Protocol.WATCHDOG_INTERVAL_MS, TimeUnit.MILLISECONDS)
            try {
                exitCode = Python.getInstance()
                    .getModule("runner")
                    .callAttr("run", code, filename, workdir, sink)
                    .toInt()
            } finally {
                flusher.cancel(false)
                watchdog.cancel(false)
            }
        } catch (t: Throwable) {
            sink.write(Protocol.STREAM_STDERR, "Internal error: ${t.message}\n")
        } finally {
            sink.flush()
        }
        send(Protocol.MSG_DONE, runId, exitCode)
    }

    private fun send(what: Int, arg1: Int, arg2: Int, text: String? = null) {
        val m = Message.obtain(null, what, arg1, arg2)
        if (text != null) m.data = Bundle().apply { putString(Protocol.KEY_TEXT, text) }
        try {
            client?.send(m)
        } catch (_: RemoteException) {
            // Client gone; nothing to do.
        }
    }
}
