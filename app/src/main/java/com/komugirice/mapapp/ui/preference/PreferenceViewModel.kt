package com.komugirice.mapapp.ui.preference

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PreferenceViewModel : ViewModel() {

    val mode = MutableLiveData<String>().apply {
        value = "evernote"
    }
    val evernoteName = MutableLiveData<String>().apply {
        value = "なし"
    }
}