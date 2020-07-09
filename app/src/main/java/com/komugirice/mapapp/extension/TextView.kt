package com.komugirice.mapapp.extension

import android.widget.TextView
import androidx.databinding.BindingAdapter

/**
 * 郵便番号を設定する
 *  @param 郵便番号込みの住所
 */
@BindingAdapter("setPostalCode")
fun TextView.setExtractPostalCode(address: String?) {
    this.text = address?.extractPostalCode()
}

/**
 * 住所を設定する
 *  @param 郵便番号込みの住所
 */
@BindingAdapter("setAddress")
fun TextView.setAddress(address: String?) {
    this.text = address?.eliminatePostalCode()
}
