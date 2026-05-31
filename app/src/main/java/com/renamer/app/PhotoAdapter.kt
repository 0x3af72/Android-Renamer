package com.renamer.app

import android.content.ContentResolver
import android.util.Size
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.renamer.app.databinding.ItemPhotoBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Grid adapter that shows a thumbnail, current name and capture date per photo. */
class PhotoAdapter(
    private val resolver: ContentResolver,
    private val scope: CoroutineScope,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    val items = mutableListOf<MediaItem>()

    private val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

    fun submit(newItems: List<MediaItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun setAllSelected(selected: Boolean) {
        items.forEach { it.selected = selected }
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun selectedItems(): List<MediaItem> = items.filter { it.selected }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PhotoViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun onViewRecycled(holder: PhotoViewHolder) {
        holder.thumbnailJob?.cancel()
        holder.thumbnailJob = null
    }

    inner class PhotoViewHolder(
        private val binding: ItemPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        var thumbnailJob: Job? = null

        fun bind(item: MediaItem) {
            binding.name.text = item.displayName
            binding.date.text = dateFormat.format(Date(item.dateTaken))
            binding.checkbox.isChecked = item.selected

            binding.root.setOnClickListener {
                val newState = !item.selected
                item.selected = newState
                binding.checkbox.isChecked = newState
                onSelectionChanged()
            }

            binding.thumbnail.setImageDrawable(null)
            thumbnailJob?.cancel()
            val uri = item.uri
            thumbnailJob = scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    runCatching {
                        resolver.loadThumbnail(uri, Size(256, 256), null)
                    }.getOrNull()
                }
                if (items.getOrNull(bindingAdapterPosition)?.uri == uri && bmp != null) {
                    binding.thumbnail.setImageBitmap(bmp)
                }
            }
        }
    }
}
