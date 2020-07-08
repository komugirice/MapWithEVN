package com.komugirice.mapapp.ui.notebook

import android.app.Activity
import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.evernote.client.android.EvernoteSession
import com.evernote.edam.type.Notebook
import com.komugirice.mapapp.MyApplication
import com.komugirice.mapapp.MyApplication.Companion.noteStoreClient
import com.komugirice.mapapp.Prefs
import com.komugirice.mapapp.task.FindNotebooksTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotebookNameViewModel: ViewModel() {
    var activity: Activity? = null
    private val noteStoreClient = EvernoteSession.getInstance().evernoteClientFactory.noteStoreClient

    var liveIsUpdate = MutableLiveData<Boolean>()

    fun callOnCreateNotebook(notebookName: String) {
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
        FindNotebooksTask().start(activity, "onCreated");
    }
}