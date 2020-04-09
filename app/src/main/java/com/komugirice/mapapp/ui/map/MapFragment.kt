package com.komugirice.mapapp.ui.map

import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.evernote.client.android.type.NoteRef
import com.evernote.edam.error.EDAMErrorCode
import com.evernote.edam.error.EDAMUserException
import com.evernote.edam.type.Note
import com.evernote.edam.type.Resource
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.Gson
import com.komugirice.mapapp.*
import com.komugirice.mapapp.MyApplication.Companion.evNotebook
import com.komugirice.mapapp.MyApplication.Companion.mode
import com.komugirice.mapapp.databinding.ImageViewDialogBinding
import com.komugirice.mapapp.enums.Mode
import com.komugirice.mapapp.extension.extractPostalCode
import com.komugirice.mapapp.extension.extractPostalCodeAndHalfAddress
import com.komugirice.mapapp.extension.makeTempFile
import com.komugirice.mapapp.extension.makeTempFileToStorage
import com.komugirice.mapapp.task.FindNotesTask
import com.komugirice.mapapp.util.AppUtil
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_map.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import net.vrallev.android.task.TaskResult
import java.io.File

/**
 * MapFragment
 * @author komugirice
 *
 * メソッド
 * onCreateView
 * onActivityCreated
 * onResume
 * initClick
 * changePosition
 * createOrUpdateEvnoteWrap
 * onActivityResult
 * focusPosition
 * onMapReady
 * createTapMarker
 * initGoogleMap
 * deleteEvResourceWrap
 * initData
 * dispatchTakePictureIntent
 * onInitFindNotes
 * onCreatedFindNote
 * onCreateNewNote
 * refresh
 * handleEvernoteApiException
 * isExistEvNotebook
 *
 */
