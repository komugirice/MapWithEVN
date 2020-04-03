package com.komugirice.mapapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.evernote.client.android.EvernoteSession
import com.evernote.edam.type.Notebook
import com.evernote.edam.type.User
import com.komugirice.mapapp.MyApplication.Companion.evNotebook
import com.komugirice.mapapp.MyApplication.Companion.evernoteUser
import com.komugirice.mapapp.MyApplication.Companion.isEvernoteLoggedIn
import com.komugirice.mapapp.MyApplication.Companion.noteStoreClient
import com.komugirice.mapapp.task.FindNotebooksTask
import com.komugirice.mapapp.task.GetUserTask
import kotlinx.android.synthetic.main.activity_main.*
import net.vrallev.android.task.TaskResult

/**
 * @author komugirice
 */
class MainActivity : AppCompatActivity() {

    // drawer
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // navigation
        val navController = findNavController(R.id.nav_host_fragment)
        nav_view.setupWithNavController(navController)
        appBarConfiguration = AppBarConfiguration(navController.graph, drawer_layout)
        // 現在位置情報の許可
        requestPermissons()

        if (isEvernoteLoggedIn) {
            noteStoreClient = EvernoteSession.getInstance().evernoteClientFactory.noteStoreClient
            if (savedInstanceState == null) {
                GetUserTask().start(this)
                FindNotebooksTask().start(this, "personal");
            } else {
                evernoteUser?.let { onGetUser(it) }
            }
        }
    }

    /**
     * 現在位置情報の許可ダイアログの準備
     *
     */
    private fun requestPermissons() {


        if (Build.VERSION.SDK_INT < 23) {
            try {
//                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, locationListener)
            } catch(e: SecurityException){
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                this.requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
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
                if (checkPermission(this)) {

                }
            }
        }
    }

    @TaskResult
    fun onGetUser(user: User) {
        evernoteUser = user
//        if (user != null) {
//            nav_view.menu.findItem(R.id.nav_evernote_value).title = user.username
//        }
    }

    @TaskResult(id = "personal")
    fun onFindNotebooks(notebooks: List<Notebook?>?) {
        if (notebooks == null || notebooks.isEmpty()) {
        } else {
            val targetName =  Prefs().notebookName.get().blockingSingle()
            if(targetName.isNotEmpty())
                notebooks?.forEach {
                    if(it != null && it?.name == targetName) {
                        evNotebook = it
                        return
                    }
                }
        }
    }

    companion object {
        fun start(activity: AppCompatActivity) = activity.apply {
            //finishAffinity()
            startActivity(Intent(activity, MainActivity::class.java))
        }

        fun checkPermission(context: Context): Boolean {
            return (ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED)
                    &&
                (ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED)
        }
    }
}
