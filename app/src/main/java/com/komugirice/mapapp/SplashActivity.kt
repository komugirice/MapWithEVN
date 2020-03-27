package com.komugirice.mapapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.evernote.client.android.EvernoteSession
import com.evernote.edam.type.Notebook
import com.evernote.edam.type.User
import com.google.gson.Gson
import com.komugirice.mapapp.MyApplication.Companion.isEvernoteLoggedIn
import com.komugirice.mapapp.MyApplication.Companion.mode
import com.komugirice.mapapp.enums.Mode
import com.komugirice.mapapp.task.FindNotebooksTask
import com.komugirice.mapapp.task.GetUserTask
import kotlinx.android.synthetic.main.activity_main.*
import net.vrallev.android.task.TaskResult

/**
 * @author komugirice
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // evernoteLoginチェック
        isEvernoteLoggedIn = EvernoteSession.getInstance().isLoggedIn
        mode = Mode.getValue(Prefs().mode.get().blockingSingle())

        MainActivity.start(this)
    }

    companion object {
        fun start(activity: AppCompatActivity) = activity.apply {
            finishAffinity()
            startActivity(Intent(activity, SplashActivity::class.java))
        }
    }
}