class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var viewModel: MapFragmentViewModel

    private val helper = MapFragmentHelper

    private lateinit var mMap: GoogleMap

    // アプリ内保存
    private var images = mutableListOf<ImageData>()
    // Evernote保存
    private var currentEvNotebook = EvNotebook()
    private var currentEvResource = EvResource()

    
    // 位置
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var myLocate: LatLng
    private var tapLocation: LatLng? = null
    private var tapMarker: Marker? = null
    
    // 写真
    private lateinit var currentPhotoPath: String
    private lateinit var currentPhotoUri: Uri

    private var isPosChangeMode = MutableLiveData<Boolean>(false)
    private var posChangeMarker: Marker? = null

    private val exceptionHandler: CoroutineExceptionHandler
            = CoroutineExceptionHandler { _, throwable -> handleEvernoteApiException(throwable)}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val root = inflater.inflate(R.layout.fragment_map,container, false)

        isPosChangeMode.observe(this, Observer {
            if(it) {
                isPosChangeModeGroup.visibility = View.VISIBLE
                buttonGroup.visibility = View.GONE
            } else {
                isPosChangeModeGroup.visibility = View.GONE
                buttonGroup.visibility = View.VISIBLE
            }
        })

        return root

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment?
        mapFragment?.apply{
            getMapAsync(this@MapFragment)
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context!!)
        }
        initClick()
    }

    override fun onResume() {
        super.onResume()
//        if(::mMap.isInitialized)
//            initData()
    }

    private fun initClick() {

        // 写真ボタンクリック時
        photoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/jpeg")
            startActivityForResult(intent, REQUEST_CODE_CHOOSE_IMAGE)
        }

        // カメラボタンクリック時
        cameraButton.setOnClickListener {
            dispatchTakePictureIntent()
        }

        // 位置変更OK
        posOkButton.setOnClickListener {
            changePosition()
        }
        // 位置変更Cancel
        posCancelButton.setOnClickListener {
            isPosChangeMode.value = false
        }

    }

    private fun changePosition() {
        isPosChangeMode.value = false

        var targetTag = posChangeMarker?.tag as ImageData
        var tap = tapMarker?.tag as ImageData

        if(targetTag == null || tap == null) return

        val tapLatLng = LatLng(tap.lat, tap.lon)

        targetTag.apply{
            lat = tap.lat
            lon = tap.lon
            address = tap.address
        }

        // マーカーを位置変更先情報に更新(imageMarkersも同時に更新される)
        posChangeMarker?.apply {
            tag = targetTag
            this.position = tapLatLng
        }

        // 保存
        if (mode == Mode.CACHE) {

            images.filter{it.id == targetTag.id}.firstOrNull()?.apply{
                images.remove(this)
                images.add(targetTag)
            }
            Prefs().allImage.put(AllImage().apply { allImage = images })
            posChangeMarker?.showInfoWindow()
        } else {
            if(!isExistEvNotebook()) return
            // マーカーの画像データ
            val evImage = posChangeMarker?.tag as EvImageData
            // クラス変数から位置変更前にいたノート抽出
            currentEvNotebook.getNote(evImage.noteGuid)?.also {

                // 変更前にいたノートの対象リソースの位置情報を更新
                it?.resources?.filter{it.guid == evImage.guid}?.firstOrNull()?.apply {
                    this.attributes.latitude = evImage.lat
                    this.attributes.longitude = evImage.lon

                    // 変更前の対象リソースに位置情報を更新したものを保持
                    currentEvResource.clear()
                    currentEvResource.resource = this
                    currentEvResource.filePath = evImage.filePath
                    currentEvResource.title = evImage.address

                    // 変更前にいたノートからリソースを削除
                    deleteEvResouceWrap(it, evImage.guid)

                    // 一度（削除したリソースの）マーカー削除
                    posChangeMarker?.apply {
                        imageMarkers.remove(this)
                        // 自分を削除しない場合残ってしまった
                        this.remove()
                    }

                }
            }

            // 位置変更先のノートを新規作成or更新
            createOrUpdateEvnoteWrap(currentEvResource)

        }
    }

    /**
     * ノートを新規作成or更新
     * [補足]
     * 新規の場合
     * 　currentNotebooks: onCreatedFindNoteで更新
     * 　マーカー: onCreatedFindNoteで作成
     * 　imageMarkers: onCreatedFindNoteで更新
     * 更新の場合
     * 　currentNotebooks: 自メソッド内で更新
     * 　マーカー: 自メソッド内で作成
     * 　imageMarkers: 自メソッド内で更新
     */
    private fun createOrUpdateEvnoteWrap(evResource: EvResource) {
        CoroutineScope(IO).launch(exceptionHandler) {
            var isCreate = false
            async {
                // 郵便番号が同じノートが存在する場合は更新
                currentEvNotebook.notes.filter { it.title.extractPostalCode() == evResource.title.extractPostalCode() }.firstOrNull()?.apply {
                    evResource.resource.noteGuid = this.guid // 注意!!
                    helper.updateNoteEvResource(this, evResource.resource)
                } ?: run {
                    // 存在しない場合は新規作成
                    val title = evResource.title.extractPostalCodeAndHalfAddress()
                    helper.createNote(evNotebook?.guid, title, evResource.resource)
                    isCreate = true
                }
            }.await()
            // マーカー作成
            withContext(Main) {
                if (isCreate) {
                    // 再度ノート検索タスク実行
                    // のちonCreateFindNoteでマーカー再設定（currentNotebookも)
                    FindNotesTask(0, 250, evNotebook, null, null).start(
                        this@MapFragment,
                        "onCreatedFindNote"
                    )
                } else {
                    // マーカー作成
                    val imageMarker = helper.createMarker(evResource, mMap)
                    imageMarker.showInfoWindow()
                    imageMarkers.add(imageMarker)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != AppCompatActivity.RESULT_OK || data == null)
            return

        var uri: Uri? = null

        // 写真選択
        if (requestCode == REQUEST_CODE_CHOOSE_IMAGE)
            uri = data.data

        // カメラ
        if(requestCode == REQUEST_CODE_CAMERA)
            uri = currentPhotoUri


        uri?.also {

            // 位置情報設定
            var latLng: LatLng = focusPosition()
            val address = AppUtil.getPostalCodeAndHalfAddress(context, latLng)

            if (mode == Mode.CACHE) {
                // キャッシュ
                var imageFile = it.makeTempFileToStorage()
                if (imageFile != null) {
                    val imageData = ImageData().apply {
                        lat = latLng.latitude
                        lon = latLng.longitude
                        filePath = "file://${imageFile.path}"
                        this.address = address
                    }
                    // SharedPreference更新
                    images.add(imageData)
                    Prefs().allImage.put(AllImage().apply { allImage = images })

                    // マーカー作成
                    val imageMarker = helper.createMarker(imageData, mMap)
                    imageMarker.showInfoWindow()
                    imageMarkers.add(imageMarker)
                }
            } else if (mode == Mode.EVERNOTE) {
                // Evernote
                var imageFile = it.makeTempFile()
                if (imageFile != null) {
                    if(!isExistEvNotebook()) return

                    // 設定情報からリソース作成
                    currentEvResource = helper.createEvResource(imageFile, latLng, address)

                    // ノートの新規登録or更新
                    createOrUpdateEvnoteWrap(currentEvResource)
                }
            }
            // TODO progress
            //start(context)
        }

    }

    private fun focusPosition(): LatLng {
        var latLng = tapLocation ?: LatLng(myLocate.latitude, myLocate.longitude) // タップした位置もしくは現在値
        return latLng
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomGesturesEnabled = true
        mMap.uiSettings.isMapToolbarEnabled = false

        // 現在地設定
        context?.apply{
            // 現在位置パーミッションチェック
            if(MainActivity.checkPermission(this)){
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    //myLocate = MainActivity.locationManager.getLastKnownLocation("gps")
                    myLocate = LatLng(location?.latitude ?: TOKYO_LAT, location?.longitude ?: TOKYO_LON)
                    createTapMarker(LatLng(myLocate.latitude, myLocate.longitude))
                    initGoogleMap()
                }
            } else {
                myLocate = LatLng(TOKYO_LAT, TOKYO_LON)
                initGoogleMap()
            }
        }
        initData()

        // タップした時のリスナーをセット
        mMap.setOnMapClickListener {
            createTapMarker(it)
        }
    }

    /**
     * 位置情報用のマーカー作成
     */
    private fun createTapMarker(latLng: LatLng) {
        tapLocation = LatLng(latLng.latitude, latLng.longitude).also {
            val locationStr = AppUtil.getPostalCodeAndAllAddress(context, latLng)
            tapMarker?.remove()
            tapMarker = mMap.addMarker(
                MarkerOptions()
                    .position(it)
                    .title(locationStr)
                    .snippet("この位置に登録します"))
            tapMarker?.apply {
                tag = ImageData().apply{
                    this.address = locationStr
                    this.lat = latLng.latitude
                    this.lon = latLng.longitude
                }
                showInfoWindow()
            }

        }
    }



    private fun initGoogleMap() {
        mMap.apply {
            setInfoWindowAdapter(SpotInfoWindowAdapter(activity, images.map { it.id }))
//            val latLngBoundsBuilder = LatLngBounds.Builder()
//            latLngBoundsBuilder.include(LatLng(TOKYO_LAT, TOKYO_LON))
//            latLngBoundsBuilder.include(LatLng(OSAKA_LAT, OSAKA_LON))
//                images.forEach {
//                    latLngBoundsBuilder.include(LatLng(it.lat, it.lon))
//                }
//            moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBoundsBuilder.build(), 150))
            val image = images.lastOrNull()
            // 現在位置設定
            moveCamera(CameraUpdateFactory.newLatLngZoom(if (image == null) LatLng(myLocate?.latitude, myLocate.longitude) else LatLng(image.lat, image.lon), 15F))

            // InfoWindowクリック時
            this.setOnInfoWindowClickListener {
                var imageData = it.tag as ImageData
                // 位置マーカーの場合（filePathなし）
                if(imageData.filePath.isEmpty()) {
                    it.hideInfoWindow()
                    return@setOnInfoWindowClickListener
                }

                // 画像プレビュー表示
                context?.apply {
                    val imageView = ImageView(this)
                    Picasso.get().load(imageData.filePath).into(imageView)
                    // 画像拡大ダイアログ表示
                    MaterialDialog(this).apply {
                        cancelable(true)
                        val dialogBinding = ImageViewDialogBinding.inflate(
                            LayoutInflater.from(context),
                            null,
                            false
                        )
                        dialogBinding.imageView.setOnClickListener {
                            this.cancel()
                        }

                        dialogBinding.imageView.setImageDrawable(imageView.drawable)
                        setContentView(dialogBinding.root)
                    }.show()
                }
            }
            // InfoWindow長押し時
            this.setOnInfoWindowLongClickListener {
                context?.apply {
                    // ダイアログ設定
                    MaterialDialog(this).apply {
                        // ダイアログのリスト設定
                        listItems(
                            items = listOf(
                                context.getString(R.string.menu_position_change),
                                context.getString(R.string.menu_delete)
                            ),
                            // ダイアログのリストごとの処理
                            selection = { dialog, index, text ->
                                when (index) {
                                    // 位置変更
                                    0 -> {
                                        posChangeMarker = it
                                        isPosChangeMode.value = true
                                    }
                                    // 削除
                                    1 -> {
                                        if (mode == Mode.CACHE) {
                                            // キャッシュ画像削除
                                            val imageData = it.tag as ImageData
                                            helper.deleteCacheImage(imageData, images)
                                        } else {
                                            // Evernote画像削除
                                            val tag = it.tag as EvImageData
                                            val note = currentEvNotebook.getNote(tag.noteGuid)
                                            deleteEvResouceWrap(note!!, tag.guid)
                                            currentEvResource.clear()
                                        }
                                        // マーカー削除
                                        imageMarkers.remove(it)
                                        it.hideInfoWindow()
                                        it.remove()
                                    }
                                    else -> return@listItems
                                }
                            }
                        )
                    }.show()
                }
            }
            // マーカークリック時
            this.setOnMarkerClickListener {
                // showInfoWindow表示
                var imageData = it.tag as ImageData
                imageData?.apply{
                    it.title = imageData.address
                    it.showInfoWindow()
                }
                true
            }

        }
    }

    /**
     * 対象ノートから引数に設定されたリソースを削除し、クラス変数のcurrentEvNotebookを更新する
     *
     * 以下のクラス変数を更新する
     * currentEvNotebook
     *
     */
    private fun deleteEvResouceWrap(note: Note, resouceGuid: String) {
        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            async {
                val retNote = helper.deleteEvResouce(note, resouceGuid)
                // クラス変数のノート同期処理
                retNote?.apply {
                    currentEvNotebook.notes.also {
                        currentEvNotebook.getNote(this.guid).apply {
                            // 一旦削除
                            it.remove(this)
                        }
                        // リソースが残っている場合、再設定
                        if (retNote.resources.isNotEmpty())
                            it.add(retNote)
                    }
                }
            }.await()
        }
    }

    /**
     * 初期表示設定
     */
    private fun initData() {
        imageMarkers.forEach { it.remove() }
        imageMarkers.clear()
        // アプリ内キャッシュ
        if (mode == Mode.CACHE) {
            images.clear()
            images.addAll(Prefs().allImage.get().blockingSingle().allImage)
            images.forEach {
                var marker = mMap.addMarker(
                    MarkerOptions().position(LatLng(it.lat, it.lon))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                )
                marker.tag = it
                imageMarkers.add(marker)
            }
        }
        // TODO Evernote
        if (mode == Mode.EVERNOTE) {
            evNotebook?.apply {
                // ノート検索タスク実行
                FindNotesTask(0, 250, evNotebook, null, null).start(this@MapFragment, "onInitFindNotes")
            }
        }

    }

    /**
     * 写真撮影インテント
     */
    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            activity?.apply{
                // Ensure that there's a camera activity to handle the intent
                takePictureIntent.resolveActivity(packageManager)?.also {
                    // Create the File where the photo should go
                    val photoFile: File? = try {
                        helper.createImageFileToCache()

                    } catch (ex: Throwable) {
                        // Error occurred while creating the File
                        null
                    }
                    // Continue only if the File was successfully created
                    photoFile?.also {
                        currentPhotoPath = it.absolutePath
                        val photoURI: Uri = FileProvider.getUriForFile(
                            this,
                            this.packageName + ".provider",
                            it
                        )
                        currentPhotoUri = photoURI
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    }
                }
            }
            startActivityForResult(takePictureIntent, REQUEST_CODE_CAMERA)
        }
    }

    /**
     * 初期表示時に呼び出され、Evernoteから画像を取得し、マーカーを作成する
     * ※FindNotesTask後にcallback
     *
     * ※以下のクラス変数を更新する
     * currentEvNotebook
     * imageMarkers
     */
    @TaskResult(id = "onInitFindNotes")
    fun onInitFindNotes(noteRefList: List<NoteRef>?) {
        currentEvNotebook.notes.clear()

        CoroutineScope(IO).launch(exceptionHandler) {

            noteRefList?.forEach {
                // ノート取得
                val note = it.loadNote(true, true, false, false)
                val resources = note.resources

                currentEvNotebook.notes.add(note)

                withContext(Dispatchers.Main){
                    resources?.forEach {
                        // マーカー作成
                        val imageMarker = helper.createMarkerFromEvernote(it, note.title, mMap)
                        imageMarkers.add(imageMarker)

                    }
                }

            }

        }
    }

    /**
     * ノート新規作成後にコールバックされる
     *
     * ※以下のクラス変数を更新する
     * currentEvNotebook
     * imageMarkers
     *
     */
    @TaskResult(id = "onCreatedFindNote")
    fun onCreatedFindNote(noteRefList: List<NoteRef>?) {

        CoroutineScope(IO).launch(exceptionHandler) {

            // 対象のノートを抽出する
            var targetRef: NoteRef? = noteRefList?.filter{it.title.extractPostalCode() == currentEvResource.title.extractPostalCode()}?.firstOrNull()
            targetRef?.apply {
                val note = this.loadNote(true, true, false, false)
                currentEvNotebook.notes.add(note)

                withContext(Dispatchers.Main){
                    // マーカー作成
                    val resource = note.resources.first() // 必ず一つ
                    val imageMarker = helper.createMarkerFromEvernote(resource, note.title, mMap)
                    imageMarkers.add(imageMarker)
                    imageMarker.showInfoWindow()
                }
            }

        }
    }

    /**
     * 未使用
     * CreateNewNoteTask実行後、コールバック
     * resource.body = nullでエラー。使えず。
     * EDAMUserException(errorCode:ENML_VALIDATION, parameter:The processing instruction target matching "[xX][mM][lL]" is not allowed.
     *
     */
    @TaskResult(id="onCreateNewNote")
    fun onCreateNewNote(note: Note?) {
        if (note != null) {
            currentEvNotebook.notes.add(note)
            currentEvResource.resource = note.resources.first()
            // マーカー作成
            val imageMarker = helper.createMarker(currentEvResource, mMap)
            imageMarker.showInfoWindow()
            imageMarkers.add(imageMarker)
        } else {
            // エラー
        }
    }

    /**
     * フラグメントの再描画
     */
    private fun refresh() {
        fragmentManager?.apply{
            val trans = this.beginTransaction()
            trans.detach(this@MapFragment)
            trans.attach(this@MapFragment)
            trans.commit()
        }
    }

    /**
     * エラーハンドリングを作成
     */
    private fun handleEvernoteApiException(throwable: Throwable) {
        Log.e("CoroutineException", "例外キャッチ $throwable")
        CoroutineScope(Main).launch {
            var errorMsg = ""
            var message = throwable.message ?: ""

            if(throwable is EDAMUserException){
                if(throwable.errorCode == EDAMErrorCode.QUOTA_REACHED) {
                    errorMsg = "Evernoteアカウントのアップロード容量の上限に達しました"
                }
            }
            if(errorMsg.isEmpty())
                errorMsg = "API実行中に予期せぬエラーが発生しました\n${throwable}"


            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Evernoteノートブックの存在チェック
     */
    private fun isExistEvNotebook(): Boolean {
        if(evNotebook == null) {
            // ノートブック存在エラー
            Toast.makeText(context, "設定画面でノートブックを設定して下さい", Toast.LENGTH_LONG)
                .show()
            return false
        }
        return true
    }

    companion object {
        private const val REQUEST_CODE_CHOOSE_IMAGE = 1000
        private const val REQUEST_CODE_CAMERA = 1001
        private var CURRENT_LAT: Double = 0.0
        private var CURRENT_LON: Double = 0.0
        private const val TOKYO_LAT = 35.681382
        private const val TOKYO_LON = 139.76608399999998
        private const val OSAKA_LAT = 34.7024
        private const val OSAKA_LON = 135.4959
        // マーカー保持用
        var imageMarkers = mutableListOf<Marker>()

        fun start(context: Context?) = context?.apply {
            startActivity(Intent(context, MapFragment::class.java))
        }

        /**
         * Evernoteノート情報関連クラス
         */
        // ノート作成：resouce + マーカー作成：title, filePath
        class EvResource {
            var title: String = ""
            var filePath: String = ""
            var resource: Resource = Resource()
            fun clear(){ title = ""; resource = Resource() }
        }

        class EvNotebook {
            var guid = evNotebook?.guid ?: ""
            var notes: MutableList<Note> = mutableListOf()

            fun getNote(noteGuid: String): Note? { return this.notes.filter{it.guid == noteGuid}.firstOrNull() }
        }
    }
}
