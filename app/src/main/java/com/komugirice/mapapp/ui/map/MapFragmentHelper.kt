package com.komugirice.mapapp.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.evernote.client.android.EvernoteUtil
import com.evernote.client.conn.mobile.FileData
import com.evernote.edam.error.EDAMUserException
import com.evernote.edam.type.Data
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
import com.komugirice.mapapp.extension.extractPostalCodeAndAllAddress
import com.komugirice.mapapp.extension.extractPostalCodeAndHalfAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.*
import java.text.SimpleDateFormat
import java.util.*


object MapFragmentHelper {

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
     * 住所取得
     */
    fun getPostalCodeAndHalfAddress(context: Context?, latLng: LatLng): String {
        return Geocoder(context, Locale.JAPAN)
            .getFromLocation(latLng.latitude, latLng.longitude, 1)
            .get(0)
            .getAddressLine(0)
            .extractPostalCodeAndHalfAddress()
    }

    /**
     * Evernoteノート情報作成
     */
    fun createEvResource(imageFile: File, latLng: LatLng, title: String): MapFragment.Companion.EvResource {
        // Hash the data in the image file. The hash is used to reference the file in the ENML note content.
        var `in` = BufferedInputStream(FileInputStream(imageFile))
        val data = FileData(EvernoteUtil.hash(`in`), imageFile)

        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.absolutePath, opts)

        val attributes = ResourceAttributes()
        attributes.fileName = imageFile.name
        attributes.longitude = latLng.longitude
        attributes.latitude = latLng.latitude

        val evResource = MapFragment.Companion.EvResource().apply {
            // クラス変数evNote設定
            this.title = title
            this.filePath = imageFile.absolutePath

            this.resource.apply{
                this.data = data
                //this.data.body = `in`.readBytes() // bodyがnullになるバグ
                //height = opts.outHeight.toShort()
                //width = opts.outWidth.toShort()
                mime = "image/jpg"
                this.attributes = attributes
                guid = UUID.randomUUID().toString()
            }

        }
        return evResource
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

    /**
     * Evernoteノート更新
     * ※同じ画像はresoucesに追加されないらしい
     */
    fun updateNoteEvResource(note: Note, resource: Resource?){

        note.content = EvernoteUtil.NOTE_PREFIX
        note.resources?.forEach {
            note.content += EvernoteUtil.createEnMediaTag(it)
        }
        resource?.apply {
            note.addToResources(resource)
            note.content += EvernoteUtil.createEnMediaTag(resource)

        }
        note.content += EvernoteUtil.NOTE_SUFFIX


        noteStoreClient?.updateNote(note)
    }

    /**
     * Evernoteノート新規登録
     */
    fun createNote(notebookGuid: String?, evResource: MapFragment.Companion.EvResource){
        val note = Note()
        note.title = evResource.title
        note.content =
            EvernoteUtil.NOTE_PREFIX +
//                    "<en-media type=\"" + evResource.resource.mime + "\" hash=\"" +
//                    EvernoteUtil.bytesToHex(evResource.resource.getData().getBodyHash()) + "\"/>" +
                    EvernoteUtil.createEnMediaTag(evResource.resource) +
                    EvernoteUtil.NOTE_SUFFIX;
        note.addToResources(evResource.resource)
        notebookGuid?.apply {
            note.notebookGuid = this
        }
        noteStoreClient?.createNote(note)
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
    fun deleteEvResouce(note: Note, resourceGuid: String): Note? {
        //var note = MyApplication.noteStoreClient?.getNote(noteGuid, true, true, true, false)
        //if(note == null) return null

        var targetResource = note?.resources?.filter{it.guid == resourceGuid}?.first()
        note.resources.remove(targetResource)

        if(note.resources.isNotEmpty()) {

            note.content = note.content.removeSuffix(EvernoteUtil.NOTE_SUFFIX)
            note.resources.forEach {
                note.content += "<en-media type=\"" + it.mime + "\" hash=\"" +
                        EvernoteUtil.bytesToHex(it.getData().getBodyHash()) + "\"/>"
            }
            note.content += EvernoteUtil.NOTE_SUFFIX

            noteStoreClient?.updateNote(note)
        } else {
            noteStoreClient?.deleteNote(note.guid)
        }
        return note
    }

}