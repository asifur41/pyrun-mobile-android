package com.pyrun.mobile.editor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pyrun.mobile.PyRunApp
import com.pyrun.mobile.R
import com.pyrun.mobile.databinding.ActivityCodeEditorBinding
import com.pyrun.mobile.files.FileManager
import com.pyrun.mobile.runtime.ExecutionResult
import com.pyrun.mobile.runtime.ExecutionStatus
import com.pyrun.mobile.runtime.OutputCapture
import com.pyrun.mobile.runtime.PythonEngine
import com.pyrun.mobile.runtime.RuntimeExecutor
import com.pyrun.mobile.utils.StorageUtils
import java.io.IOException

class CodeEditorActivity : AppCompatActivity() {

    private lateinit var b: ActivityCodeEditorBinding
    private lateinit var app: PyRunApp
    private lateinit var executor: RuntimeExecutor
    private lateinit var palette: SyntaxPalette
    private lateinit var capture: OutputCapture
    private val highlighter = SyntaxHighlighter()
    private val autocomplete = AutocompleteManager()
    private val ui = Handler(Looper.getMainLooper())
    private var dirty = false
    private var suppressDirty = false
    private var running = false
    private var runItem: MenuItem? = null

    private val symbols = listOf(
        "⇥" to "    ", ":" to ":", "(" to "(", ")" to ")", "[" to "[", "]" to "]", "{" to "{", "}" to "}",
        "\"" to "\"", "'" to "'", "=" to "=", "#" to "#", "_" to "_", "." to ".", "," to ","
    )

