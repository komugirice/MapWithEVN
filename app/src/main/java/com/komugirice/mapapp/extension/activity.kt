package com.komugirice.mapapp.extension

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager


/**
 * hideKeybordメソッド
 *
 */
fun Activity.hideKeybord(view: View) {
    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
        view.windowToken,
        InputMethodManager.HIDE_NOT_ALWAYS
    )
}
