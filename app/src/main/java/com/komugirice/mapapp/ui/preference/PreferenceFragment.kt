package com.komugirice.mapapp.ui.preference


import android.app.AlertDialog
import android.app.Application
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.evernote.client.android.EvernoteSession
import com.evernote.client.android.type.NoteRef
import com.evernote.edam.type.Note
import com.evernote.edam.type.User
import com.google.android.gms.maps.model.LatLng
import com.komugirice.mapapp.MyApplication
import com.komugirice.mapapp.MyApplication.Companion.isEvernoteLoggedIn
import com.komugirice.mapapp.Prefs
import com.komugirice.mapapp.R
import com.komugirice.mapapp.base.BaseFragment
import com.komugirice.mapapp.data.AllImage
import com.komugirice.mapapp.data.EvImageData
import com.komugirice.mapapp.data.ImageData
import com.komugirice.mapapp.enums.Mode
import com.komugirice.mapapp.extension.extractPostalCode
import com.komugirice.mapapp.extension.makeTempFile
import com.komugirice.mapapp.extension.makeTempFileToStorage
import com.komugirice.mapapp.task.FindNotesTask
import com.komugirice.mapapp.task.GetUserTask
import com.komugirice.mapapp.ui.map.EvernoteHelper
import com.komugirice.mapapp.ui.map.MapFragment
import com.komugirice.mapapp.ui.map.MapFragmentHelper
import com.komugirice.mapapp.ui.notebook.NotebookNameActivity
import com.komugirice.mapapp.util.AppUtil
import kotlinx.android.synthetic.main.activity_header.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_preference.*
import kotlinx.coroutines.*
import net.vrallev.android.task.TaskResult
import java.io.File
import java.io.IOException


/**
 * @author komugirice
 */
class PreferenceFragment : BaseFragment() {

    private lateinit var preferenceViewModel: PreferenceViewModel

    private val handler = Handler()

    private val EvernoteHelper = EvernoteHelper()