    private val highlightTask = Runnable { b.editor.text?.let { SyntaxSpans.apply(it, highlighter, palette) } }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importFrom(it) } }
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/x-python")) { uri -> uri?.let { exportTo(it) } }

    private val project get() = app.projects.currentProject
    private val file get() = app.projects.currentFile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCodeEditorBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        app = application as PyRunApp
        executor = RuntimeExecutor(app.engine, app.files)

        fun c(id: Int) = ContextCompat.getColor(this, id)
        palette = SyntaxPalette(
            mapOf(
                TokenType.KEYWORD to c(R.color.tok_keyword), TokenType.BUILTIN to c(R.color.tok_builtin),
                TokenType.STRING to c(R.color.tok_string), TokenType.NUMBER to c(R.color.tok_number),
                TokenType.COMMENT to c(R.color.tok_comment), TokenType.DECORATOR to c(R.color.tok_decorator),
                TokenType.SELF to c(R.color.tok_self)
            )
        )
        capture = OutputCapture(c(R.color.editor_fg), c(R.color.console_error), c(R.color.console_system))

        b.consoleText.setText("", TextView.BufferType.EDITABLE)
        b.consoleClear.setOnClickListener { clearConsole() }
        b.consoleClose.setOnClickListener { showConsole(false) }
        b.editor.onCursorChanged = { refreshQuickBar() }
        b.editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, bf: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!suppressDirty && !dirty) { dirty = true; updateSubtitle() }
                ui.removeCallbacks(highlightTask)
                ui.postDelayed(highlightTask, 120)
                refreshQuickBar()
            }
        })
        loadFile(project, file)
    }

    // ---------- menu ----------
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.editor_menu, menu)
        runItem = menu.findItem(R.id.action_run)
        applyRunIcon()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_run -> toggleRun()
            R.id.action_save -> { saveNow(); toast("Saved") }
            R.id.action_new -> promptNewFile()
            R.id.action_open -> promptOpenFile()
            R.id.action_projects -> promptProjects()
            R.id.action_import -> importLauncher.launch(arrayOf("text/*", "application/octet-stream"))
            R.id.action_export -> exportLauncher.launch(file)
            R.id.action_delete -> confirmDelete()
            R.id.action_console -> showConsole(b.consolePanel.visibility != View.VISIBLE)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // ---------- files ----------
    private fun loadFile(proj: String, name: String) {
        try {
            val text = app.files.read(proj, name)
            app.projects.currentProject = proj
            app.projects.currentFile = name
            suppressDirty = true
            b.editor.setText(text)
            suppressDirty = false
            dirty = false
            updateSubtitle()
            ui.post(highlightTask)
        } catch (e: Exception) {
            suppressDirty = false
            val fallback = try { app.files.firstFile(proj) } catch (_: Exception) { null }
            if (fallback != null && fallback != name) loadFile(proj, fallback) else toast(e.message ?: "Cannot open file")
        }
    }

    private fun saveNow(): Boolean = try {
        app.files.write(project, file, b.editor.text.toString())
        dirty = false
        updateSubtitle()
        true
    } catch (e: Exception) { toast("Save failed: ${e.message}"); false }

    private fun saveIfDirty() { if (dirty) saveNow() }

    private fun promptNewFile() {
        val input = EditText(this).apply { hint = "file_name"; setSingleLine() }
        MaterialAlertDialogBuilder(this).setTitle("New file").setView(input)
            .setPositiveButton("Create") { _, _ ->
                try {
                    saveIfDirty()
                    val name = app.files.createFile(project, input.text.toString(), FileManager.STARTER)
                    loadFile(project, name)
                } catch (e: Exception) { toast(e.message ?: "Cannot create file") }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun promptOpenFile() {
        val names = app.files.listFiles(project)
        MaterialAlertDialogBuilder(this).setTitle("Open file in $project")
            .setItems(names.toTypedArray()) { _, i -> saveIfDirty(); loadFile(project, names[i]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun promptProjects() {
        val names = app.files.listProjects()
        val items = (names + "+ New project…").toTypedArray()
        MaterialAlertDialogBuilder(this).setTitle("Projects").setItems(items) { _, i ->
            saveIfDirty()
            if (i < names.size) {
                loadFile(names[i], app.files.firstFile(names[i]))
            } else {
                val input = EditText(this).apply { hint = "project_name"; setSingleLine() }
                MaterialAlertDialogBuilder(this).setTitle("New project").setView(input)
                    .setPositiveButton("Create") { _, _ ->
                        try {
                            val p = app.files.createProject(input.text.toString())
                            loadFile(p, app.files.firstFile(p))
                        } catch (e: Exception) { toast(e.message ?: "Cannot create project") }
                    }.setNegativeButton("Cancel", null).show()
            }
        }.show()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this).setTitle("Delete $file?")
            .setPositiveButton("Delete") { _, _ ->
                app.files.delete(project, file)
                dirty = false
                loadFile(project, app.files.firstFile(project))
            }.setNegativeButton("Cancel", null).show()
    }

    private fun importFrom(uri: android.net.Uri) {
        try {
            val text = StorageUtils.readTextFromUri(this, uri)
            val name = app.files.uniqueName(project, StorageUtils.displayName(this, uri))
            saveIfDirty()
            app.files.write(project, name, text)
            loadFile(project, name)
        } catch (e: IOException) { toast(e.message ?: "Import failed") }
    }

    private fun exportTo(uri: android.net.Uri) {
        try {
            StorageUtils.writeTextToUri(this, uri, b.editor.text.toString())
            toast("Exported")
        } catch (e: IOException) { toast(e.message ?: "Export failed") }
    }

    // ---------- run ----------
    private fun toggleRun() {
        if (running) { app.engine.stop(); return }
        saveNow()
        clearConsole()
        showConsole(true)
        setRunning(true)
        val started = executor.run(project, file, b.editor.text.toString(), listener)
        if (!started) setRunning(false)
    }

    private val listener = object : PythonEngine.Listener {
        override fun onOutput(stream: PythonEngine.Stream, text: String) {
            capture.append(b.consoleText.text as Editable, stream, text)
            b.consoleScroll.post { b.consoleScroll.fullScroll(View.FOCUS_DOWN) }
        }

        override fun onFinished(result: ExecutionResult) {
            val secs = "%.2f".format(result.durationMs / 1000.0)
            val msg = when (result.status) {
                ExecutionStatus.SUCCESS -> "\n✔ Finished in ${secs}s\n"
                ExecutionStatus.ERROR -> "\n✖ Exited with code ${result.exitCode} (${secs}s)\n"
                ExecutionStatus.TIMEOUT -> "\n⏱ Stopped: exceeded 30 s limit\n"
                ExecutionStatus.MEMORY -> "\n⛔ Stopped: exceeded 150 MB memory limit\n"
                ExecutionStatus.CANCELLED -> "\n■ Stopped by user\n"
                ExecutionStatus.CRASHED -> "\n⚠ Python runtime crashed\n"
            }
            capture.appendSystem(b.consoleText.text as Editable, msg)
            setRunning(false)
        }
    }

    private fun setRunning(r: Boolean) { running = r; applyRunIcon() }
    private fun applyRunIcon() {
        runItem?.setIcon(if (running) R.drawable.ic_stop else R.drawable.ic_play)
        runItem?.title = if (running) "Stop" else "Run"
    }

    private fun showConsole(show: Boolean) { b.consolePanel.visibility = if (show) View.VISIBLE else View.GONE }
    private fun clearConsole() { (b.consoleText.text as Editable).clear() }

    // ---------- quick bar (autocomplete or symbols) ----------
    private fun refreshQuickBar() {
        val text = b.editor.text ?: return
        val cursor = b.editor.selectionStart
        if (cursor < 0) return
        val suggestions = autocomplete.suggest(text.toString(), cursor)
        b.quickBar.removeAllViews()
        if (suggestions.isNotEmpty()) {
            suggestions.forEach { s -> b.quickBar.addView(chip(s) { applySuggestion(s) }) }
        } else {
            symbols.forEach { (label, insert) -> b.quickBar.addView(chip(label) { b.editor.insertAtCursor(insert) }) }
        }
    }

    private fun applySuggestion(s: String) {
        val e = b.editor.text ?: return
        val cur = b.editor.selectionStart
        val prefix = autocomplete.currentPrefix(e, cur)
        e.replace(cur - prefix.length, cur, s)
    }

    private fun chip(label: String, onClick: () -> Unit): TextView {
        val d = resources.displayMetrics.density
        return TextView(this).apply {
            text = label
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.editor_fg))
            setBackgroundResource(R.drawable.bg_chip)
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt()) }
            setOnClickListener { onClick() }
        }
    }

    private fun updateSubtitle() { b.toolbar.subtitle = "$project/$file${if (dirty) " •" else ""}" }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------- lifecycle ----------
    override fun onPause() { super.onPause(); saveIfDirty() }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        if (isFinishing && running) app.engine.stop()
        super.onDestroy()
    }
}
