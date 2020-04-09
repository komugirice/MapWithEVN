package com.komugirice.mapapp.util

import android.content.Context
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import com.komugirice.mapapp.MyApplication.Companion.applicationContext
import com.komugirice.mapapp.extension.extractPostalCodeAndAllAddress
import com.komugirice.mapapp.extension.extractPostalCodeAndHalfAddress
import timber.log.Timber
import java.io.File
import java.util.*

object AppUtil {
    fun deleteCacheDir() {
        val cacheDir = File(StringBuilder().append(applicationContext.cacheDir).toString())
        Timber.d("cacheDir.exists:\${cacheDir.exists()}")
        if (!cacheDir.exists())
            return
        cacheDir.listFiles().forEach {
            Timber.d("fileName:${it.name}")
            if (it.exists()) {
                val isDeleted = it.delete()
                Timber.d("isDeleted:$isDeleted")
            }
        }
    }

    /**
     * 住所取得
     */
    fun getPostalCodeAndAllAddress(context: Context?, latLng: LatLng): String {
        return Geocoder(context, Locale.JAPAN)
            .getFromLocation(latLng.latitude, latLng.longitude, 1)
            .get(0)
            .getAddressLine(0)
            .extractPostalCodeAndAllAddress()
    }

    /**
     * 住所取得(ノートタイトルまで）
     */
    fun getPostalCodeAndHalfAddress(context: Context?, latLng: LatLng): String {
        return Geocoder(context, Locale.JAPAN)
            .getFromLocation(latLng.latitude, latLng.longitude, 1)
            .get(0)
            .getAddressLine(0)
            .extractPostalCodeAndHalfAddress()
    }
}