package com.komugirice.mapapp.enums

enum class Mode(val id: Int, val modeName: String) {
    CACHE(0,"アプリ内キャッシュ"),
    EVERNOTE(1,"Evernote");

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