package com.komugirice.mapapp.ui.map

import android.graphics.BitmapFactory
import android.os.Environment
import com.evernote.client.android.EvernoteUtil
import com.evernote.client.conn.mobile.FileData
import com.evernote.edam.type.Note
import com.evernote.edam.type.Resource
import com.evernote.edam.type.ResourceAttributes
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.komugirice.mapapp.*
import com.komugirice.mapapp.MyApplication.Companion.noteStoreClient
import com.komugirice.mapapp.data.AllImage
import com.komugirice.mapapp.data.EvImageData
import com.komugirice.mapapp.data.ImageData
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


object MapFragmentHelper: EvernoteHelper() {

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

    /**
     * マーカー作成
     */
    fun createMarker(imageData: ImageData, mMap: GoogleMap): Marker {

        val marker = mMap.addMarker(
            MarkerOptions().position(
                LatLng(
                    imageData.lat,
                    imageData.lon
                )
            ).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        )
        marker.tag = imageData
        return marker
    }

    /**
     * マーカー作成
     */
    fun createMarker(evResource: MapFragment.Companion.EvResource, mMap: GoogleMap): Marker {

        val marker = mMap.addMarker(
            MarkerOptions().position(
                LatLng(
                    evResource.resource.attributes.latitude,
                    evResource.resource.attributes.longitude
                )
            ).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        )

        // EvImageData
        val evImageData = EvImageData().apply {
            lat = evResource.resource.attributes.latitude
            lon = evResource.resource.attributes.longitude
            filePath = "file://${evResource.filePath}"
            this.address = evResource.title
            guid = evResource.resource.guid
            noteGuid = evResource.resource.noteGuid
        }
        marker.tag = evImageData
        return marker
    }

    /**
     * Evernoteデータからのマーカー作成
     */
    fun createMarkerFromEvernote(resource: Resource, address: String, mMap: GoogleMap): Marker {
        var marker = mMap.addMarker(
            MarkerOptions().position(
                LatLng(
                    resource.attributes.latitude,
                    resource.attributes.longitude
                )
            ).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        )

        // マーカー用の画像ファイル作成
        val newFile: File? = try {
            createImageFileToCache()
        } catch (ex: IOException) {
            // Error occurred while creating the File
            null
        }
        newFile?.apply {

            try {
                // 画像ファイルにevernote取得データをコピー
                writeBytes(resource.data.body)
            } catch(e: Exception) {
                // resource.data.bodyがnullでexceptionの時がある
                null
            }
            // EvImageData
            val evImageData = EvImageData().apply {
                lat = resource.attributes.latitude
                lon = resource.attributes.longitude
                filePath = "file://${newFile.path}"
                this.address = address
                guid = resource.guid
                noteGuid = resource.noteGuid
            }
            marker.tag = evImageData

        }
        return marker
    }

    fun deleteCacheImage(imageData: ImageData,
                         images: MutableList<ImageData>) {
        File(imageData.filePath).delete()
        images.remove(imageData)
        Prefs().allImage.put(AllImage().apply { allImage = images })


    }

}