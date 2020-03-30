package com.komugirice.mapapp.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Geocoder
import com.evernote.client.android.EvernoteSession
import com.evernote.client.android.EvernoteUtil
import com.evernote.client.android.type.NoteRef
import com.evernote.client.conn.mobile.FileData
import com.evernote.edam.type.Note
import com.evernote.edam.type.ResourceAttributes
import com.google.android.gms.maps.model.LatLng
import com.komugirice.mapapp.ImageData
import com.komugirice.mapapp.MyApplication
import com.komugirice.mapapp.extension.extractPostalCodeAndAddress
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.*


object MapFragmentHelper {

    private val noteStoreClient = EvernoteSession.getInstance().evernoteClientFactory.noteStoreClient

    /**
     * 住所取得
     */
    fun getAddress(context: Context?, latLng: LatLng): String {
        return Geocoder(context, Locale.JAPAN)
            .getFromLocation(latLng.latitude, latLng.longitude, 1)
            .get(0)
            .getAddressLine(0)
            .extractPostalCodeAndAddress()
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

        noteStoreClient.updateNote(note)
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
        noteStoreClient.createNote(note)
    }

}