package com.renamer.app

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.renamer.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PhotoAdapter

    /** Names computed before asking the system for write consent. */
    private var pendingRenames: List<Pair<Uri, String>> = emptyList()

    private val permission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadImages()
        } else {
            toast("Permission needed to read your photos")
        }
    }

    private val renameConsent = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            applyPendingRenames()
        } else {
            toast("Rename cancelled")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = PhotoAdapter(contentResolver, lifecycleScope) { updateSelectionCount() }
        binding.recycler.layoutManager = GridLayoutManager(this, 3)
        binding.recycler.adapter = adapter

        // Restore the last used name format and keep it saved as it changes.
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.getString(KEY_FORMAT, null)?.let { binding.formatInput.setText(it) }
        binding.formatInput.doAfterTextChanged { text ->
            prefs.edit().putString(KEY_FORMAT, text?.toString().orEmpty()).apply()
        }

        binding.selectAll.setOnClickListener { adapter.setAllSelected(true) }
        binding.selectNone.setOnClickListener { adapter.setAllSelected(false) }
        binding.refresh.setOnClickListener { ensurePermissionThenLoad() }
        binding.renameButton.setOnClickListener { onRenameClicked() }

        ensurePermissionThenLoad()
    }

    private fun ensurePermissionThenLoad() {
        if (ContextCompat.checkSelfPermission(this, permission)
            == PackageManager.PERMISSION_GRANTED
        ) {
            loadImages()
        } else {
            requestPermission.launch(permission)
        }
    }

    private fun loadImages() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { queryImages() }
            adapter.submit(items)
            binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun queryImages(): List<MediaItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sort = MediaStore.Images.Media.DATE_TAKEN + " DESC, " +
            MediaStore.Images.Media.DATE_ADDED + " DESC"

        val result = mutableListOf<MediaItem>()
        contentResolver.query(collection, projection, null, null, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: ("image_" + id)
                val taken = cursor.getLong(takenCol)
                // DATE_TAKEN is in millis; DATE_ADDED is in seconds.
                val date = if (taken > 0) taken else cursor.getLong(addedCol) * 1000L
                val uri = ContentUris.withAppendedId(collection, id)
                result.add(MediaItem(id, uri, name, date))
            }
        }
        return result
    }

    private fun onRenameClicked() {
        val template = binding.formatInput.text?.toString()?.trim().orEmpty()
        if (template.isBlank()) {
            toast("Type a name format first")
            return
        }
        val selected = adapter.selectedItems()
        if (selected.isEmpty()) {
            toast("Select at least one photo")
            return
        }

        pendingRenames = computeNewNames(template, selected)
        val uris = pendingRenames.map { it.first }
        val pendingIntent = MediaStore.createWriteRequest(contentResolver, uris)
        renameConsent.launch(
            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        )
    }

    /**
     * Maps each selected item to its target file name. When more than one
     * selected photo resolves to the same base name, every one of them (the
     * first included) gets a number appended with no space: name1, name2, ...
     * Names that are unique within the selection are left without a number.
     */
    private fun computeNewNames(
        template: String,
        selected: List<MediaItem>
    ): List<Pair<Uri, String>> {
        val bases = selected.map { NameFormatter.format(template, it.dateTaken) }

        // How many selected photos share each base name (case-insensitive).
        val totals = HashMap<String, Int>()
        bases.forEach { base ->
            val key = base.lowercase()
            totals[key] = (totals[key] ?: 0) + 1
        }

        val seen = HashMap<String, Int>()
        return selected.mapIndexed { index, item ->
            val ext = NameFormatter.extensionOf(item.displayName)
            val base = bases[index]
            val key = base.lowercase()
            val name = if ((totals[key] ?: 0) > 1) {
                val n = (seen[key] ?: 0) + 1
                seen[key] = n
                withExt(base + n, ext)
            } else {
                withExt(base, ext)
            }
            item.uri to name
        }
    }

    private fun withExt(base: String, ext: String): String =
        if (ext.isBlank()) base else base + "." + ext

    private fun applyPendingRenames() {
        val work = pendingRenames
        if (work.isEmpty()) return
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                var success = 0
                var failure = 0
                for ((uri, newName) in work) {
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, newName)
                        }
                        val updated = contentResolver.update(uri, values, null, null)
                        if (updated > 0) success++ else failure++
                    } catch (e: Exception) {
                        failure++
                    }
                }
                success to failure
            }
            val ok = outcome.first
            val fail = outcome.second
            pendingRenames = emptyList()
            toast(if (fail == 0) "Renamed " + ok + " photo(s)" else "Renamed " + ok + ", failed " + fail)
            loadImages()
        }
    }

    private fun updateSelectionCount() {
        val count = adapter.selectedItems().size
        binding.count.text = getString(R.string.selected_count, count, adapter.items.size)
        binding.renameButton.isEnabled = count > 0
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val PREFS = "renamer_prefs"
        private const val KEY_FORMAT = "name_format"
    }
}
