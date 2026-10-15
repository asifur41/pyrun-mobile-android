package com.pyrun.mobile.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import com.pyrun.mobile.R
import kotlin.math.max

/** Monospace editor: no wrapping, line-number gutter pinned to the left, Python-aware auto-indent. */
class CodeEditorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyle) {

    var onCursorChanged: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gutterBg = Paint().apply { color = ContextCompat.getColor(context, R.color.gutter_bg) }
    private val divider = Paint().apply { color = ContextCompat.getColor(context, R.color.divider); strokeWidth = density }
    private var gutterWidth = 0
    private var ready = false // text callbacks fire inside the super constructor, before fields exist

    init {
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        // After inputType: setting inputType can reset the scrolling mode. No wrapping = gutter line numbers stay exact.
        setHorizontallyScrolling(true)
        setBackgroundColor(ContextCompat.getColor(context, R.color.editor_bg))
        setTextColor(ContextCompat.getColor(context, R.color.editor_fg))
        filters = arrayOf(AutoIndentFilter())
        numberPaint.color = ContextCompat.getColor(context, R.color.gutter_fg)
        numberPaint.typeface = Typeface.MONOSPACE
        numberPaint.textAlign = Paint.Align.RIGHT
        ready = true
        updateGutter()
    }

    fun insertAtCursor(s: String) {
        val e = text ?: return
        val a = max(0, selectionStart)
        val b = max(a, selectionEnd)
        e.replace(a, b, s)
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        if (ready) updateGutter()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (ready) onCursorChanged?.invoke()
    }

    private fun updateGutter() {
        numberPaint.textSize = textSize
        val lines = (text?.count { it == '\n' } ?: 0) + 1
        val digits = max(3, lines.toString().length)
        val width = (numberPaint.measureText("0") * digits + 16 * density).toInt()
        if (width != gutterWidth) {
            gutterWidth = width
            setPadding(width + (6 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = layout ?: return
        val left = scrollX.toFloat()
        val top = scrollY.toFloat()
        val bottom = (scrollY + height).toFloat()
        canvas.drawRect(left, top, left + gutterWidth, bottom, gutterBg)
        canvas.drawLine(left + gutterWidth, top, left + gutterWidth, bottom, divider)
        // Wrapping is disabled, so visual line index == logical line index.
        val first = l.getLineForVertical(scrollY)
        val last = l.getLineForVertical(scrollY + height)
        val x = left + gutterWidth - 8 * density
        for (i in first..last) {
            canvas.drawText((i + 1).toString(), x, (l.getLineBaseline(i) + totalPaddingTop).toFloat(), numberPaint)
        }
    }

    /** Pressing Enter keeps indentation and adds 4 spaces after a line ending in ':'. */
    private class AutoIndentFilter : InputFilter {
        override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
            if (end - start != 1 || source[start] != '\n') return null
            val lineStart = dest.lastIndexOf('\n', dstart - 1) + 1
            val line = dest.subSequence(lineStart, dstart).toString()
            val indent = line.takeWhile { it == ' ' }
            val extra = if (line.trimEnd().endsWith(":")) "    " else ""
            return "\n$indent$extra"
        }
    }
}
