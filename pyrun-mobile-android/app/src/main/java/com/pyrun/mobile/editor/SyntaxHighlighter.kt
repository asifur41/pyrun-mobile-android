package com.pyrun.mobile.editor

import android.text.Editable
import android.text.Spannable
import android.text.style.ForegroundColorSpan

enum class TokenType { KEYWORD, BUILTIN, STRING, NUMBER, COMMENT, DECORATOR, SELF }

data class Token(val start: Int, val end: Int, val type: TokenType)

/** Pure tokenizer (JVM-testable). Alternation order matters: comment, triple string, string, decorator, number, word. */
class SyntaxHighlighter {

    companion object {
        const val MAX_CHARS = 200_000

        private val PATTERN = Regex(
            listOf(
                """(#[^\n]*)""",                                                                             // 1 comment
                """((?:[rRbBfFuU]{1,2})?(?:\"\"\"[\s\S]*?(?:\"\"\"|\z)|'''[\s\S]*?(?:'''|\z)))""",           // 2 triple-quoted
                """((?:[rRbBfFuU]{1,2})?(?:"(?:\\.|[^"\\\n])*"?|'(?:\\.|[^'\\\n])*'?))""",                  // 3 single-line string
                """(@[A-Za-z_][\w.]*)""",                                                                    // 4 decorator
                """(\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|0[oO][0-7_]+|\d[\d_]*(?:\.[\d_]*)?(?:[eE][+-]?\d+)?[jJ]?))""", // 5 number
                """([A-Za-z_]\w*)"""                                                                         // 6 identifier
            ).joinToString("|")
        )
    }

    fun tokenize(text: CharSequence): List<Token> {
        if (text.length > MAX_CHARS) return emptyList()
        val out = ArrayList<Token>()
        for (m in PATTERN.findAll(text)) {
            val g = m.groups
            val type: TokenType = when {
                g[1] != null -> TokenType.COMMENT
                g[2] != null || g[3] != null -> TokenType.STRING
                g[4] != null -> TokenType.DECORATOR
                g[5] != null -> TokenType.NUMBER
                else -> when {
                    m.value in PythonVocabulary.KEYWORDS -> TokenType.KEYWORD
                    m.value in PythonVocabulary.BUILTINS -> TokenType.BUILTIN
                    m.value == "self" || m.value == "cls" -> TokenType.SELF
                    else -> continue
                }
            }
            out += Token(m.range.first, m.range.last + 1, type)
        }
        return out
    }
}

class SyntaxPalette(val colors: Map<TokenType, Int>)

private class SyntaxSpan(color: Int) : ForegroundColorSpan(color)

object SyntaxSpans {
    fun apply(text: Editable, highlighter: SyntaxHighlighter, palette: SyntaxPalette) {
        for (s in text.getSpans(0, text.length, SyntaxSpan::class.java)) text.removeSpan(s)
        for (t in highlighter.tokenize(text)) {
            val color = palette.colors[t.type] ?: continue
            text.setSpan(SyntaxSpan(color), t.start, t.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
