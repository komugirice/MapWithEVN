package com.komugirice.mapapp.ui.gallery

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.komugirice.mapapp.EvImageData
import com.komugirice.mapapp.databinding.ImageCellBinding
import com.komugirice.mapapp.extension.eliminatePostalCode
import com.komugirice.mapapp.extension.extractPostalCode

class GalleryView : RecyclerView {
    constructor(ctx: Context) : super(ctx)
    constructor(ctx: Context, attrs: AttributeSet?) : super(ctx, attrs)
    constructor(ctx: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        ctx,
        attrs,
        defStyleAttr
    )

    val customAdapter by lazy {
        Adapter(
            context
        )
    }

    init {
        adapter = customAdapter
        setHasFixedSize(true)
        layoutManager = LinearLayoutManager(context)
    }

    class Adapter(val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val items = mutableListOf<EvImageData>()


        fun refresh(list: List<EvImageData>) {
            items.apply {
                clear()
                addAll(list)
            }
            notifyDataSetChanged()
        }

        fun clear() {
            items.clear()
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            ImageCellViewHolder(ImageCellBinding.inflate(LayoutInflater.from(context), parent, false))

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is ImageCellViewHolder)
                onBindViewHolder(holder, position)
        }

        private fun onBindViewHolder(holder: ImageCellViewHolder, position: Int) {
            val data = items[position]
            holder.binding.apply{
                filePath = data.filePath
                postalCode = data.address.extractPostalCode()
                address = data.address.eliminatePostalCode()
                lat = data.lat.toString()
                lon = data.lon.toString()

            }
            holder.binding.root.setOnClickListener {

            }
        }

    }

    class ImageCellViewHolder(val binding: ImageCellBinding) : RecyclerView.ViewHolder(binding.root)

}