package com.komugirice.mapapp.enums

import com.komugirice.mapapp.MyApplication
import com.komugirice.mapapp.R

enum class Mode(val id: Int, val modeName: String) {
    CACHE(0, MyApplication.applicationContext.getString(R.string.enum_cache)),
    EVERNOTE(1, MyApplication.applicationContext.getString(R.string.enum_evernote));

    companion object {
        // enumへの変換を行う
        fun getValue(index: Int): Mode {
            return values().firstOrNull { it.id == index } ?: CACHE
        }
    }
    val isCache: Boolean
        get() = this == CACHE

    val isEvernote: Boolean
        get() = this == EVERNOTE
}