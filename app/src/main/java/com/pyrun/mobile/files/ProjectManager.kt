package com.pyrun.mobile.files

import android.content.SharedPreferences

class ProjectManager(private val prefs: SharedPreferences) {
    var currentProject: String
        get() = prefs.getString("project", FileManager.DEFAULT_PROJECT) ?: FileManager.DEFAULT_PROJECT
        set(v) = prefs.edit().putString("project", v).apply()

    var currentFile: String
        get() = prefs.getString("file", "main.py") ?: "main.py"
        set(v) = prefs.edit().putString("file", v).apply()
}
