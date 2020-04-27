package com.komugirice.mapapp.ui.preference

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.komugirice.mapapp.MyApplication.Companion.applicationContext
import com.komugirice.mapapp.MyApplication.Companion.evernoteUser
import com.komugirice.mapapp.Prefs
import com.komugirice.mapapp.R
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
        value = evernoteUser?.username ?: applicationContext.getString(R.string.none)
    }
    // ノートブック
    val notebookName = MutableLiveData<String>().apply {
        value = Prefs().notebookName.get().blockingSingle()
    }

    fun initData() {
        val prefMode = Prefs().mode.get().blockingSingle()
        mode.value = Mode.getValue(prefMode)

        evernoteName.value = evernoteUser?.username ?: applicationContext.getString(R.string.none)

        var prefName = Prefs().notebookName.get().blockingSingle()
        if(prefName.isEmpty()) prefName = applicationContext.getString(R.string.none)
        notebookName.value = prefName
    }


}