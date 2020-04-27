package com.komugirice.mapapp.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import com.evernote.client.android.EvernoteUtil
import com.evernote.client.conn.mobile.FileData
import com.evernote.edam.error.EDAMErrorCode
import com.evernote.edam.error.EDAMUserException
import com.evernote.edam.type.Note
import com.evernote.edam.type.Resource
import com.evernote.edam.type.ResourceAttributes
import com.google.android.gms.maps.model.LatLng
import com.komugirice.mapapp.MyApplication
import com.komugirice.mapapp.MyApplication.Companion.applicationContext
import com.komugirice.mapapp.MyApplication.Companion.noteStoreClient
import com.komugirice.mapapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.*


open class EvernoteHelper {

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
                // 注意!! noteGuidは設定できない
            }

        }
        return evResource
    }

    /**
     * Evernoteノート更新
     * ※同じ画像はresoucesに追加されない仕様らしい
     */
    fun updateNoteEvResource(note: Note, resource: Resource?){

        addResource(note, resource)


        noteStoreClient?.updateNote(note)
    }

    /**
     * EvernoteノートにResourceを追加します
     * ※同じ画像はresoucesに追加されない仕様らしい
     */
    fun addResource(note: Note, resource: Resource?){

        note.content = EvernoteUtil.NOTE_PREFIX
        note.resources?.forEach {
            note.content += EvernoteUtil.createEnMediaTag(it)
        }
        resource?.apply {
            note.addToResources(resource)
            note.content += EvernoteUtil.createEnMediaTag(resource)

        }
        note.content += EvernoteUtil.NOTE_SUFFIX
    }

    /**
     * Evernoteノート新規登録
     */
    fun registNote(notebookGuid: String?, title: String, resource: Resource){
        val note = createNote(notebookGuid, title, resource)
        noteStoreClient?.createNote(note)
    }

    /**
     * Evernoteノート作成
     */
    fun createNote(notebookGuid: String?, title: String, resource: Resource): Note {
        val note = Note()
        note.title = title
        note.content =
            EvernoteUtil.NOTE_PREFIX +
                    EvernoteUtil.createEnMediaTag(resource) +
                    EvernoteUtil.NOTE_SUFFIX;
        note.addToResources(resource)
        notebookGuid?.apply {
            note.notebookGuid = this
        }
        return note
    }


    fun deleteEvResouce(note: Note, resourceGuid: String): Note? {

        var targetResource = note?.resources?.filter{it.guid == resourceGuid}?.first()
        note.resources.remove(targetResource)

        // リソースが残っている場合
        if(note.resources.isNotEmpty()) {

            note.content = note.content.removeSuffix(EvernoteUtil.NOTE_SUFFIX)
            note.resources.forEach {
                note.content += "<en-media type=\"" + it.mime + "\" hash=\"" +
                        EvernoteUtil.bytesToHex(it.getData().getBodyHash()) + "\"/>"
            }
            note.content += EvernoteUtil.NOTE_SUFFIX

            noteStoreClient?.updateNote(note)
        } else {
            // リソースが残っていない場合
            noteStoreClient?.deleteNote(note.guid)
        }
        return note
    }

    /**
     * エラーハンドリングを作成
     */
    fun handleEvernoteApiException(context: Context, throwable: Throwable) {
        Log.e("CoroutineException", "例外キャッチ $throwable")
        CoroutineScope(Dispatchers.Main).launch {
            var errorMsg = ""
            var message = throwable.message ?: ""

            if (throwable is EDAMUserException) {
                if (throwable.errorCode == EDAMErrorCode.QUOTA_REACHED) {
                    errorMsg = applicationContext.getString(R.string.exception_evernote_upload_amount)
                }
            }
            if (errorMsg.isEmpty())
                errorMsg = "${applicationContext.getString(R.string.exception_evernote_api)}\n${throwable}"


            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Evernoteノートブックの存在チェック
     */
    fun isExistEvNotebook(context: Context?): Boolean {
        if (MyApplication.evNotebook == null) {
            // ノートブック存在エラー
            Toast.makeText(context, applicationContext.getString(R.string.no_evernote_notebook), Toast.LENGTH_LONG)
                .show()
            return false
        }
        return true
    }

}