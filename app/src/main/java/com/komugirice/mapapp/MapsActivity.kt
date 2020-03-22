package com.komugirice.mapapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.forEach
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.evernote.edam.type.User
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.navigation.NavigationView
import com.komugirice.mapapp.MyApplication.Companion.isLoggedInEvernote
import com.komugirice.mapapp.task.GetUserTask
import com.komugirice.mapapp.ui.preference.PreferenceActivity
import kotlinx.android.synthetic.main.activity_maps.*
import net.vrallev.android.task.TaskResult
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var images = mutableListOf<ImageData>()
    // 位置
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    // 写真
    private lateinit var currentPhotoPath: String
    private lateinit var currentPhotoUri: Uri
    // drawer
    private lateinit var appBarConfiguration: AppBarConfiguration
    // evernote
    private var mUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        initLocationListener()

        // navigation
//        val navController = findNavController(R.id.nav_host_fragment)
//        nav_view.setupWithNavController(navController)
//        appBarConfiguration = AppBarConfiguration(navController.graph, drawer_layout)

        nav_view.setNavigationItemSelectedListener(object: NavigationView.OnNavigationItemSelectedListener{
            override fun onNavigationItemSelected(p0: MenuItem): Boolean {
                if(p0.itemId == R.id.nav_preference) {
                    PreferenceActivity.start(this@MapsActivity)
                }
                return true
            }
        })

        photoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/jpeg")
            startActivityForResult(intent, REQUEST_CODE_CHOOSE_IMAGE)
        }

        cameraButton.setOnClickListener {
            dispatchTakePictureIntent()
        }

        if (isLoggedInEvernote) {
            if (savedInstanceState == null) {
                GetUserTask().start(this)
            } else mUser?.let { onGetUser(it) }
        }
    }

    /**
     * 現在位置情報の許可ダイアログの準備
     *
     */
    private fun initLocationListener() {

        locationListener = object: LocationListener {
            override fun onLocationChanged(location: Location?) {
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            }

            override fun onProviderEnabled(provider: String?) {
            }

            override fun onProviderDisabled(provider: String?) {
            }
        }


        if (Build.VERSION.SDK_INT < 23) {
                try {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, locationListener)
                } catch(e: SecurityException){
                }
            } else {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                this.requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
            } else {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, locationListener)
            }
        }
    }

    /**
     * 現在位置情報の許可ダイアログの結果
     *
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == 1) {
            if (grantResults.size > 0 && grantResults.get(0) == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, locationListener)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != AppCompatActivity.RESULT_OK || data == null)
            return
        if (requestCode == REQUEST_CODE_CHOOSE_IMAGE)
            data.data?.also {
                val file = it.makeTempFile()
                if (file != null) {
                    val latLng = mMap.cameraPosition.target
                    val imageData = ImageData().apply {
                        lat = latLng.latitude
                        lon = latLng.longitude
                        filePath = "file://${file.path}"
                    }
                    images.add(imageData)
                    Prefs().allImage.put(AllImage().apply { allImage = images })
                    start(this)
                }
            }

        if(requestCode == REQUEST_CODE_CAMERA)
            currentPhotoUri.also {
                val file = it.makeTempFile()
                if (file != null) {
                    val latLng = mMap.cameraPosition.target
                    val imageData = ImageData().apply {
                        lat = latLng.latitude
                        lon = latLng.longitude
                        filePath = "file://${file.path}"
                    }
                    images.add(imageData)
                    Prefs().allImage.put(AllImage().apply { allImage = images })
                    start(this)
                }
            }
            Toast.makeText(this, currentPhotoUri.toString(), Toast.LENGTH_LONG).show()
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
            setInfoWindowAdapter(SpotInfoWindowAdapter(this@MapsActivity, images.map { it.id }))
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
        images.addAll(Prefs().allImage.get().blockingSingle().allImage)
        images.forEach {
            var marker = mMap.addMarker(
                MarkerOptions().position(LatLng(it.lat, it.lon))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )
            marker.tag = it
        }
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
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
                        this,
                        this.packageName + ".provider",
                        it
                    )
                    currentPhotoUri = photoURI
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_CODE_CAMERA)
                }
            }
        }
    }


    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    @TaskResult
    fun onGetUser(user: User) {
        mUser = user
        if (user != null) {
            nav_view.menu.getItem(R.id.nav_evernote_value).title = user.username
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
        fun start(activity: AppCompatActivity) = activity.apply {
            finishAffinity()
            startActivity(Intent(activity, MapsActivity::class.java))
        }
    }
}
