package com.pyrun.mobile.runtime

import com.pyrun.mobile.files.FileManager
import java.io.IOException

class RuntimeExecutor(private val engine: PythonEngine, private val files: FileManager) {

    /** Saves the file, then runs it with the project directory as cwd. Returns false if it could not start. */
    fun run(project: String, fileName: String, source: String, listener: PythonEngine.Listener): Boolean {
        return try {
            files.write(project, fileName, source)
            engine.execute(source, fileName, files.projectDir(project).absolutePath, listener)
        } catch (e: IOException) {
            listener.onOutput(PythonEngine.Stream.STDERR, "Could not save file: ${e.message}\n")
            false
        } catch (e: IllegalArgumentException) {
            listener.onOutput(PythonEngine.Stream.STDERR, "${e.message}\n")
            false
        }
    }
}
