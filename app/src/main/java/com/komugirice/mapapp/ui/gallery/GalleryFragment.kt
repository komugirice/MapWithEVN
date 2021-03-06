package com.komugirice.mapapp.ui.gallery

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.komugirice.mapapp.R
import com.komugirice.mapapp.data.EvImageData
import com.komugirice.mapapp.databinding.FragmentGalleryBinding
import com.komugirice.mapapp.ui.map.MapFragment
import kotlinx.android.synthetic.main.activity_header.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_gallery.*

class GalleryFragment : Fragment() {


    private lateinit var binding: FragmentGalleryBinding
    private lateinit var viewModel: GalleryViewModel

    private lateinit var mCallback: OnImageSelectedListener

    // The container Activity must implement this interface so the frag can deliver messages
    interface OnImageSelectedListener {
        /** Called by MapFragment when a list item is selected  */
        fun onImageSelected(data: EvImageData)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mCallback = context as OnImageSelectedListener

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        inflater.inflate(R.layout.fragment_gallery, container, false)

        binding = FragmentGalleryBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this

        // initFriendView
        binding.galleryView.customAdapter.onClickCallBack = {
            MapFragment.isRefresh = true
            MapFragment.refreshEvImageData = it
            this.parentFragmentManager.popBackStack()
            mCallback.onImageSelected(it)
        }
        
        viewModel = ViewModelProviders.of(this).get(GalleryViewModel::class.java).apply {
            context = context
            items.observe(viewLifecycleOwner, Observer {
                binding.apply {
                    galleryView.customAdapter.refresh(it)
                    swipeRefreshLayout.isRefreshing = false
                }
            })
        }

        binding.header.backImageView.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()

        // ナビゲーションからギャラリーを非活性
        activity?.nav_view?.getMenu()?.findItem(R.id.nav_gallery)?.setEnabled(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initData()

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.initData()
            swipeRefreshLayout.isRefreshing = false
        }

        titleTextView.text = getString(R.string.menu_gallery)
    }


    companion object {
        fun start(context: Context?) = context?.apply {
            startActivity(Intent(context, GalleryFragment::class.java))
        }
    }

}
