package com.komugirice.mapapp.ui.preference


import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.komugirice.mapapp.MapsActivity
import com.komugirice.mapapp.R
import kotlinx.android.synthetic.main.activity_preference.*

class PreferenceActivity: AppCompatActivity() {

    private lateinit var preferenceViewModel: PreferenceViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preference)
        preferenceViewModel =
            ViewModelProviders.of(this).get(PreferenceViewModel::class.java).apply {

                mode.observe(this@PreferenceActivity, Observer {
                    modeValue.text = it
                })
                evernoteName.observe(this@PreferenceActivity, Observer {
                    evernoteValue.text = it
                })
            }
    }

    companion object {
        fun start(activity: AppCompatActivity) = activity.apply {
            finishAffinity()
            startActivity(Intent(activity, PreferenceActivity::class.java))
        }
    }
}
