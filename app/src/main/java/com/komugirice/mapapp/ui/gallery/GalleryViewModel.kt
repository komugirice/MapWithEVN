package com.komugirice.mapapp.ui.gallery

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.komugirice.mapapp.EvImageData
import com.komugirice.mapapp.ui.map.MapFragment

class GalleryViewModel : ViewModel() {
    val items = MutableLiveData<List<EvImageData>>()

    fun initData() {
        var list = mutableListOf<EvImageData>()
        MapFragment.imageMarkers.forEach {
            val evImageData = it.tag as EvImageData
            list.add(evImageData)
        }
        items.postValue(list)
    }
}
