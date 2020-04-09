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
import com.komugirice.mapapp.databinding.FragmentGalleryBinding
import kotlinx.android.synthetic.main.fragment_gallery.*

class GalleryFragment : Fragment() {


    private lateinit var binding: FragmentGalleryBinding
    private lateinit var viewModel: GalleryViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        inflater.inflate(R.layout.fragment_gallery, container, false)

        binding = FragmentGalleryBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this

        viewModel = ViewModelProviders.of(this).get(GalleryViewModel::class.java).apply {
            items.observe(this@GalleryFragment, Observer {
                binding.apply {
                    galleryView.customAdapter.refresh(it)
                    swipeRefreshLayout.isRefreshing = false
                }
            })
        }

        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel.initData()

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.initData()
            swipeRefreshLayout.isRefreshing = false
        }
    }


    companion object {
        fun start(context: Context?) = context?.apply {
            startActivity(Intent(context, GalleryFragment::class.java))
        }
    }

}
