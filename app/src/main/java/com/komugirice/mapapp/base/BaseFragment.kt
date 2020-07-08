package com.komugirice.mapapp.base

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.komugirice.mapapp.databinding.ProgressDialogBinding

/**
 * Created by Jane on 2018/04/05.
 */
abstract class BaseFragment : Fragment() {

    private var progressDialog: MaterialDialog? = null

    protected fun showProgressDialog(context: Context){
        dismissProgressDialog()
        this.progressDialog =  MaterialDialog(context).apply {
            cancelable(true)
            val dialogBinding = ProgressDialogBinding.inflate(
                LayoutInflater.from(context),
                null,
                false
            )
            setContentView(dialogBinding.root)
        }
        // 背景を透過
        this.progressDialog?.apply {
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.0f)
            }
            show()
        }
    }

    protected fun dismissProgressDialog(){
        this.progressDialog?.dismiss()
    }



}