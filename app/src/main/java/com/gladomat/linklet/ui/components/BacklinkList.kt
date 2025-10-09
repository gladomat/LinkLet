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

@Composable
fun BacklinkList(
    backlinks: List<LinkEntityDto>,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (backlinks.isEmpty()) return

    Column(modifier = modifier.padding(top = 24.dp)) {
        Text(
            text = "Backlinks",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        backlinks.forEach { backlink ->
            Text(
                text = backlink.alias ?: backlink.source,
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
