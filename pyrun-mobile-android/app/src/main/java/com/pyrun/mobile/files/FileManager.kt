package com.pyrun.mobile.files

import java.io.File
import java.io.IOException

/**
 * Layout: <root>/<project>/<file>.py   (root = filesDir/projects)
 * Names are valid Python identifiers so `import helper` works between files.
 */
class FileManager(private val root: File) {

    companion object {
        const val EXT = ".py"
        const val DEFAULT_PROJECT = "default"
        const val MAX_BYTES = 2L * 1024 * 1024
        private val NAME = Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$")
        const val STARTER = "print(\"Hello, PyRun!\")\n"
    }

    init { root.mkdirs() }

    fun sanitizeName(raw: String): String {
        var n = raw.trim().removeSuffix(EXT).replace(Regex("[^A-Za-z0-9_]"), "_").take(64)
        if (n.isEmpty()) n = "untitled"
        if (n[0].isDigit()) n = "_$n".take(64)
        return n
    }

    private fun validate(name: String): String {
        require(NAME.matches(name)) { "Invalid name \"$name\": use letters, digits, underscore; must not start with a digit." }
        return name
    }

    private fun fileName(raw: String): String = validate(raw.trim().removeSuffix(EXT)) + EXT

    private fun inside(f: File): File {
        val rootPath = root.canonicalPath + File.separator
        require(f.canonicalPath.startsWith(rootPath)) { "Path escapes workspace" }
        return f
    }

    fun projectDir(project: String): File {
        val dir = inside(File(root, validate(project)))
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create project $project")
        return dir
    }

    fun listProjects(): List<String> =
        root.listFiles { f -> f.isDirectory }?.map { it.name }?.sorted() ?: emptyList()

    fun createProject(name: String): String {
        val n = validate(name)
        val dir = File(root, n)
        if (dir.exists()) throw IOException("Project already exists")
        if (!dir.mkdirs()) throw IOException("Cannot create project")
        return n
    }

    fun listFiles(project: String): List<String> =
        projectDir(project).listFiles { f -> f.isFile && f.name.endsWith(EXT) }?.map { it.name }?.sorted() ?: emptyList()

    fun exists(project: String, file: String): Boolean = inside(File(projectDir(project), fileName(file))).exists()

    fun read(project: String, file: String): String {
        val f = inside(File(projectDir(project), fileName(file)))
        if (!f.isFile) throw IOException("File not found: ${f.name}")
        if (f.length() > MAX_BYTES) throw IOException("File too large")
        return f.readText(Charsets.UTF_8)
    }

    /** Atomic write: temp file + rename. */
    fun write(project: String, file: String, content: String) {
        val dir = projectDir(project)
        val target = inside(File(dir, fileName(file)))
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) { tmp.delete(); throw IOException("Could not save ${target.name}") }
        }
    }

    /** @return the normalized file name (with .py). Fails if it exists. */
    fun createFile(project: String, file: String, content: String = STARTER): String {
        val name = fileName(file)
        if (exists(project, name)) throw IOException("$name already exists")
        write(project, name, content)
        return name
    }

    fun uniqueName(project: String, desired: String): String {
        val base = sanitizeName(desired)
        var candidate = base
        var i = 2
        while (exists(project, candidate)) { candidate = "${base}_$i"; i++ }
        return candidate + EXT
    }

    fun rename(project: String, old: String, new: String): String {
        val src = inside(File(projectDir(project), fileName(old)))
        val dst = inside(File(projectDir(project), fileName(new)))
        if (!src.isFile) throw IOException("File not found")
        if (dst.exists()) throw IOException("${dst.name} already exists")
        if (!src.renameTo(dst)) throw IOException("Rename failed")
        return dst.name
    }

    fun delete(project: String, file: String): Boolean =
        inside(File(projectDir(project), fileName(file))).delete()

    fun firstFile(project: String): String =
        listFiles(project).firstOrNull() ?: createFile(project, "main")

    /** First launch: make default/main.py. */
    fun ensureStarter() {
        if (listProjects().isEmpty()) createProject(DEFAULT_PROJECT)
        if (listFiles(listProjects().first()).isEmpty()) createFile(listProjects().first(), "main")
    }
}
