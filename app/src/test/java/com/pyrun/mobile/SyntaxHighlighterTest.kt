package com.pyrun.mobile

import com.pyrun.mobile.editor.SyntaxHighlighter
import com.pyrun.mobile.editor.TokenType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SyntaxHighlighterTest {
    private val h = SyntaxHighlighter()
    private fun types(s: String) = h.tokenize(s).map { it.type }

    @Test fun `keywords builtins numbers`() {
        assertEquals(listOf(TokenType.KEYWORD, TokenType.BUILTIN, TokenType.NUMBER), types("def print 42"))
    }
    @Test fun `comment swallows rest of line including quotes`() {
        assertEquals(listOf(TokenType.COMMENT), types("# don't \"parse\" this"))
    }
    @Test fun `keyword inside string is not a keyword`() {
        assertEquals(listOf(TokenType.STRING), types("\"if for while\""))
    }
    @Test fun `triple quoted multiline string`() {
        val t = h.tokenize("x = \"\"\"a\nb\"\"\"")
        assertEquals(TokenType.STRING, t.single().type)
    }
    @Test fun `unterminated string ends at line end`() {
        val t = h.tokenize("print(\"abc\nx = 1")
        assertTrue(t.any { it.type == TokenType.STRING })
        assertTrue(t.any { it.type == TokenType.NUMBER })
    }
    @Test fun `decorator and self`() {
        assertEquals(listOf(TokenType.DECORATOR, TokenType.SELF), types("@property self"))
    }
}
