package com.komugirice.mapapp.ui.notebook

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.komugirice.mapapp.Prefs
import com.komugirice.mapapp.R
import kotlinx.android.synthetic.main.activity_header.*
import kotlinx.android.synthetic.main.activity_notebook_name.*

class NotebookNameActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notebook_name)
        initialize()
    }

    private fun initialize() {
        initLayout()
        initClick()
    }

    private fun initLayout() {

        titleTextView.text = "ノートブック名"

        val notebookName = Prefs().notebookName.get().blockingSingle()
        notebookNameEditText.setText(notebookName)

    }
    private fun initClick() {
        backImageView.setOnClickListener {
            finish()
        }

        saveButton.setOnClickListener {
            if(notebookNameEditText.text.isNotEmpty())
                update()
                finish()
        }
    }

    private fun update() {
        val notebookName = notebookNameEditText.text.toString().trim()

        // notebookの存在チェック

        // preferenceに登録
        Prefs().notebookName.put(notebookName)
    }

    companion object {
        fun start(activity: Activity?) =
            activity?.startActivity(
                Intent(activity, NotebookNameActivity::class.java)
            )
    }
}
