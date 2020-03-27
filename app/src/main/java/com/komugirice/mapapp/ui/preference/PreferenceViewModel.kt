package com.komugirice.mapapp.ui.preference

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.evernote.client.android.EvernoteSession
import com.komugirice.mapapp.MyApplication.Companion.evernoteUser
import com.komugirice.mapapp.MyApplication.Companion.isEvernoteLoggedIn
import com.komugirice.mapapp.Prefs
import com.komugirice.mapapp.enums.Mode

/**
 * @author komugirice
 */
class PreferenceViewModel : ViewModel() {

    // モード
    val mode = MutableLiveData<Mode>().apply {
        value = Mode.getValue(Prefs().mode.get().blockingSingle())
    }
    // Evernote連携
    val evernoteName = MutableLiveData<String>().apply {
        value = evernoteUser?.username ?: "なし"
    }
    // ノートブック
    val notebookName = MutableLiveData<String>().apply {
        value = Prefs().notebookName.get().blockingSingle()
    }

    fun initData() {
        val prefMode = Prefs().mode.get().blockingSingle()
        mode.value = Mode.getValue(prefMode)

        evernoteName.value = evernoteUser?.username ?: "なし"

        val prefName = Prefs().notebookName.get().blockingSingle()
        notebookName.value = prefName
    }


}