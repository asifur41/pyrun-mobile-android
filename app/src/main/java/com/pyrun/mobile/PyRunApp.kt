package com.pyrun.mobile

import android.app.Application
import com.pyrun.mobile.files.FileManager
import com.pyrun.mobile.files.ProjectManager
import com.pyrun.mobile.runtime.PythonEngine
import com.pyrun.mobile.utils.StorageUtils

/**
 * Application.onCreate runs in EVERY process (including :python). Keep it empty.
 * Singletons are lazy so the worker process never creates them.
 */
class PyRunApp : Application() {
    val files by lazy { FileManager(StorageUtils.projectsRoot(this)).also { it.ensureStarter() } }
    val projects by lazy { ProjectManager(getSharedPreferences("pyrun", MODE_PRIVATE)) }
    val engine by lazy { PythonEngine(this) }
}
