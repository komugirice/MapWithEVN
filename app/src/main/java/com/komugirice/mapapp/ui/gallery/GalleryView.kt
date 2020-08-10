package com.komugirice.mapapp.ui.gallery

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.komugirice.mapapp.R
import com.komugirice.mapapp.data.EvImageData
import com.komugirice.mapapp.databinding.ImageCellBinding
import com.komugirice.mapapp.databinding.ImageMapGalleryCellBinding
import com.komugirice.mapapp.extension.eliminatePostalCode
import com.komugirice.mapapp.extension.extractPostalCode
import com.komugirice.mapapp.ui.map.MapGalleryView

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

        lateinit var onClickCallBack: (EvImageData) -> Unit


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

        override fun getItemCount(): Int = if(items.isEmpty()) 1 else items.size

        override fun getItemViewType(position: Int): Int {
            return if(items.isEmpty()) VIEW_TYPE_EMPTY else VIEW_TYPE_GALLERY
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                if(viewType == VIEW_TYPE_GALLERY) {
                    return ImageCellViewHolder(ImageCellBinding.inflate(LayoutInflater.from(context), parent, false))
                } else {
                    // Emptyセル
                    return MapGalleryView.EmptyViewHolder(LayoutInflater.from(context)
                            .inflate(R.layout.empty_cell, parent, false))
                }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is ImageCellViewHolder)
                onBindViewHolder(holder, position)
            else if (holder is MapGalleryView.EmptyViewHolder)
                onBindEmptyViewHolder(holder, position)
        }

        private fun onBindViewHolder(holder: ImageCellViewHolder, position: Int) {
            val data = items[position]
            holder.binding.evImageData = data
            holder.binding.root.setOnClickListener {
                onClickCallBack.invoke(data)
            }
        }

        /**
         * itemsが0件のViewHolder
         *
         * @param holder
         * @param position
         */
        private fun onBindEmptyViewHolder(holder: MapGalleryView.EmptyViewHolder, position: Int) {
        }
    }

    class ImageCellViewHolder(val binding: ImageCellBinding) : RecyclerView.ViewHolder(binding.root)

    class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var searchZeroText = itemView.findViewById(R.id.registZeroText) as TextView
    }

    companion object {
        const val VIEW_TYPE_GALLERY = 0
        const val VIEW_TYPE_EMPTY = -1
    }
}