package com.komugirice.mapapp.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.os.Environment
import com.evernote.client.android.EvernoteSession
import com.evernote.client.android.EvernoteUtil
import com.evernote.client.android.type.NoteRef
import com.evernote.client.conn.mobile.FileData
import com.evernote.edam.type.Note
import com.evernote.edam.type.Resource
import com.evernote.edam.type.ResourceAttributes
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.komugirice.mapapp.ImageData
import com.komugirice.mapapp.MyApplication
import com.komugirice.mapapp.MyApplication.Companion.noteStoreClient
import com.komugirice.mapapp.extension.extractPostalCodeAndAllAddress
import com.komugirice.mapapp.extension.extractPostalCodeAndHalfAddress
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
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
    fun createEvNote(imageFile: File, latLng: LatLng, title: String): MapFragment.Companion.EvNote {
        // Hash the data in the image file. The hash is used to reference the file in the ENML note content.
        var `in` = BufferedInputStream(FileInputStream(imageFile.getPath()))
        val data = FileData(EvernoteUtil.hash(`in`), File(imageFile.getPath()))

        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(File(imageFile.path).absolutePath, opts)

        val attributes = ResourceAttributes()
        attributes.fileName = imageFile.name
        attributes.longitude = latLng.longitude
        attributes.latitude = latLng.latitude

        val evNote = MapFragment.Companion.EvNote().apply {
            // クラス変数evNote設定
            this.title = title

            this.resource.apply{
                this.data = data
                //height = opts.outHeight.toShort()
                //width = opts.outWidth.toShort()
                mime = "image/jpg"
                this.attributes = attributes
            }

        }
        return evNote
    }

    /**
     * Evernoteノート更新
     */
    fun updateNote(note: Note, evNote: MapFragment.Companion.EvNote){
        note.addToResources(evNote.resource)

        note.content = note.content.removeSuffix(EvernoteUtil.NOTE_SUFFIX) +
                "<en-media type=\"" + evNote.resource.mime + "\" hash=\"" +
                EvernoteUtil.bytesToHex(evNote.resource.getData().getBodyHash()) + "\"/>" +
                EvernoteUtil.NOTE_SUFFIX;

        noteStoreClient?.updateNote(note)
    }

    /**
     * 画像ファイル作成
     */
    @Throws(IOException::class)
    fun createImageFile(): File {
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
     * Evernoteノート新規登録
     */
    fun registNote(notebookGuid: String?, evNote: MapFragment.Companion.EvNote){
        val note = Note()
        note.title = evNote.title
        note.content =
            EvernoteUtil.NOTE_PREFIX +
                    "<en-media type=\"" + evNote.resource.mime + "\" hash=\"" +
                    EvernoteUtil.bytesToHex(evNote.resource.getData().getBodyHash()) + "\"/>" +
                    EvernoteUtil.NOTE_SUFFIX;
        note.addToResources(evNote.resource)
        notebookGuid?.apply {
            note.notebookGuid = this
        }
        noteStoreClient?.createNote(note)
    }

    /**
     * Evernoteデータからのマーカー作成
     */
    fun createMarkerFromEvernote(resource: Resource, address: String, mMap: GoogleMap) {
        // 画像ファイル作成
        val newFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            // Error occurred while creating the File
            null
        }
        newFile?.apply {
            // 画像ファイルにevernote取得データをコピー
            writeBytes(resource.data.body)
            var marker = mMap.addMarker(
                MarkerOptions().position(
                    LatLng(
                        resource.attributes.latitude,
                        resource.attributes.longitude
                    )
                ).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )
            // ImageData
            val imageData = ImageData().apply {
                lat = resource.attributes.latitude
                lon = resource.attributes.longitude
                filePath = "file://${newFile.path}"
                this.address = address
            }
            marker.tag = imageData
        }
    }

}