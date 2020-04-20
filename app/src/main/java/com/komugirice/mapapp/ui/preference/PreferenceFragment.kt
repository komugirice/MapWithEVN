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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
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
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_preference.*
import net.vrallev.android.task.TaskResult


/**
 * @author komugirice
 */
class PreferenceFragment: Fragment() {

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
                mode.observe(viewLifecycleOwner, Observer {
                    modeValue.setSelection(it.id)
                })
                // Evernote連携
                evernoteName.observe(viewLifecycleOwner, Observer {
                    evernoteValue.text = it
                })
                // ノートブック
                notebookName.observe(viewLifecycleOwner, Observer {
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

        // ナビゲーションからギャラリーを非活性
        activity?.nav_view?.getMenu()?.findItem(R.id.nav_gallery)?.setEnabled(false)

        // evernote連携再設定
        isEvernoteLoggedIn = EvernoteSession.getInstance().isLoggedIn
        if(isEvernoteLoggedIn){
            handler.postDelayed({
                GetUserTask().start(this, "init")
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
            parentFragmentManager.popBackStack()
        }
        evernoteValue.setOnClickListener {
//            if(!isEvernoteLoggedIn)
                EvernoteSession.getInstance().authenticate(this.requireActivity())
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

    @TaskResult(id = "init")
    fun onGetUser(user: User) {
        MyApplication.evernoteUser = user
        if (user != null) {
            preferenceViewModel.evernoteName.value = user.username
        }
    }

    @TaskResult(id = "onLoginFinished")
    fun onLoginFinishedGetUser(user: User) {
        MyApplication.evernoteUser = user
        if (user != null) {
            preferenceViewModel.evernoteName.value = user.username
            // ノートブック削除
            Prefs().notebookName.remove()
            preferenceViewModel.notebookName.value = "なし"
        }
    }

}
