package com.komugirice.mapapp

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
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.evernote.edam.type.User
import com.komugirice.mapapp.MyApplication.Companion.evernoteUser
import com.komugirice.mapapp.MyApplication.Companion.isEvernoteLoggedIn
import com.komugirice.mapapp.task.GetUserTask
import kotlinx.android.synthetic.main.activity_main.*
import net.vrallev.android.task.TaskResult

class MainActivity : AppCompatActivity() {

    // drawer
    private lateinit var appBarConfiguration: AppBarConfiguration
    // 位置
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // navigation
        val navController = findNavController(R.id.nav_host_fragment)
        nav_view.setupWithNavController(navController)
        appBarConfiguration = AppBarConfiguration(navController.graph, drawer_layout)
        // 位置ダイアログ
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        initLocationListener()

        if (isEvernoteLoggedIn) {
            if (savedInstanceState == null) {
                GetUserTask().start(this)
            } else evernoteUser?.let { onGetUser(it) }
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
            if (ContextCompat.checkSelfPermission(
                    this,
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

    @TaskResult
    fun onGetUser(user: User) {
        evernoteUser = user
        if (user != null) {
            nav_view.menu.findItem(R.id.nav_evernote_value).title = user.username
        }
    }

    companion object {
        fun start(activity: AppCompatActivity) = activity.apply {
            finishAffinity()
            startActivity(Intent(activity, MainActivity::class.java))
        }
    }
}
