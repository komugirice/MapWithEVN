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
import com.afollestad.materialdialogs.MaterialDialog
import com.evernote.client.android.type.NoteRef
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
import com.komugirice.mapapp.extension.makeTempFile
import com.komugirice.mapapp.task.FindNotesTask
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_map.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.vrallev.android.task.TaskResult
import java.io.File
import java.io.IOException

/**
 * @author komugirice
 */
class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var viewModel: MapFragmentViewModel

    private val helper = MapFragmentHelper

    private lateinit var mMap: GoogleMap
    private var images = mutableListOf<ImageData>()

    // 位置
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var myLocate: LatLng
    private var tapLocation: LatLng? = null
    private var tapMarker: Marker? = null

    // 写真
    private lateinit var currentPhotoPath: String
    private lateinit var currentPhotoUri: Uri


    private var evNote = EvNote()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val root = inflater.inflate(R.layout.fragment_map,container, false)

        // 写真ボタンクリック時
        root.photoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/jpeg")
            startActivityForResult(intent, REQUEST_CODE_CHOOSE_IMAGE)
        }

        // カメラボタンクリック時
        root.cameraButton.setOnClickListener {
            dispatchTakePictureIntent()
        }

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
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != AppCompatActivity.RESULT_OK || data == null)
            return
        // 写真選択
        if (requestCode == REQUEST_CODE_CHOOSE_IMAGE)
            data.data?.also {
                val imageFile = it.makeTempFile()
                if (imageFile != null) {

                    // 位置情報設定
                    var latLng = focusPosition()
                    val address = helper.getPostalCodeAndHalfAddress(context, latLng)

                    val imageData = ImageData().apply {
                        lat = latLng.latitude
                        lon = latLng.longitude
                        filePath = "file://${imageFile.path}"
                        this.address = address
                    }

                    if (mode == Mode.CACHE) {
                        images.add(imageData)
                        Prefs().allImage.put(AllImage().apply { allImage = images })

                    } else if (mode == Mode.EVERNOTE) {
                        evNotebook?.apply {
                            // ノート情報設定
                            evNote = helper.createEvNote(imageFile, latLng, address)
                            // ノート検索タスク実行
                            FindNotesTask(0,250, evNotebook, null, null).start(this@MapFragment, "onFindNotesAndCreateOrUpdate")
                        } ?: run {
                            // ノートブック存在エラー
                            Toast.makeText(context, "設定画面でノートブックを設定して下さい", Toast.LENGTH_LONG).show()
                        }
                    }
                    // TODO progress
                    //start(context)
                }
            }
        // カメラ
        if(requestCode == REQUEST_CODE_CAMERA)
            currentPhotoUri.also {
                val imageFile = it.makeTempFile()
                if (imageFile != null) {

                    // 位置情報設定
                    var latLng: LatLng = focusPosition()

                    val address = helper.getPostalCodeAndHalfAddress(context, latLng)

                    val imageData = ImageData().apply {
                        lat = latLng.latitude
                        lon = latLng.longitude
                        filePath = "file://${imageFile.path}"
                        this.address = address
                    }

                    if (mode == Mode.CACHE) {
                        images.add(imageData)
                        Prefs().allImage.put(AllImage().apply { allImage = images })
                    } else if (mode == Mode.EVERNOTE) {
                        evNotebook?.apply {
                            // ノート情報設定
                            evNote = helper.createEvNote(imageFile, latLng, address)
                            // ノート検索タスク実行
                            FindNotesTask(0,250, evNotebook, null, null).start(this@MapFragment, "onFindNotesAndCreateOrUpdate")
                        } ?: run {
                            // ノートブック存在エラー
                            Toast.makeText(context, "設定画面でノートブックを設定して下さい", Toast.LENGTH_LONG).show()
                        }
                    }
                    // TODO progress
                    //start(context)
                }
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
//        fusedLocationClient.lastLocation.addOnSuccessListener { location : Location? ->
            // Got last known location. In some rare situations this can be null.
//            CURRENT_LAT = location?.latitude ?: TOKYO_LAT
//            CURRENT_LON = location?.longitude ?: TOKYO_LON

//        val sydney = LatLng(-34.0, 151.0)
//        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
//        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
//        }

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

            // InfoWindowタップ時
            this.setOnInfoWindowClickListener {
                var imageData = it.tag as ImageData
                if(imageData.filePath.isEmpty()) {
                    it.hideInfoWindow()
                    return@setOnInfoWindowClickListener
                }

                // 画像プレビュー表示
                context?.apply {
                    val imageView = ImageView(this)
                    Picasso.get().load(imageData.filePath).into(imageView)

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
                Log.d("OnInfoWindowClick", "${Gson().toJson(it.tag)}")
            }
            this.setOnMarkerClickListener {
                var imageData = it.tag as ImageData
                it.title = imageData.address
                it.showInfoWindow()
                true
            }
        }
    }


    private fun initData() {
        // アプリ内キャッシュ
        if (mode == Mode.CACHE) {
            images.addAll(Prefs().allImage.get().blockingSingle().allImage)
            images.forEach {
                var marker = mMap.addMarker(
                    MarkerOptions().position(LatLng(it.lat, it.lon))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                )
                marker.tag = it
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

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            activity?.apply{
                // Ensure that there's a camera activity to handle the intent
                takePictureIntent.resolveActivity(packageManager)?.also {
                    // Create the File where the photo should go
                    val photoFile: File? = try {
                        helper.createImageFile()
                    } catch (ex: IOException) {
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
     * 初期表示時Evernoteから画像を取得し、マーカーを作成する
     * ※FindNotesTask後にcallback
     */
    @TaskResult(id = "onInitFindNotes")
    fun onInitFindNotes(noteRefList: List<NoteRef>?) {
        CoroutineScope(IO).launch {

            noteRefList?.forEach {

                val note = it.loadNote(true, true, false, false)
                val resources = note.resources

                withContext(Dispatchers.Main){
                    resources?.forEach {
                        // マーカー作成
                        helper.createMarkerFromEvernote(it, note.title, mMap)
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
        var targetRef: NoteRef? = noteRefList?.filter{it.title.extractPostalCode() == evNote.title.extractPostalCode()}?.firstOrNull()

        targetRef?.apply {
            // ノートあり　更新
            CoroutineScope(IO).launch {
                val note = this@apply.loadNote(true, true, false, false)
                helper.updateNote(note, evNote)
            }
        } ?: run() {
            // ノートなし　新規登録
            CoroutineScope(IO).launch {
                helper.registNote(MyApplication.evNotebook?.guid, evNote)
            }
        }

        // マーカー作成
        helper.createMarkerFromEvernote(evNote.resource, evNote.title, mMap)

    }

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
         * Evernoteノート情報
         */
        class EvNote {
            var title: String = ""
            var resource: Resource = Resource()
            fun clear(){ title = ""; resource = Resource() }
        }
    }
}
