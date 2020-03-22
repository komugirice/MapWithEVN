package com.komugirice.mapapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.evernote.client.android.EvernoteSession
import com.komugirice.mapapp.MyApplication.Companion.isLoggedInEvernote
import com.komugirice.mapapp.task.GetUserTask

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // evernoteLoginチェック
        isLoggedInEvernote = EvernoteSession.getInstance().isLoggedIn
        if(isLoggedInEvernote){
            GetUserTask().start(this)
        }

        MainActivity.start(this)
    }

    companion object {
        fun start(activity: AppCompatActivity) = activity.apply {
            finishAffinity()
            startActivity(Intent(activity, SplashActivity::class.java))
        }
    }
}
