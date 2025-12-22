package com.gladomat.linklet.ui.components

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun FullscreenImageViewer(
    uri: Uri,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val maxWidthPx = with(density) { config.screenWidthDp.dp.roundToPx() }.coerceAtLeast(1)
    val maxHeightPx = with(density) { config.screenHeightDp.dp.roundToPx() }.coerceAtLeast(1)

    var bitmap: ImageBitmap? by remember(uri) { mutableStateOf(null) }
    var error: String? by remember(uri) { mutableStateOf(null) }

    LaunchedEffect(uri, maxWidthPx, maxHeightPx) {
        bitmap = null
        error = null
        val result = withContext(Dispatchers.IO) {
            loadImageBitmap(
                context = context,
                uri = uri,
                spec = ImageDecodeSpec(
                    maxWidthPx = maxWidthPx,
                    maxHeightPx = maxHeightPx,
                    maxDecodedPixels = 16_000_000L,
                    preferredConfig = Bitmap.Config.ARGB_8888,
                ),
            )
        }
        result.fold(
            onSuccess = { bitmap = it },
            onFailure = { error = it.message ?: "Failed to decode image" },
        )
    }

    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offsetX by remember(uri) { mutableFloatStateOf(0f) }
    var offsetY by remember(uri) { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("fullscreen-image-viewer")
            .background(Color.Black)
            .combinedClickable(
                onClick = onClose,
                onDoubleClick = {
                    val next = if (scale < 1.5f) 2f else 1f
                    scale = next
                    offsetX = 0f
                    offsetY = 0f
                },
            )
            .pointerInput(uri) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 6f)
                    scale = nextScale
                    if (nextScale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            error != null -> Text(
                text = error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f)).padding(12.dp),
            )
            bitmap == null -> Text(
                text = "Loading…",
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f)).padding(12.dp),
            )
            else -> {
                val loaded = bitmap!!
                androidx.compose.foundation.Image(
                    bitmap = loaded,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
        }
    }
}
