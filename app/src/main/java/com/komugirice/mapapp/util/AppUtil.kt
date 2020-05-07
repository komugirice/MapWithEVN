package com.komugirice.mapapp.util

import android.content.Context
import android.location.Geocoder
import android.os.Environment
import com.google.android.gms.maps.model.LatLng
import com.komugirice.mapapp.MyApplication
import com.komugirice.mapapp.MyApplication.Companion.applicationContext
import com.komugirice.mapapp.extension.extractPostalCodeAndAllAddress
import com.komugirice.mapapp.extension.extractPostalCodeAndHalfAddress
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
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

    /**
     * ExternalStorageに画像ファイル作成
     */
    @Throws(IOException::class)
    fun createImageFileToStorage(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = MyApplication.applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
    }

    /**
     * キャッシュに画像ファイル作成
     */
    @Throws(IOException::class)
    fun createImageFileToCache(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = MyApplication.applicationContext.cacheDir
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
    }
}