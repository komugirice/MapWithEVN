package com.komugirice.mapapp.ui.preference


import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.os.postDelayed
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.evernote.client.android.EvernoteSession
import com.evernote.client.android.login.EvernoteLoginFragment
import com.evernote.edam.type.User
import com.komugirice.mapapp.MyApplication
import com.komugirice.mapapp.MyApplication.Companion.isEvernoteLoggedIn
import com.komugirice.mapapp.Prefs
import com.komugirice.mapapp.R
import com.komugirice.mapapp.enums.Mode
import com.komugirice.mapapp.interfaces.Update
import com.komugirice.mapapp.task.GetUserTask
import com.komugirice.mapapp.ui.map.MapFragment
import com.komugirice.mapapp.ui.notebook.NotebookNameActivity
import kotlinx.android.synthetic.main.activity_header.*
import kotlinx.android.synthetic.main.fragment_preference.*
import net.vrallev.android.task.TaskResult


/**
 * @author komugirice
 */
class PreferenceFragment: Fragment(),
    EvernoteLoginFragment.ResultCallback {

    private lateinit var preferenceViewModel: PreferenceViewModel

    private val handler = Handler()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        preferenceViewModel =
            ViewModelProviders.of(this).get(PreferenceViewModel::class.java).apply {
                // モード
                mode.observe(this@PreferenceFragment, Observer {
                    modeValue.setSelection(it.id)
                })
                // Evernote連携
                evernoteName.observe(this@PreferenceFragment, Observer {
                    evernoteValue.text = it
                })
                // ノートブック
                notebookName.observe(this@PreferenceFragment, Observer {
                    notebookValue.text = it
                })
            }
        val root = inflater.inflate(R.layout.fragment_preference, container, false)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        titleTextView.text = "設定"
        initSpinner()
        initClick()
    }

    override fun onResume() {
        super.onResume()

        // evernote連携再設定
        isEvernoteLoggedIn = EvernoteSession.getInstance().isLoggedIn
        if(isEvernoteLoggedIn){
            handler.postDelayed({
                GetUserTask().start(this, "preference")
            }, 100L)

        }

        preferenceViewModel.initData()
    }

    private fun initSpinner(){
        var adapter: ArrayAdapter<String>
        var modeList = Mode.values().map {it.modeName}
        context?.apply {
            adapter = ArrayAdapter<String>(
                this, R.layout.spinner_item, modeList
            )
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)

            modeValue.apply {

                this.adapter = adapter
                this.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    // 選択変更時
                    override fun onItemSelected(
                        adapterView: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        val selected: Mode = Mode.values().get(position)
                        preferenceViewModel.mode.value = selected
                        MyApplication.mode = selected   // グローバルデータ更新
                        Prefs().mode.put(position)
                    }

                    override fun onNothingSelected(adapterView: AdapterView<*>?) { // Nop
                    }
                }
            }
        }

    }


    private fun initClick(){
        backImageView.setOnClickListener {
            getFragmentManager()?.popBackStack()
        }
        evernoteValue.setOnClickListener {
//            if(!isEvernoteLoggedIn)
                EvernoteSession.getInstance().authenticate(this.activity)
        }
        notebookValue.setOnClickListener {
            NotebookNameActivity.start(this.activity)
        }
    }

    fun getModeName(mode: Mode): String {
        if(mode.isCache) return getString(R.string.mode_cache)
        if(mode.isEvernote) return getString(R.string.mode_evernote)
        return ""
    }

    /**
     * 呼び出されていない
     */
    override fun onLoginFinished(successful: Boolean) {
        if (successful) {
            GetUserTask().start(this)
        } else {
            Toast.makeText(context, "Evernote連携に失敗しました", Toast.LENGTH_LONG).show()
        }
    }

    @TaskResult(id = "preference")
    fun onGetUser(user: User) {
        MyApplication.evernoteUser = user
        if (user != null) {
            preferenceViewModel.evernoteName.value = user.username
        }
    }

}
