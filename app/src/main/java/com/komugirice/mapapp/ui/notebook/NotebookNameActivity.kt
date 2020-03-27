package com.komugirice.mapapp.ui.notebook

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.evernote.edam.type.Notebook
import com.google.gson.Gson
import com.komugirice.mapapp.Prefs
import com.komugirice.mapapp.R
import com.komugirice.mapapp.task.FindNotebooksTask
import kotlinx.android.synthetic.main.activity_header.*
import kotlinx.android.synthetic.main.activity_notebook_name.*
import net.vrallev.android.task.TaskResult

class NotebookNameActivity : AppCompatActivity() {

    var mutableIsUpdate = MutableLiveData<Boolean>()

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
            update()
        }
    }

    private fun update() {
        // notebook取得
        FindNotebooksTask().start(this, "personal");

        // onFindNotebooksの監視
        mutableIsUpdate.observe(this, Observer{
            if(it == true) {
                // preferenceに登録
                val notebookName = notebookNameEditText.text.toString().trim()
                Prefs().notebookName.put(notebookName)
                finish()
            } else {
                Toast.makeText(this, "ノートブック名が存在しません", Toast.LENGTH_LONG).show()
            }
        })
    }

    @TaskResult(id = "personal")
    fun onFindNotebooksNotebook(notebooks: List<Notebook?>?) {
        val text = notebookNameEditText.text.toString()
        notebooks?.forEach {
            if(it != null && it?.name == text) {
                mutableIsUpdate.postValue(true)
                return
            }
        }
        mutableIsUpdate.postValue(false)
    }

    companion object {
        fun start(activity: Activity?) =
            activity?.startActivity(
                Intent(activity, NotebookNameActivity::class.java)
            )
    }
}
