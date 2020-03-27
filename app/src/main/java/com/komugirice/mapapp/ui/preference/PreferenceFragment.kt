package com.komugirice.mapapp.ui.preference


import android.os.Bundle
import android.provider.CalendarContract.Colors
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.evernote.client.android.EvernoteSession
import com.evernote.edam.type.User
import com.komugirice.mapapp.MyApplication
import com.komugirice.mapapp.MyApplication.Companion.isEvernoteLoggedIn
import com.komugirice.mapapp.Prefs
import com.komugirice.mapapp.R
import com.komugirice.mapapp.enums.Mode
import com.komugirice.mapapp.task.GetUserTask
import com.komugirice.mapapp.ui.map.MapFragment
import com.komugirice.mapapp.ui.notebook.NotebookNameActivity
import kotlinx.android.synthetic.main.activity_header.*
import kotlinx.android.synthetic.main.fragment_preference.*
import net.vrallev.android.task.TaskResult


/**
 * @author komugirice
 */
class PreferenceFragment: Fragment() {

    private lateinit var preferenceViewModel: PreferenceViewModel

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
            GetUserTask().start(this)
        }

        preferenceViewModel.initData()
    }

    private fun initSpinner(){
        var adapter: ArrayAdapter<Mode>
        var modeList = mutableListOf<Mode>().apply {
            add(Mode.CACHE)
            add(Mode.EVERNOTE)
        }
        context?.apply {
            adapter = ArrayAdapter<Mode>(
                this, R.layout.row_spinner, Mode.values()
            )

            modeValue.apply {

                this.adapter = adapter
                this.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        adapterView: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        val selected: Mode = Mode.values().get(position)
                        preferenceViewModel.mode.value = selected
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
            MapFragment.start(context)
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

    @TaskResult
    fun onGetUser(user: User) {
        MyApplication.evernoteUser = user
        if (user != null) {
            evernoteValue.text = user.username
        }
    }

}