    private val exceptionHandler: CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            EvernoteHelper.handleEvernoteApiException(
                context!!,
                throwable
            )
        }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        preferenceViewModel =
            ViewModelProviders.of(this).get(PreferenceViewModel::class.java).apply {
                // モード
                mode.observe(viewLifecycleOwner, Observer {
                    modeValue.setSelection(it.id)
                })
                // Evernote連携
                evernoteName.observe(viewLifecycleOwner, Observer {
                    evernoteValue.text = it
                })
                // ノートブック
                notebookName.observe(viewLifecycleOwner, Observer {
                    notebookValue.text = it
                })
            }
        val root = inflater.inflate(R.layout.fragment_preference, container, false)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        titleTextView.text = "設定"
        initSpinner()
        initClick()
    }

    override fun onResume() {
        super.onResume()

        // ナビゲーションからギャラリーを非活性
        activity?.nav_view?.getMenu()?.findItem(R.id.nav_gallery)?.setEnabled(false)

        // evernote連携再設定
        isEvernoteLoggedIn = EvernoteSession.getInstance().isLoggedIn
        if (isEvernoteLoggedIn) {
            handler.postDelayed({
                GetUserTask().start(this, "init")
            }, 100L)

        }

        preferenceViewModel.initData()
    }

    private fun initSpinner() {
        var adapter: ArrayAdapter<String>
        var modeList = Mode.values().map { it.modeName }
        context?.apply {
            adapter = ArrayAdapter<String>(
                this, R.layout.spinner_item, modeList
            )
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)

            modeValue.apply {

                this.adapter = adapter
                this.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    // 選択変更時
                    override fun onItemSelected(
                        adapterView: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        val selected: Mode = Mode.values().get(position)
                        preferenceViewModel.mode.value = selected
                        MyApplication.mode = selected   // グローバルデータ更新
                        Prefs().mode.put(position)
                    }

                    override fun onNothingSelected(adapterView: AdapterView<*>?) { // Nop
                    }
                }
            }
        }
    }

    private fun initClick() {
        backImageView.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        evernoteValue.setOnClickListener {
            //            if(!isEvernoteLoggedIn)
            EvernoteSession.getInstance().authenticate(this.requireActivity())
        }
        notebookValue.setOnClickListener {
            NotebookNameActivity.start(this.activity)
        }

        // アプリ内キャッシュ → Evernote 完全に同期
        syncPerfectCacheToEv.setOnClickListener {
            if (!EvernoteHelper.isExistEvNotebook(context)) return@setOnClickListener
            MyApplication.evNotebook?.apply {
                // 確認ボタン
                AlertDialog.Builder(context)
                    .setMessage(
                        getString(
                            R.string.confirm_sync,
                            getString(R.string.sync_cache_to_ev_label),
                            getString(R.string.sync_perfect),
                            this.name
                        )
                    )
                    .setPositiveButton(R.string.ok, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            showProgressDialog(requireContext())
                            // ノート検索タスク実行
                            FindNotesTask(0, 250, MyApplication.evNotebook, null, null).start(
                                this@PreferenceFragment,
                                "onSyncPerfectCacheToEv"
                            )
                        }
                    })
                    .setNegativeButton(R.string.cancel, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                        }
                    }).show()
            }
        }

        // アプリ内キャッシュ → Evernote 差分を同期
        syncDiffCacheToEv.setOnClickListener {
            if (!EvernoteHelper.isExistEvNotebook(context)) return@setOnClickListener
            MyApplication.evNotebook?.apply {
                // 確認ボタン
                AlertDialog.Builder(context)
                    .setMessage(
                        getString(
                            R.string.confirm_sync,
                            getString(R.string.sync_cache_to_ev_label),
                            getString(R.string.sync_difference),
                            this.name
                        )
                    )
                    .setPositiveButton(R.string.ok, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            showProgressDialog(requireContext())
                            // ノート検索タスク実行
                            FindNotesTask(0, 250, MyApplication.evNotebook, null, null).start(
                                this@PreferenceFragment,
                                "onSyncDiffCacheToEv"
                            )
                        }
                    })
                    .setNegativeButton(R.string.cancel, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                        }
                    }).show()

            }
        }
        // Evernote → アプリ内キャッシュ 完全に同期
        syncPerfectEvToCache.setOnClickListener {
            if (!EvernoteHelper.isExistEvNotebook(context)) return@setOnClickListener
            MyApplication.evNotebook?.apply {
                // 確認ボタン
                AlertDialog.Builder(context)
                    .setMessage(
                        getString(
                            R.string.confirm_sync,
                            getString(R.string.sync_ev_to_cache_label),
                            getString(R.string.sync_perfect),
                            this.name
                        )
                    )
                    .setPositiveButton(R.string.ok, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            showProgressDialog(requireContext())
                            // アプリ内キャッシュ消去
                            val images = mutableListOf<ImageData>()
                            images.addAll(Prefs().allImage.get().blockingSingle().allImage)
                            images.forEach {
                                File(it.filePath).delete()
                            }
                            Prefs().allImage.remove()

                            // ノート検索タスク実行
                            FindNotesTask(0, 250, MyApplication.evNotebook, null, null).start(
                                this@PreferenceFragment,
                                "onSyncDiffEvToCache"
                            )
                        }
                    })
                    .setNegativeButton(R.string.cancel, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                        }
                    }).show()
            }
        }
        // Evernote → アプリ内キャッシュ 差分を同期
        syncDiffEvToCache.setOnClickListener {
            if (!EvernoteHelper.isExistEvNotebook(context)) return@setOnClickListener
            MyApplication.evNotebook?.apply {
                // 確認ボタン
                AlertDialog.Builder(context)
                    .setMessage(
                        getString(
                            R.string.confirm_sync,
                            getString(R.string.sync_ev_to_cache_label),
                            getString(R.string.sync_difference),
                            this.name
                        )
                    )
                    .setPositiveButton(R.string.ok, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            showProgressDialog(requireContext())
                            // ノート検索タスク実行
                            FindNotesTask(0, 250, MyApplication.evNotebook, null, null).start(
                                this@PreferenceFragment,
                                "onSyncDiffEvToCache"
                            )
                        }
                    })
                    .setNegativeButton(R.string.cancel, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                        }
                    }).show()
            }
        }
    }

    @TaskResult(id = "init")
    fun onGetUser(user: User) {
        MyApplication.evernoteUser = user
        if (user != null) {
            preferenceViewModel.evernoteName.value = user.username
        }
    }

    @TaskResult(id = "onLoginFinished")
    fun onLoginFinishedGetUser(user: User) {
        MyApplication.evernoteUser = user
        if (user != null) {
            preferenceViewModel.evernoteName.value = user.username
            // ノートブック削除
            Prefs().notebookName.remove()
            preferenceViewModel.notebookName.value = "なし"
        }
    }

    /**
     * アプリ内キャッシュ → Evernote 完全に同期
     */
    @TaskResult(id = "onSyncPerfectCacheToEv")
    fun syncPerfectCacheToEv(noteRefList: List<NoteRef>?) {

        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            async {
                noteRefList?.forEach {
                    // ノート全削除
                    MyApplication.noteStoreClient?.deleteNote(it.guid)
                }
            }.await()
            withContext(Dispatchers.Main) {
                // アプリ内キャッシュ → Evernoteノート同期
                syncCacheToEv(null)
            }
        }
    }

    /**
     * アプリ内キャッシュ → Evernote 差分を同期
     */
    @TaskResult(id = "onSyncDiffCacheToEv")
    fun syncDiffCacheToEv(noteRefList: List<NoteRef>?) {
        var gottenEvNotebook = MapFragment.Companion.EvNotebook()

        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            async {
                noteRefList?.forEach {
                    // ノート取得
                    val note = it.loadNote(true, true, false, false)
                    gottenEvNotebook.notes.add(note)
                }
            }.await()
            withContext(Dispatchers.Main) {
                // アプリ内キャッシュ → Evernoteノート同期
                syncCacheToEv(gottenEvNotebook)
            }
        }
    }

    /**
     * アプリ内キャッシュ → Evernoteノート同期
     * 完全同期の場合・・・noteBookはnullが入る。
     * 差分同期の場合・・・noteBookは値が設定される。
     */
    private fun syncCacheToEv(noteBook: MapFragment.Companion.EvNotebook?) {
        var updateNotes = mutableListOf<Note>()
        var newNotes = mutableListOf<Note>()

        // アプリ内キャッシュの画像を取得
        val images = mutableListOf<ImageData>()
        images.addAll(Prefs().allImage.get().blockingSingle().allImage)

        // アプリ内キャッシュ → Evernoteリソースへ変換
        val resources = mutableListOf<MapFragment.Companion.EvResource>()
        images.forEach {
            var imageFile = it.filePath.toUri().makeTempFile()
            imageFile?.apply {
                val resource = EvernoteHelper.createEvResource(
                    imageFile,
                    LatLng(it.lat, it.lon),
                    it.address
                )
                resources.add(resource)
            }
        }

        // ノートに追加する処理
        resources.forEach {
            val lat = it.resource.attributes.latitude
            val lon = it.resource.attributes.longitude
            // resourceからノートタイトルを設定
            val noteTitle = AppUtil.getPostalCodeAndHalfAddress(context, LatLng(lat, lon))
            // 郵便番号チェック
            val targetNote =
                noteBook?.notes?.filter { it.title.extractPostalCode() == noteTitle.extractPostalCode() }
                    ?.firstOrNull()
            // ノートありの場合
            targetNote?.apply {

                // lat, lonが完全に一致している場合は対象外
                val isDuplicate =
                    targetNote.resources.find { it.attributes.latitude == lat && it.attributes.longitude == lon }
                if (isDuplicate == null) {
                    // ノートにリソースを追加
                    targetNote.addToResources(it.resource)
                    // updateNotesにまだ未追加の場合は追加する
                    if (!updateNotes.contains(targetNote))
                        updateNotes.add(targetNote)
                }

            } ?: run {
                // ノートなし
                val tmpNote =
                    newNotes.filter { it.title.extractPostalCode() == noteTitle.extractPostalCode() }
                        .firstOrNull()
                if (tmpNote == null) {
                    // 新規ノート初作成
                    val newNote = EvernoteHelper.createNote(
                        MyApplication.evNotebook?.guid,
                        noteTitle,
                        it.resource
                    )
                    newNotes.add(newNote)
                } else {
                    // 既に新規ノート作成済み
                    EvernoteHelper.addResource(tmpNote, it.resource)
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {

            // ノート登録
            newNotes.forEach {
                MyApplication.noteStoreClient?.createNote(it)
            }
            updateNotes.forEach {
                MyApplication.noteStoreClient?.updateNote(it)
            }

            dismissProgressDialog()
            Toast.makeText(context, "同期が完了しました。", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Evernote → アプリ内キャッシュ 差分を同期
     */
    @TaskResult(id = "onSyncDiffEvToCache")
    fun syncDiffEvToCache(noteRefList: List<NoteRef>?) {
        var gottenEvNotebook = MapFragment.Companion.EvNotebook()

        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            async {
                noteRefList?.forEach {
                    // ノート取得
                    val note = it.loadNote(true, true, false, false)
                    gottenEvNotebook.notes.add(note)
                }
            }.await()
            withContext(Dispatchers.Main) {
                // Evernote → アプリ内キャッシュ 同期
                syncEvToCache(gottenEvNotebook)
            }
        }
    }

    /**
     * Evernote → アプリ内キャッシュ 同期
     *
     */
    private fun syncEvToCache(noteBook: MapFragment.Companion.EvNotebook) {

        // 登録済みのアプリ内キャッシュの画像を取得
        val images = mutableListOf<ImageData>()
        images.addAll(Prefs().allImage.get().blockingSingle().allImage)

        // Evernoteリソース→imageDataリストへ詰め込む
        val tmp = mutableListOf<ImageData>()
        noteBook.notes.forEach {
            it.resources.forEach { resource ->

                // 同じ座標の場合は登録しない
                if (images.any {
                        it.lat == resource.attributes.latitude
                                && it.lon == resource.attributes.longitude
                    })
                    return@forEach

                // アプリ内キャッシュ用の画像ファイル作成
                val newFile: File? = try {
                    AppUtil.createImageFileToStorage()
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    null
                }
                newFile?.apply {

                    try {
                        // 画像ファイルにevernote取得データをコピー
                        writeBytes(resource.data.body)
                    } catch (e: Exception) {
                        // resource.data.bodyがnullでexceptionの時がある
                        null
                    }
                    // EvImageData
                    val imageData = ImageData().apply {
                        lat = resource.attributes.latitude
                        lon = resource.attributes.longitude
                        filePath = "file://${newFile.path}"
                        this.address = AppUtil.getPostalCodeAndAllAddress(context, LatLng(lat, lon))
                    }
                    tmp.add(imageData)
                }
            }
        }
        // アプリ内キャッシュに登録
        images.addAll(tmp)
        Prefs().allImage.put(AllImage().apply { allImage = images })

        dismissProgressDialog()
        Toast.makeText(context, "同期が完了しました。", Toast.LENGTH_LONG).show()
    }
}
