package com.komugirice.mapapp.ui.map

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.evernote.client.android.EvernoteSession
import com.evernote.client.android.EvernoteUtil
import com.evernote.client.android.type.NoteRef
import com.evernote.edam.notestore.NoteFilter
import com.evernote.edam.notestore.NotesMetadataList
import com.evernote.edam.notestore.NotesMetadataResultSpec
import com.evernote.edam.type.Note
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.Gson
import com.komugirice.mapapp.*
import com.komugirice.mapapp.MyApplication.Companion.applicationContext
import com.komugirice.mapapp.MyApplication.Companion.evNotebook
import com.komugirice.mapapp.MyApplication.Companion.mode
import com.komugirice.mapapp.enums.Mode
import com.komugirice.mapapp.extension.extractPostalCodeAndAddress
import com.komugirice.mapapp.extension.makeTempFile
import com.komugirice.mapapp.task.CreateNewNoteTask
import com.komugirice.mapapp.task.FindNotesTask
import kotlinx.android.synthetic.main.fragment_map.view.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import net.vrallev.android.task.TaskResult
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author komugirice
 */
class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var images = mutableListOf<ImageData>()
    // 位置
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // 写真
    private lateinit var currentPhotoPath: String
    private lateinit var currentPhotoUri: Uri

    private val noteStoreClient = EvernoteSession.getInstance().evernoteClientFactory.noteStoreClient
    private lateinit var tmpAdress: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val root = inflater.inflate(R.layout.fragment_map,container, false)

        root.photoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/jpeg")
            startActivityForResult(intent, REQUEST_CODE_CHOOSE_IMAGE)
        }

        root.cameraButton.setOnClickListener {
            dispatchTakePictureIntent()
        }
        return root

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
//        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
//        mapFragment.getMapAsync(this)
//        fusedLocationClient = LocationServices.getFusedLocationProviderClient()


        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment?
        mapFragment?.apply{
            getMapAsync(this@MapFragment)
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context!!)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != AppCompatActivity.RESULT_OK || data == null)
            return
        // 写真選択
        if (requestCode == REQUEST_CODE_CHOOSE_IMAGE)
            data.data?.also {
                val file = it.makeTempFile()
                if (file != null) {
                    val latLng = mMap.cameraPosition.target
                    val address = Geocoder(context, Locale.JAPAN)
                        .getFromLocation(latLng.latitude, latLng.longitude, 1)
                        .get(0)
                        .getAddressLine(0)
                        .extractPostalCodeAndAddress()
                    val imageData = ImageData().apply {
                        lat = latLng.latitude
                        lon = latLng.longitude
                        filePath = "file://${file.path}"
                        this.address = address
                    }


                    if (mode == Mode.CACHE) {
                        images.add(imageData)
                        Prefs().allImage.put(AllImage().apply { allImage = images })
                    } else if (mode == Mode.EVERNOTE) {
                        evNotebook?.apply {
                            tmpAdress = address
                            // ノート検索タスク実行
                            FindNotesTask(0,250, evNotebook, null, null).start(this@MapFragment)
                        } ?: run {
                            // ノートブック存在エラー
                            Toast.makeText(context, "設定画面でノートブックを設定して下さい", Toast.LENGTH_LONG).show()
                        }
                    }
                    // TODO progress
                    //start(context)
                    //refresh()
                }
            }
        // カメラ
        if(requestCode == REQUEST_CODE_CAMERA)
            currentPhotoUri.also {
                val file = it.makeTempFile()
                if (file != null) {
                    val latLng = mMap.cameraPosition.target
                    val address = Geocoder(context, Locale.JAPAN)
                        .getFromLocation(latLng.latitude, latLng.longitude, 1)
                        .get(0)
                        .getAddressLine(0)
                        .extractPostalCodeAndAddress()
                    val imageData = ImageData().apply {
                        lat = latLng.latitude
                        lon = latLng.longitude
                        filePath = "file://${file.path}"
                        this.address = address
                    }
                    if (mode == Mode.CACHE) {
                        images.add(imageData)
                        Prefs().allImage.put(AllImage().apply { allImage = images })
                    } else if (mode == Mode.EVERNOTE) {

                    }
                    //start(context)
                    refresh()
                }
                //Toast.makeText(context, currentPhotoUri.toString(), Toast.LENGTH_LONG).show()
            }

    }

    /**
     * evernoteノート作成
     */
    private fun createNote(title: String) {
        var getNote: Note?
        // IOスレッド
        CoroutineScope(IO).launch {
        //val guid = getNoteGuid(title)

            try {
            } catch (e: Exception) {
                // ノートが見つからないので新規作成
                // ノートを作成
                val note = Note()
                note.title = title
                note.content = EvernoteUtil.NOTE_PREFIX
                note.content += EvernoteUtil.NOTE_SUFFIX;
                evNotebook?.guid.apply {
                    note.notebookGuid = this
                }
                noteStoreClient.createNote(note)
                return@launch
            }
            // ノートが見つかったので更新

            //noteStoreClient.updateNote(getNote)


        }
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
        initData()
        fusedLocationClient.lastLocation.addOnSuccessListener { location : Location? ->
            // Got last known location. In some rare situations this can be null.
            CURRENT_LAT = location?.latitude ?: TOKYO_LAT
            CURRENT_LON = location?.longitude ?: TOKYO_LON
            initGoogleMap()
//        val sydney = LatLng(-34.0, 151.0)
//        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
//        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
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
            moveCamera(CameraUpdateFactory.newLatLngZoom(if (image == null) LatLng(CURRENT_LAT, CURRENT_LON) else LatLng(image.lat, image.lon), 12F))
            var userLocation = LatLng(CURRENT_LAT, CURRENT_LON)
            mMap.addMarker(
                MarkerOptions()
                    .position(userLocation)
                    .title("現在地")
            )
//             mMap.setOnInfoWindowClickListener(object: GoogleMap.OnInfoWindowClickListener {
//                 override fun onInfoWindowClick(p0: Marker?) {
//                        p0?.showInfoWindow()
//                 }
//            })
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

        }

    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            activity?.apply{
                // Ensure that there's a camera activity to handle the intent
                takePictureIntent.resolveActivity(packageManager)?.also {
                    // Create the File where the photo should go
                    val photoFile: File? = try {
                        createImageFile()
                    } catch (ex: IOException) {
                        // Error occurred while creating the File
                        null
                    }
                    // Continue only if the File was successfully created
                    photoFile?.also {
                        val photoURI: Uri = FileProvider.getUriForFile(
                            context!!,
                            packageName + ".provider",
                            it
                        )
                        currentPhotoUri = photoURI
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                        startActivityForResult(takePictureIntent, REQUEST_CODE_CAMERA)
                    }
                }
            }
        }
    }


    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    /**
     * Evernoteのノート新規作成or更新
     * ※FindNotesTask後にcallback
     */
    @TaskResult
    fun onFindNotesAndCreateOrUpdate(noteRefList: List<NoteRef>?) {

        // 住所と同じタイトルのノート
        var targetRef: NoteRef? = noteRefList?.filter{it.title == tmpAdress}?.firstOrNull()

        targetRef?.apply {
            // ノートあり　更新
            CoroutineScope(IO).launch {
                Log.d("onFindNotes", Gson().toJson(this@apply))
                val note = this@apply.loadNote(true, true, false, false)
                note.content

                noteStoreClient.updateNote(note)
            }
        } ?: run() {
            // ノートなし　新規登録
            val note = Note()
            note.title = tmpAdress
            note.content = EvernoteUtil.NOTE_PREFIX
            note.content += EvernoteUtil.NOTE_SUFFIX;
            evNotebook?.guid.apply {
                note.notebookGuid = this
            }
            CoroutineScope(IO).launch {
                noteStoreClient.createNote(note)
            }

        }
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
    }
}
