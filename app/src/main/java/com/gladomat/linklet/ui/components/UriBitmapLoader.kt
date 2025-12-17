package com.gladomat.linklet.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max

/**
 * Decodes a bitmap from [uri], downsampling to fit within [maxWidthPx] x [maxHeightPx].
 *
 * This is a minimal, dependency-free loader intended for local images (SAF or file://).
 */
suspend fun loadImageBitmap(
    context: Context,
    uri: Uri,
    maxWidthPx: Int,
    maxHeightPx: Int,
): Result<ImageBitmap> {
    val safeWidth = max(1, maxWidthPx)
    val safeHeight = max(1, maxHeightPx)

    return runCatching {
        val lower = uri.toString().lowercase()
        if (lower.endsWith(".svg")) {
            throw IllegalStateException("SVG images are not supported")
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: throw IllegalStateException("Unable to open input stream for $uri")

        val sampleSize = calculateInSampleSize(bounds, safeWidth, safeHeight)
        val decode = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sampleSize
        }
        val bitmap = context.contentResolver.openInputStream(uri)
            ?.use { stream -> BitmapFactory.decodeStream(stream, null, decode) }
            ?: throw IllegalStateException("Failed to decode image at $uri")

        bitmap.asImageBitmap()
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        while (height / inSampleSize > reqHeight && width / inSampleSize > reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}
