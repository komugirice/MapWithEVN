package com.komugirice.mapapp.extension

import android.widget.ImageView
import androidx.databinding.BindingAdapter
import com.squareup.picasso.Picasso

/**
 * xmlでImageViewに:imageUrlを設定すると画像が取得できる
 *
 * @param url
 *
 */
@BindingAdapter("imageUrl")
fun ImageView.loadImage(url: String?) {
    Picasso.get().load(url).into(this)
}
