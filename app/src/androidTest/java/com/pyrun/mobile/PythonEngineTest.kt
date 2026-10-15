package com.pyrun.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.pyrun.mobile.runtime.ExecutionResult
import com.pyrun.mobile.runtime.ExecutionStatus
import com.pyrun.mobile.runtime.PythonEngine
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PythonEngineTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private lateinit var engine: PythonEngine
    private lateinit var dir: String

    @Before fun setUp() {
        val ctx = inst.targetContext
        dir = ctx.cacheDir.absolutePath
        inst.runOnMainSync { engine = PythonEngine(ctx) }
    }
    @After fun tearDown() { inst.runOnMainSync { engine.cleanup() } }

    private fun run(code: String, waitSec: Long = 20): Triple<String, String, ExecutionResult?> {
        val out = StringBuffer(); val err = StringBuffer()
        var result: ExecutionResult? = null
        val latch = CountDownLatch(1)
        inst.runOnMainSync {
            engine.execute(code, "t.py", dir, object : PythonEngine.Listener {
                override fun onOutput(stream: PythonEngine.Stream, text: String) {
                    (if (stream == PythonEngine.Stream.STDERR) err else out).append(text)
                }
                override fun onFinished(r: ExecutionResult) { result = r; latch.countDown() }
            })
        }
        latch.await(waitSec, TimeUnit.SECONDS)
        return Triple(out.toString(), err.toString(), result)
    }

    @Test fun printsHello() {
        val (out, _, r) = run("print('hi', 1+1)")
        assertEquals("hi 2\n", out); assertEquals(ExecutionStatus.SUCCESS, r?.status)
    }

    @Test fun exceptionGoesToStderrWithoutRunnerFrames() {
        val (_, err, r) = run("1/0")
        assertEquals(ExecutionStatus.ERROR, r?.status)
        assertTrue(err.contains("ZeroDivisionError")); assertFalse(err.contains("runner.py"))
    }

    @Test fun sysExitCodePropagates() {
        val (_, _, r) = run("import sys; sys.exit(3)")
        assertEquals(3, r?.exitCode)
    }

    @Test fun networkIsBlocked() {
        val (_, err, r) = run("import socket; socket.create_connection(('example.com', 80))")
        assertEquals(ExecutionStatus.ERROR, r?.status); assertTrue(err.contains("offline"))
    }

    @Test fun inputRaisesEOF() {
        val (_, err, _) = run("input()")
        assertTrue(err.contains("EOFError"))
    }

    @LargeTest @Test fun infiniteLoopTimesOutThenEngineStillWorks() {
        val (_, _, r) = run("while True: pass", waitSec = 40)
        assertEquals(ExecutionStatus.TIMEOUT, r?.status)
        val (out, _, r2) = run("print('alive')")
        assertEquals("alive\n", out); assertEquals(ExecutionStatus.SUCCESS, r2?.status)
    }

    @LargeTest @Test fun hugeAllocationIsStopped() {
        val (_, _, r) = run("x = bytearray(400*1024*1024)\nimport time; time.sleep(5)", waitSec = 30)
        assertEquals(ExecutionStatus.MEMORY, r?.status)
    }
}
