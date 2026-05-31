package com.renamer.app

import android.net.Uri

/** A single image discovered in the device's media store. */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    /** Epoch milliseconds the photo was taken (falls back to date added). */
    val dateTaken: Long,
    var selected: Boolean = false
)
