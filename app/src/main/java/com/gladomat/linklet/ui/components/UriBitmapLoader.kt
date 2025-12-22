package com.gladomat.linklet.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
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
    return loadImageBitmap(
        context = context,
        uri = uri,
        spec = ImageDecodeSpec(
            maxWidthPx = maxWidthPx,
            maxHeightPx = maxHeightPx,
            maxDecodedPixels = DEFAULT_MAX_DECODED_PIXELS,
            preferredConfig = Bitmap.Config.ARGB_8888,
        ),
    )
}

data class ImageDecodeSpec(
    val maxWidthPx: Int,
    val maxHeightPx: Int,
    val maxDecodedPixels: Long,
    val preferredConfig: Bitmap.Config,
)

private const val DEFAULT_MAX_DECODED_PIXELS: Long = 16_000_000L

suspend fun loadImageBitmap(
    context: Context,
    uri: Uri,
    spec: ImageDecodeSpec,
): Result<ImageBitmap> {
    val safeWidth = max(1, spec.maxWidthPx)
    val safeHeight = max(1, spec.maxHeightPx)
    val safeMaxDecodedPixels = spec.maxDecodedPixels.coerceAtLeast(1L)

    return runCatching {
        val lower = uri.toString().lowercase()
        if (lower.endsWith(".svg")) {
            throw IllegalStateException("SVG images are not supported")
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInputStreamForDecode(context, uri).use { stream ->
            val decoded = BitmapFactory.decodeStream(stream, null, bounds)
            if (decoded == null && (bounds.outWidth <= 0 || bounds.outHeight <= 0)) {
                throw IllegalStateException("Failed to decode image bounds at $uri")
            }
        }

        val sampleSize = calculateInSampleSizeWithPixelCap(bounds, safeWidth, safeHeight, safeMaxDecodedPixels)
        val decode = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sampleSize
            inPreferredConfig = spec.preferredConfig
            if (spec.preferredConfig == Bitmap.Config.RGB_565) {
                inDither = true
            }
        }
        val bitmap = openInputStreamForDecode(context, uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, decode)
        } ?: throw IllegalStateException("Failed to decode image at $uri")

        bitmap.asImageBitmap()
    }
}

private fun openInputStreamForDecode(context: Context, uri: Uri): InputStream {
    if (uri.scheme == null || uri.scheme.equals("file", ignoreCase = true)) {
        val path = uri.path ?: throw IllegalStateException("Missing file path for uri=$uri")
        return FileInputStream(File(path))
    }

    val resolver = context.contentResolver
    resolver.openInputStream(uri)?.let { return it }

    Log.w("UriBitmapLoader", "openInputStream() returned null for uri=$uri; trying AssetFileDescriptor")
    val afd = resolver.openAssetFileDescriptor(uri, "r")
        ?: throw IllegalStateException("Unable to open input stream for $uri")
    return afd.createInputStream()
}

@Suppress("SameParameterValue")
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

internal fun calculateInSampleSizeWithPixelCap(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int,
    maxDecodedPixels: Long,
): Int {
    val base = calculateInSampleSize(options, reqWidth, reqHeight)
    val width = options.outWidth.coerceAtLeast(0)
    val height = options.outHeight.coerceAtLeast(0)
    if (width == 0 || height == 0) return base.coerceAtLeast(1)

    var sampleSize = base.coerceAtLeast(1)
    val safeMax = maxDecodedPixels.coerceAtLeast(1L)
    while (decodedPixelsForSampleSize(width, height, sampleSize) > safeMax) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

private fun decodedPixelsForSampleSize(width: Int, height: Int, sampleSize: Int): Long {
    val safeSample = sampleSize.coerceAtLeast(1)
    val decodedWidth = (width / safeSample).coerceAtLeast(1)
    val decodedHeight = (height / safeSample).coerceAtLeast(1)
    return decodedWidth.toLong() * decodedHeight.toLong()
}
