package com.pyrun.mobile.runtime

import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan

/** Appends colored text to the console's Editable and trims from the front when too long. */
class OutputCapture(
    private val stdoutColor: Int,
    private val stderrColor: Int,
    private val systemColor: Int,
    private val maxChars: Int = 200_000
) {
    fun append(target: Editable, stream: PythonEngine.Stream, text: String) {
        append(target, text, if (stream == PythonEngine.Stream.STDERR) stderrColor else stdoutColor)
    }

    fun appendSystem(target: Editable, text: String) = append(target, text, systemColor)

    private fun append(target: Editable, text: String, color: Int) {
        val s = SpannableString(text)
        s.setSpan(ForegroundColorSpan(color), 0, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        target.append(s)
        val excess = target.length - maxChars
        if (excess > 0) target.delete(0, excess)
    }
}
