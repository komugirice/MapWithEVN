package com.komugirice.mapapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.evernote.client.android.EvernoteSession
import com.komugirice.mapapp.MyApplication.Companion.isEvernoteLoggedIn
import com.komugirice.mapapp.MyApplication.Companion.mode
import com.komugirice.mapapp.enums.Mode
import com.komugirice.mapapp.util.AppUtil

/**
 * @author komugirice
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // キャッシュ削除
        AppUtil.deleteCacheDir()

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
