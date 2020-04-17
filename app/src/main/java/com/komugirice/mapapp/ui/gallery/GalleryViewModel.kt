package com.komugirice.mapapp.ui.gallery

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.komugirice.mapapp.data.EvImageData
import com.komugirice.mapapp.data.ImageData
import com.komugirice.mapapp.MyApplication
import com.komugirice.mapapp.enums.Mode
import com.komugirice.mapapp.extension.extractPostalCode
import com.komugirice.mapapp.ui.map.MapFragment
import com.komugirice.mapapp.util.AppUtil

class GalleryViewModel : ViewModel() {
    var context: Context? = MyApplication.applicationContext

    val items = MutableLiveData<List<EvImageData>>()

    fun initData() {
        var list = mutableListOf<EvImageData>()
        MapFragment.imageMarkers.forEach {
            var data = EvImageData()
            if (MyApplication.mode == Mode.CACHE) {
                // キャッシュの場合もEvImageDataに合わせる
                data.applyImageData(it.tag as ImageData)
            } else {
                data = it.tag as EvImageData
            }
            context?.apply {
                // Evernoteではフルアドレスを取得できないのでlatから再取得
                data.address = AppUtil.getPostalCodeAndAllAddress(context, LatLng(data.lat, data.lon))
            }
            list.add(data)
        }
        list.sortBy { it.address.extractPostalCode() }
        items.postValue(list)
    }
}
