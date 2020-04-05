package com.komugirice.mapapp.ui.notebook

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.evernote.client.android.EvernoteSession
import com.evernote.edam.type.Notebook
import com.google.gson.Gson
import com.komugirice.mapapp.MyApplication
import com.komugirice.mapapp.Prefs
import com.komugirice.mapapp.R
import com.komugirice.mapapp.task.FindNotebooksTask
import kotlinx.android.synthetic.main.activity_header.*
import kotlinx.android.synthetic.main.activity_notebook_name.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.vrallev.android.task.TaskResult

class NotebookNameActivity : AppCompatActivity() {

    var mutableIsUpdate = MutableLiveData<Boolean>()

    private val noteStoreClient = EvernoteSession.getInstance().evernoteClientFactory.noteStoreClient

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
            val notebookName = notebookNameEditText.text.toString().trim()
            if(it == true) {
                // preferenceに登録
                Prefs().notebookName.put(notebookName)
                finish()
            } else {
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.alert_create_notebook, notebookName))
                    .setPositiveButton(R.string.yes, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            // ノートブック新規作成
                            val notebook = Notebook().apply{
                                this.name = notebookName
                            }
                            CoroutineScope(Dispatchers.IO).launch {
                                noteStoreClient.createNotebook(notebook)
                            }
                            Prefs().notebookName.put(notebookName)
                            // 検索で使えない
                            //MyApplication.evNotebook = notebook
                            // notebook取得
                            FindNotebooksTask().start(this@NotebookNameActivity, "onCreated");

                        }
                    })
                    .setNeutralButton(R.string.no, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                        }
                    }).show()

            }
        })
    }

    @TaskResult(id = "personal")
    fun onFindNotebooksNotebook(notebooks: List<Notebook?>?) {
        val text = notebookNameEditText.text.toString()
        notebooks?.forEach {
            if(it != null && it?.name == text) {
                MyApplication.evNotebook = it // グローバル変数のノートブック更新
                mutableIsUpdate.postValue(true)
                return
            }
        }
        mutableIsUpdate.postValue(false)
    }

    @TaskResult(id = "onCreated")
    fun onCreatedNotebook(notebooks: List<Notebook?>?) {
        val text = notebookNameEditText.text.toString()
        notebooks?.forEach {
            if(it != null && it?.name == text) {
                MyApplication.evNotebook = it // グローバル変数のノートブック更新
                mutableIsUpdate.postValue(true)
                Toast.makeText(this@NotebookNameActivity, getString(R.string.success_create_notebook, text), Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }
        mutableIsUpdate.postValue(false)
        Toast.makeText(this@NotebookNameActivity, getString(R.string.failed_create_notebook, text), Toast.LENGTH_LONG).show()

    }

    companion object {
        fun start(activity: Activity?) =
            activity?.startActivity(
                Intent(activity, NotebookNameActivity::class.java)
            )
    }
}
