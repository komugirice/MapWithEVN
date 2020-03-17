package com.example.mapapp

import android.view.View

fun View.toggle(isVisible: Boolean) {
    visibility = if (isVisible) View.VISIBLE else View.GONE
}

fun View.visible(isVisible: Boolean) {
    visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
}

fun View.notPressTwice() {
    isEnabled = false
    postDelayed({ isEnabled = true }, 500L)
}

fun <T : View> T.onClick(onClick: () -> Unit) {
    this.setOnClickListener {
        notPressTwice()
        onClick.invoke()
    }
}