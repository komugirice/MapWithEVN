package com.komugirice.mapapp.ui.gallery

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.komugirice.mapapp.EvImageData
import com.komugirice.mapapp.extension.extractPostalCode
import com.komugirice.mapapp.ui.map.MapFragment
import com.komugirice.mapapp.util.AppUtil

class GalleryViewModel : ViewModel() {
    var context: Context? = null

    val items = MutableLiveData<List<EvImageData>>()

    fun initData() {
        var list = mutableListOf<EvImageData>()
        MapFragment.imageMarkers.forEach {
            val data = it.tag as EvImageData
            context?.apply {
                // Evernoteではフルアドレスを取得できないのでlatから再取得
                data.address =AppUtil. getPostalCodeAndAllAddress(context, LatLng(data.lat, data.lon))
            }
            list.add(data)
        }
        list.sortBy { it.address.extractPostalCode() }
        items.postValue(list)
    }
}
