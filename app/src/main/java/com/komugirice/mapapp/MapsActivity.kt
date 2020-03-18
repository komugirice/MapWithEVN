package com.komugirice.mapapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.activity_maps.*
import java.util.jar.Manifest

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var images = mutableListOf<ImageData>()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        initLocationListener()

        photoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/jpeg")
            startActivityForResult(intent, REQUEST_CODE_CHOOSE_IMAGE)
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
        if (resultCode != Activity.RESULT_OK || data == null)
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
            moveCamera(CameraUpdateFactory.newLatLngZoom(if (image == null) LatLng(CURRENT_LAT, CURRENT_LON) else LatLng(image.lat, image.lon), 12F))
        }
    }

    private fun initData() {
        images.addAll(Prefs().allImage.get().blockingSingle().allImage)
        images.forEach {
            var marker = mMap.addMarker(MarkerOptions().position(LatLng(it.lat, it.lon)))
            marker.tag = it
        }
    }

    companion object {
        private const val REQUEST_CODE_CHOOSE_IMAGE = 1000
        private var CURRENT_LAT: Double = 0.0
        private var CURRENT_LON: Double = 0.0
        private const val TOKYO_LAT = 35.681382
        private const val TOKYO_LON = 139.76608399999998
        private const val OSAKA_LAT = 34.7024
        private const val OSAKA_LON = 135.4959
        fun start(activity: Activity) = activity.apply {
            finishAffinity()
            startActivity(Intent(activity, MapsActivity::class.java))
        }
    }
}
