package com.komugirice.mapapp.ui.preference

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.evernote.client.android.EvernoteSession
import com.komugirice.mapapp.MyApplication.Companion.evernoteUser
import com.komugirice.mapapp.MyApplication.Companion.isEvernoteLoggedIn
import com.komugirice.mapapp.Prefs

/**
 * @author komugirice
 */
class PreferenceViewModel : ViewModel() {

    val mode = MutableLiveData<String>().apply {
        value = "evernote"
    }
    val evernoteName = MutableLiveData<String>().apply {
        value = evernoteUser?.username ?: "なし"
    }
    val notebookName = MutableLiveData<String>().apply {
        value = Prefs().notebookName.get().blockingSingle()
    }

    fun initData() {
        evernoteName.value = evernoteUser?.username ?: "なし"

        val prefName = Prefs().notebookName.get().blockingSingle()
        notebookName.value = prefName
    }


}