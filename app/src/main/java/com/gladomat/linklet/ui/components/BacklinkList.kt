package com.gladomat.linklet.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gladomat.linklet.domain.repository.LinkEntityDto
import java.util.LinkedHashMap

@Composable
fun BacklinkList(
    backlinks: List<LinkEntityDto>,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val distinctBacklinks = dedupeBacklinks(backlinks)
    if (distinctBacklinks.isEmpty()) return

    Column(modifier = modifier.padding(top = 24.dp)) {
        Text(
            text = "Backlinks",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        distinctBacklinks.forEach { backlink ->
            val displayText = backlink.sourceTitle ?: backlink.alias ?: backlink.source
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onOpenNote(backlink.source) },
            )
        }
    }
}

fun dedupeBacklinks(backlinks: List<LinkEntityDto>): List<LinkEntityDto> {
    if (backlinks.isEmpty()) return emptyList()
    val ordered = LinkedHashMap<String, LinkEntityDto>()
    backlinks.forEach { backlink ->
        val existing = ordered[backlink.source]
        if (existing == null) {
            ordered[backlink.source] = backlink
        } else {
            val updated = existing.copy(
                sourceTitle = existing.sourceTitle ?: backlink.sourceTitle,
                alias = existing.alias ?: backlink.alias,
            )
            ordered[backlink.source] = updated
        }
    }
    return ordered.values.toList()
}
