package com.komugirice.mapapp

import com.google.android.gms.maps.model.Marker
import com.squareup.picasso.Callback
import java.lang.Exception

/**
 * @author Jane
 */
class InfoWindowRefresher(private val marker: Marker): Callback {

    override fun onSuccess() {
        marker.showInfoWindow()
    }

    override fun onError(e: Exception?) {}
}