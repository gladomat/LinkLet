package com.gladomat.linklet.ui.screens.note

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gladomat.linklet.data.settings.ThemeId
import com.gladomat.linklet.ui.theme.LinkLetAppTheme
import com.gladomat.linklet.ui.theme.LocalThemeTokens
import com.gladomat.linklet.ui.theme.ThemeRegistry

/**
 * Horizontal row of tag pills.
 * Scrollable if tags exceed available width.
 */
@Composable
fun TagRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            TagPill(tag = tag)
        }
    }
}

@Composable
private fun TagPill(
    tag: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "#$tag",
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Medium,
        ),
        color = LocalThemeTokens.current.tagFg,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LocalThemeTokens.current.tagBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Preview
@Composable
private fun TagRowPreview() {
    LinkLetAppTheme {
        Surface {
            TagRow(
                tags = listOf("tea", "brewing", "lifestyle"),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Preview
@Composable
private fun TagRowDarkPreview() {
    LinkLetAppTheme(theme = ThemeRegistry.resolve(ThemeId.CATPPUCCIN_MOCHA)) {
        Surface {
            TagRow(
                tags = listOf("tea", "brewing", "lifestyle", "productivity", "wellness"),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Preview
@Composable
private fun TagRowEmptyPreview() {
    LinkLetAppTheme {
        Surface {
            TagRow(
                tags = emptyList(),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
