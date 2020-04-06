package com.komugirice.mapapp.ui.map

import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
            isPosChangeMode.value = false

            var target = posChangeMarker?.tag as ImageData
            var tap = tapMarker?.tag as ImageData

            if(target == null || tap == null) return@setOnClickListener

            val tapLatLng = LatLng(tap.lat, tap.lon)

            target.apply{
                lat = tap.lat
                lon = tap.lon
                address = tap.address
            }

            // posChangeMarkerはimageMarkersの値をシャローコピーしているか？
            posChangeMarker?.apply{
                tag = target
                this.position = tapLatLng
                showInfoWindow()
//                imageMarkers.remove(this)
//                imageMarkers.add(this)
            }


            // 保存
            if (mode == Mode.CACHE) {

                images.filter{it.id == target.id}.firstOrNull()?.apply{
                    images.remove(this)
                    images.add(target)
                }
                Prefs().allImage.put(AllImage().apply { allImage = images })
            } else {
                evNotebook?.apply {
                    val evImage = posChangeMarker?.tag as EvImageData
                    // 変更前ノート
                    currentEvNotebook.notes.filter{it.guid == evImage.noteGuid}.firstOrNull()?.also {

                        it?.resources?.filter{it.guid == evImage.guid}?.firstOrNull()?.apply {
                            this.attributes.latitude = evImage.lat
                            this.attributes.longitude = evImage.lon

                            currentEvResource.clear()
                            currentEvResource.resource = this
                            currentEvResource.filePath = evImage.filePath
                            currentEvResource.title = evImage.address

                            // 変更前ノートのリソース削除でノート登録
                            it?.resources?.remove(this)
                            CoroutineScope(IO).launch {
                                async {
                                    try {
                                        helper.updateNoteEvResource(it, null)
                                    } catch (e: EDAMUserException) {
                                        withContext(Main) {
                                            Timber.e(e)
                                            if (e.errorCode == EDAMErrorCode.QUOTA_REACHED)
                                                Toast.makeText(
                                                    context,
                                                    "Evernoteアカウントのアップロード容量上限に達しました",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                        }
                                        cancel()
                                    }
                                }.await()


                            }
                        }
                    }
                    // 変更後ノート

                            // 変更後ノート
                            CoroutineScope(IO).launch {
                                async {
                                    try {
                                        // 郵便番号が同じノートが存在する場合は更新
                                        currentEvNotebook.notes.filter{it.title.extractPostalCode() == evImage.address.extractPostalCode()}.firstOrNull()?.apply {
                                            helper.updateNoteEvResource(this, currentEvResource.resource)
                                        } ?: run {
                                            // 存在しない場合は新規作成
                                            helper.createNote(evNotebook?.guid, currentEvResource)
                                        }
                                    } catch (e: EDAMUserException) {
                                        withContext(Main) {
                                            Timber.e(e)
                                            if (e.errorCode == EDAMErrorCode.QUOTA_REACHED)
                                                Toast.makeText(
                                                    context,
                                                    "Evernoteアカウントのアップロード容量上限に達しました",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                        }
                                        cancel()
                                    }
                                }.await()
                        }

                } ?: run {
                    // ノートブック存在エラー
                    Toast.makeText(context, "設定画面でノートブックを設定して下さい", Toast.LENGTH_LONG)
                        .show()
                }
            }


        }
        // 位置変更Cancel
        posCancelButton.setOnClickListener {
            isPosChangeMode.value = false
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
                                            CoroutineScope(Dispatchers.IO).launch {
                                                helper.deleteEvResouce(tag.noteGuid, tag.guid)
                                            }
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

        CoroutineScope(IO).launch {

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
                CoroutineScope(IO).launch {
                    async {
                        val note = this@apply.loadNote(true, true, false, false)
                        currentEvResource.resource.noteGuid = note.guid

                        try {
                            helper.updateNoteEvResource(note, currentEvResource.resource)
                            currentEvNotebook.notes.filter { it.guid == note.guid }.first().resources.add(
                                currentEvResource.resource
                            )
                        }catch(e: EDAMUserException) {
                            withContext(Main) {
                                Timber.e(e)
                                if(e.errorCode == EDAMErrorCode.QUOTA_REACHED)
                                    Toast.makeText(
                                        context,
                                        "Evernoteアカウントのアップロード容量上限に達しました",
                                        Toast.LENGTH_LONG
                                    ).show()
                            }
                            cancel()
                        }
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
                val note = helper.createNote(MyApplication.evNotebook?.guid, currentEvResource)

                // resource.body = nullでエラー。使えず。EDAMUserException(errorCode:ENML_VALIDATION, parameter:The processing instruction target matching "[xX][mM][lL]" is not allowed.
                //CreateNewNoteTask(note.title, note.content, null, evNotebook, null).start(this@MapFragment, "onCreateNewNote")

                // 上記の代替処理
                CoroutineScope(IO).launch {
                    async {
                        try {
                            val note = MyApplication.noteStoreClient?.createNote(note)
                        }catch(e: EDAMUserException) {
                            withContext(Main) {
                                Timber.e(e)
                                if(e.errorCode == EDAMErrorCode.QUOTA_REACHED)
                                    Toast.makeText(
                                        context,
                                        "Evernoteアカウントのアップロード容量上限に達しました",
                                        Toast.LENGTH_LONG
                                    ).show()
                            }
                            cancel()
                        }
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

        CoroutineScope(IO).launch {

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
