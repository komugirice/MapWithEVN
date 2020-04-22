package com.komugirice.mapapp.ui.preference


import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
import com.komugirice.mapapp.data.ImageData
import com.komugirice.mapapp.enums.Mode
import com.komugirice.mapapp.extension.extractPostalCode
import com.komugirice.mapapp.extension.makeTempFile
import com.komugirice.mapapp.extension.makeTempFileToStorage
import com.komugirice.mapapp.task.FindNotesTask
import com.komugirice.mapapp.task.GetUserTask
import com.komugirice.mapapp.ui.map.EvernoteHelper
import com.komugirice.mapapp.ui.map.MapFragment
import com.komugirice.mapapp.ui.notebook.NotebookNameActivity
import com.komugirice.mapapp.util.AppUtil
import kotlinx.android.synthetic.main.activity_header.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_preference.*
import kotlinx.coroutines.*
import net.vrallev.android.task.TaskResult
import java.io.File


/**
 * @author komugirice
 */
class PreferenceFragment: Fragment() {

    private lateinit var preferenceViewModel: PreferenceViewModel

    private val handler = Handler()

    private val EvernoteHelper = EvernoteHelper()

    private val exceptionHandler: CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable -> EvernoteHelper.handleEvernoteApiException(context!!, throwable) }


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
        if(isEvernoteLoggedIn){
            handler.postDelayed({
                GetUserTask().start(this, "init")
            }, 100L)

        }

        preferenceViewModel.initData()
    }

    private fun initSpinner(){
        var adapter: ArrayAdapter<String>
        var modeList = Mode.values().map {it.modeName}
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

    private fun initClick(){
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
            syncPerfectCacheToEv()
        }
        // アプリ内キャッシュ → Evernote 差分を同期
        syncDiffCacheToEv.setOnClickListener {
            // 全てのノートを取得
            MyApplication.evNotebook?.apply {
                // ノート検索タスク実行
                FindNotesTask(0, 250, MyApplication.evNotebook, null, null).start(
                    this@PreferenceFragment,
                    "onSyncDiffCacheToEv"
                )
            }
        }
        // Evernote → アプリ内キャッシュ 完全に同期
        syncPerfectEvToCache.setOnClickListener {

        }
        // Evernote → アプリ内キャッシュ 差分を同期
        syncDiffEvToCache.setOnClickListener {

        }
    }

    fun getModeName(mode: Mode): String {
        if(mode.isCache) return getString(R.string.mode_cache)
        if(mode.isEvernote) return getString(R.string.mode_evernote)
        return ""
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
    private fun syncPerfectCacheToEv() {

        // ノートブックを全削除

        // キャッシュの画像を取得
        val images = mutableListOf<ImageData>()
        images.addAll(Prefs().allImage.get().blockingSingle().allImage)

        // Evernoteリソース作成
        val resources = mutableListOf<MapFragment.Companion.EvResource>()
        images.forEach {
            val resource = EvernoteHelper.createEvResource(File(it.filePath), LatLng(it.lat, it.lon), it.address)
            resources.add(resource)
        }

        // ノート新規作成

        // ノート登録
    }

    /**
     * アプリ内キャッシュ → Evernote 差分を同期
     */
    @TaskResult(id = "onSyncDiffCacheToEv")
    fun syncDiffCacheToEv(noteRefList: List<NoteRef>?) {
        var gottenEvNotebook = MapFragment.Companion.EvNotebook()
        var updateNotes = mutableListOf<Note>()
        var newNotes = mutableListOf<Note>()

        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            async {
                noteRefList?.forEach {
                    // ノート取得
                    val note = it.loadNote(true, true, false, false)

                    gottenEvNotebook.notes.add(note)

                }
            }.await()
            withContext(Dispatchers.Main) {
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
                        gottenEvNotebook.notes.filter { it.title.extractPostalCode() == noteTitle.extractPostalCode() }.firstOrNull()
                    // ノートありの場合
                    targetNote?.apply {

                        // TODO 画像が同じ郵便番号の場合の分岐

                        // lat, lonが完全に一致している場合は対象外
                        val isDuplicate =
                            targetNote.resources.find { it.attributes.latitude == lat && it.attributes.longitude == lon }
                        if (isDuplicate == null) {
                            // ノートにリソースを追加
                            targetNote.addToResources(it.resource)
                            // updateNotesにまだ未追加の場合は追加する
                            if(!updateNotes.contains(targetNote))
                                updateNotes.add(targetNote)
                        }

                    } ?: run {
                        // ノートなし
                        val tmpNote = newNotes.filter{ it.title.extractPostalCode() == noteTitle.extractPostalCode() }.firstOrNull()
                        if(tmpNote == null){
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
            }

            // ノート登録
            newNotes.forEach {
                MyApplication.noteStoreClient?.createNote(it)
            }
            updateNotes.forEach {
                MyApplication.noteStoreClient?.updateNote(it)
            }
        }
    }
}
