package com.pyrun.mobile

import com.pyrun.mobile.editor.AutocompleteManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AutocompleteManagerTest {
    private val a = AutocompleteManager()

    @Test fun `prefix too short gives nothing`() = assertTrue(a.suggest("p", 1).isEmpty())

    @Test fun `document words rank before keywords`() {
        val text = "print_total = 1\npri"
        val r = a.suggest(text, text.length)
        assertEquals("print_total", r.first())
        assertTrue("print" in r)
    }

    @Test fun `exact prefix is not suggested`() {
        val text = "def foo(): pass\nfoo"
        assertFalse("foo" in a.suggest(text, text.length))
    }
}
