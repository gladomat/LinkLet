package com.gladomat.linklet.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun OrgInlineImageBlock(
    uri: Uri,
    caption: String?,
    align: String?,
    widthHint: String?,
    onOpen: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap: ImageBitmap? by remember(uri) { mutableStateOf(null) }
    var error: String? by remember(uri) { mutableStateOf(null) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val maxHeightPx = maxWidthPx.coerceAtLeast(1) * 3
        val preferredWidth = remember(maxWidth, widthHint) { parsePreferredWidth(maxWidth = maxWidth, widthHint = widthHint) }

        LaunchedEffect(uri, maxWidthPx, maxHeightPx) {
            bitmap = null
            error = null
            val result = withContext(Dispatchers.IO) { loadImageBitmap(context, uri, maxWidthPx, maxHeightPx) }
            result.fold(
                onSuccess = { bitmap = it },
                onFailure = { error = it.message ?: "Failed to decode image" },
            )
        }

        val contentAlignment = when (align?.lowercase()) {
            "center" -> Alignment.Center
            "right" -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }

        Column(
            modifier = if (preferredWidth != null) Modifier.width(preferredWidth) else Modifier.fillMaxWidth(),
            horizontalAlignment = when (contentAlignment) {
                Alignment.Center -> Alignment.CenterHorizontally
                Alignment.CenterEnd -> Alignment.End
                else -> Alignment.Start
            },
        ) {
            when {
                error != null -> {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                bitmap == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(Color.Black.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Loading image…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                else -> {
                    val loaded = bitmap!!
                    val ratio = remember(loaded) {
                        val w = loaded.width.coerceAtLeast(1)
                        val h = loaded.height.coerceAtLeast(1)
                        w.toFloat() / h.toFloat()
                    }
                    androidx.compose.foundation.Image(
                        bitmap = loaded,
                        contentDescription = caption,
                        modifier = Modifier
                            .aspectRatio(ratio)
                            .testTag("org-inline-image")
                            .clickable { onOpen(uri) }
                            .padding(vertical = 6.dp),
                        contentScale = ContentScale.Fit,
                        alignment = contentAlignment,
                    )
                }
            }
            if (!caption.isNullOrBlank()) {
                Text(
                    text = caption,
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun parsePreferredWidth(maxWidth: Dp, widthHint: String?): Dp? {
    val raw = widthHint?.trim()?.lowercase().orEmpty()
    if (raw.isBlank()) return null

    if (raw.endsWith('%')) {
        val percent = raw.removeSuffix("%").trim().toFloatOrNull() ?: return null
        if (percent <= 0f || percent > 100f) return null
        return maxWidth * (percent / 100f)
    }

    val numeric = raw.removeSuffix("dp").trim().toFloatOrNull() ?: return null
    if (numeric <= 0f) return null
    val clamped = numeric.roundToInt().dp
    return if (clamped > maxWidth) maxWidth else clamped
}
