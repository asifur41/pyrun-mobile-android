package com.pyrun.mobile

import com.pyrun.mobile.files.FileManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class FileManagerTest {
    private lateinit var root: File
    private lateinit var fm: FileManager

    @BeforeEach fun setUp() { root = Files.createTempDirectory("pyrun").toFile(); fm = FileManager(root) }
    @AfterEach fun tearDown() { root.deleteRecursively() }

    @Test fun `create write read roundtrip`() {
        fm.createProject("p")
        val name = fm.createFile("p", "main", "print(1)\n")
        assertEquals("main.py", name)
        assertEquals("print(1)\n", fm.read("p", "main.py"))
    }

    @Test fun `write overwrites atomically and leaves no tmp`() {
        fm.createProject("p"); fm.createFile("p", "a", "1"); fm.write("p", "a.py", "2")
        assertEquals("2", fm.read("p", "a"))
        assertEquals(listOf("a.py"), fm.listFiles("p"))
        assertFalse(File(root, "p/a.py.tmp").exists())
    }

    @Test fun `duplicate create fails`() {
        fm.createProject("p"); fm.createFile("p", "a")
        assertThrows(IOException::class.java) { fm.createFile("p", "a") }
    }

    @Test fun `invalid and traversal names rejected`() {
        fm.createProject("p")
        assertThrows(IllegalArgumentException::class.java) { fm.createFile("p", "../evil") }
        assertThrows(IllegalArgumentException::class.java) { fm.createFile("p", "1abc") }
        assertThrows(IllegalArgumentException::class.java) { fm.projectDir("a/b") }
    }

    @Test fun `rename and delete`() {
        fm.createProject("p"); fm.createFile("p", "a")
        assertEquals("b.py", fm.rename("p", "a", "b"))
        assertTrue(fm.delete("p", "b"))
        assertTrue(fm.listFiles("p").isEmpty())
    }

    @Test fun `sanitize and uniqueName`() {
        assertEquals("_9lives", fm.sanitizeName("9lives.py"))
        assertEquals("my_file", fm.sanitizeName("my-file.py"))
        fm.createProject("p"); fm.createFile("p", "x")
        assertEquals("x_2.py", fm.uniqueName("p", "x.py"))
    }
}
