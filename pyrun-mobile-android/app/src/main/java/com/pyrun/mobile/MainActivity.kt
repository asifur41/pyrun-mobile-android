package com.pyrun.mobile

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pyrun.mobile.editor.CodeEditorActivity

/** Trampoline: warm the Python worker, open the editor, finish. No layout. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as PyRunApp).engine.warmUp()
        startActivity(Intent(this, CodeEditorActivity::class.java))
        finish()
    }
}
