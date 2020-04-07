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
import com.google.android.material.snackbar.Snackbar
import com.komugirice.mapapp.*
import com.komugirice.mapapp.MyApplication.Companion.evNotebook
import com.komugirice.mapapp.MyApplication.Companion.mode
import com.komugirice.mapapp.MyApplication.Companion.noteStoreClient
import com.komugirice.mapapp.databinding.ImageViewDialogBinding
import com.komugirice.mapapp.enums.Mode
import com.komugirice.mapapp.extension.extractPostalCode
import com.komugirice.mapapp.extension.makeTempFile
import com.komugirice.mapapp.extension.makeTempFileToStorage
import com.komugirice.mapapp.task.CreateNewNoteTask
import com.komugirice.mapapp.task.FindNotesTask
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_map.*
import kotlinx.android.synthetic.main.fragment_map.view.*
import kotlinx.android.synthetic.main.fragment_preference.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import net.vrallev.android.task.TaskResult
import timber.log.Timber
import java.io.File
import java.io.IOException

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
 * onActivityResult
 * focusPosition
 * onMapReady
 * createTapMarker
 * initGoogleMap
 * initData
 * dispatchTakePictureIntent
 * onInitFindNotes
 * onFindNotesAndCreateOrUpdate
 * onCreatedFindNote
 * onCreateNewNote
 * refresh
 *
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
    // マーカー保持用
    private var imageMarkers = mutableListOf<Marker>()
    
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
            = CoroutineExceptionHandler { _, throwable -> evernoteApiException(throwable)}

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

        // マーカーを変更先情報に更新(imageMarkersも同時に更新される)
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
            evNotebook?.apply {
                val evImage = posChangeMarker?.tag as EvImageData
                // 変更前ノート
                currentEvNotebook.notes.filter{it.guid == evImage.noteGuid}.firstOrNull()?.also {

                    it?.resources?.filter{it.guid == evImage.guid}?.firstOrNull()?.apply {
                        this.attributes.latitude = evImage.lat
                        this.attributes.longitude = evImage.lon

                        // 変更前データに位置情報を更新したものを保持
                        currentEvResource.clear()
                        currentEvResource.resource = this
                        currentEvResource.filePath = evImage.filePath
                        currentEvResource.title = evImage.address

                        // 変更前ノートのリソース削除でノート登録
                        CoroutineScope(IO).launch(exceptionHandler) {
                            async {
                                // 削除
                                val retNote = helper.deleteEvResouce(it, evImage.guid)
                                retNote?.apply {
                                    // リソース残る＆残らない
                                    currentEvNotebook.notes.remove(it)
                                    // リソース残る
                                    if(retNote.resources.isNotEmpty())
                                        currentEvNotebook.notes.add(retNote)
                                }
                            }.await()
                        }
                    }
                }
                // 変更後ノート

                // 変更後ノート
                CoroutineScope(IO).launch(exceptionHandler) {
                    var isCreate = false
                    async {
                        // 郵便番号が同じノートが存在する場合は更新
                        currentEvNotebook.notes.filter{it.title.extractPostalCode() == evImage.address.extractPostalCode()}.firstOrNull()?.apply {
                            helper.updateNoteEvResource(this, currentEvResource.resource)
                        } ?: run {
                            // 存在しない場合は新規作成
                            isCreate = true
                            helper.createNote(evNotebook?.guid, currentEvResource)
                        }
                    }.await()
                    // マーカー作成
                    withContext(Main) {
                        if(isCreate) {
                            // ノート新規登録の場合は一度マーカー削除
                            posChangeMarker?.apply {
                                imageMarkers.remove(this)
                            }
                            // のちonCreateFindNoteでマーカー再設定（currentNotebookも)
                            // 再度ノート検索タスク実行
                            FindNotesTask(0, 250, evNotebook, null, null).start(
                                this@MapFragment,
                                "onCreatedFindNote"
                            )
                        } else {
                            // 更新の場合
                            posChangeMarker?.showInfoWindow()
                        }
                    }
                }

            } ?: run {
                // ノートブック存在エラー
                Toast.makeText(context, "設定画面でノートブックを設定して下さい", Toast.LENGTH_LONG)
                    .show()
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
            val address = helper.getPostalCodeAndHalfAddress(context, latLng)

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

                    images.add(imageData)
                    Prefs().allImage.put(AllImage().apply { allImage = images })

                    val imageMarker = helper.createMarker(imageData, mMap)
                    imageMarker.showInfoWindow()
                    imageMarkers.add(imageMarker)
                }
            } else if (mode == Mode.EVERNOTE) {
                // Evernote
                var imageFile = it.makeTempFile()
                if (imageFile != null) {
                    evNotebook?.apply {
                        // ノート情報設定
                        currentEvResource =
                            helper.createEvResource(imageFile, latLng, address)
                        // ノート検索タスク実行
                        FindNotesTask(
                            0,
                            250,
                            evNotebook,
                            null,
                            null
                        ).start(this@MapFragment, "onFindNotesAndCreateOrUpdate")
                    } ?: run {
                        // ノートブック存在エラー
                        Toast.makeText(context, "設定画面でノートブックを設定して下さい", Toast.LENGTH_LONG)
                            .show()
                    }
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

    private fun createTapMarker(latLng: LatLng) {
        tapLocation = LatLng(latLng.latitude, latLng.longitude).also {
            val locationStr = helper.getPostalCodeAndAllAddress(context, latLng)
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
                    MaterialDialog(this).apply {
                        listItems(
                            items = listOf(
                                context.getString(R.string.menu_position_change),
                                context.getString(R.string.menu_delete)
                            ),
                            selection = { dialog, index, text ->
                                when (index) {
                                    // 位置変更
                                    0 -> {
                                        posChangeMarker = it
                                        Log.d("posChangeMarker", posChangeMarker.hashCode().toString())
                                        Log.d("posChangeMarker", it.hashCode().toString())
                                        Log.d("posChangeMarker equals", it.equals(posChangeMarker).toString())
                                        isPosChangeMode.value = true
                                    }
                                    // 削除
                                    1 -> {
                                        if (mode == Mode.CACHE) {
                                            // キャッシュ画像削除
                                            val imageData = it.tag as ImageData
                                            helper.deleteCacheImage(imageData, images)
                                        } else {
                                            deleteEvImage(it)
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
                // showInfoWindow
                var imageData = it.tag as ImageData
                imageData?.apply{
                    it.title = imageData.address
                    it.showInfoWindow()
                }
                true
            }

        }
    }

    private fun deleteEvImage(marker: Marker) {
        // Evernote画像削除
        val tag = marker.tag as EvImageData
        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            val targetNote = currentEvNotebook.notes.filter{it.guid == tag.noteGuid}.first()
            val retNote = helper.deleteEvResouce(targetNote, tag.guid)
            retNote?.apply {
                currentEvNotebook.notes.also{
                    it.filter{it.guid == this.guid}.first().apply {
                        // リソース残る＆残らない
                        it.remove(this)
                    }
                    // リソース残る
                    if(retNote.resources.isNotEmpty())
                        it.add(retNote)
                }
            }

        }
        currentEvResource.clear()
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
     * Evernoteのノート新規作成or更新
     * ※FindNotesTask後にcallback
     */
    @TaskResult(id = "onFindNotesAndCreateOrUpdate")
    fun onFindNotesAndCreateOrUpdate(noteRefList: List<NoteRef>?) {


            // タイトルが同じ郵便番号のノート
            var targetRef: NoteRef? =
                noteRefList?.filter { it.title.extractPostalCode() == currentEvResource.title.extractPostalCode() }
                    ?.firstOrNull()

            targetRef?.apply {

                // ノートあり　更新
                CoroutineScope(IO).launch(exceptionHandler) {
                    async {
                        val note = this@apply.loadNote(true, true, false, false)
                        currentEvResource.resource.noteGuid = note.guid

                        helper.updateNoteEvResource(note, currentEvResource.resource)
                        currentEvNotebook.notes.filter { it.guid == note.guid }.first().resources.add(
                            currentEvResource.resource
                        )
                    }.await()

                    withContext(Main) {
                        // マーカー作成
                        val imageMarker = helper.createMarker(currentEvResource, mMap)
                        imageMarker.showInfoWindow()
                        imageMarkers.add(imageMarker)
                    }
                }


            } ?: run() {


                // ノートなし　新規登録

                // resource.body = nullでエラー。使えず。EDAMUserException(errorCode:ENML_VALIDATION, parameter:The processing instruction target matching "[xX][mM][lL]" is not allowed.
                //CreateNewNoteTask(note.title, note.content, null, evNotebook, null).start(this@MapFragment, "onCreateNewNote")

                // 上記の代替処理
                CoroutineScope(IO).launch(exceptionHandler) {
                    async {
                        helper.createNote(MyApplication.evNotebook?.guid, currentEvResource)
                    }.await()

                    withContext(Main) {
                        // 再度ノート検索タスク実行
                        FindNotesTask(0, 250, evNotebook, null, null).start(
                            this@MapFragment,
                            "onCreatedFindNote"
                        )
                    }
                }
            }

    }

    /**
     * ノート新規作成後にコールバックされる
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

    // エラーハンドリングを作成
    private fun evernoteApiException(throwable: Throwable) {
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

    companion object {
        private const val REQUEST_CODE_CHOOSE_IMAGE = 1000
        private const val REQUEST_CODE_CAMERA = 1001
        private var CURRENT_LAT: Double = 0.0
        private var CURRENT_LON: Double = 0.0
        private const val TOKYO_LAT = 35.681382
        private const val TOKYO_LON = 139.76608399999998
        private const val OSAKA_LAT = 34.7024
        private const val OSAKA_LON = 135.4959
        fun start(context: Context?) = context?.apply {
            startActivity(Intent(context, MapFragment::class.java))
        }

        /**
         * Evernoteノート情報関連クラス
         */
        class EvResource {
            var title: String = ""
            var filePath: String = ""
            var resource: Resource = Resource()
            fun clear(){ title = ""; resource = Resource() }
        }

        class EvNotebook {
            var guid = evNotebook?.guid ?: ""
            var notes: MutableList<Note> = mutableListOf()
        }
    }
}
