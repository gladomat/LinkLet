package com.gladomat.linklet.ui.screens.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gladomat.linklet.ui.theme.LinkLetAppTheme

/**
 * Common org-roam properties that can be edited.
 */
private val COMMON_PROPERTIES = listOf(
    PropertyDefinition("ID", "Unique org-roam identifier", readOnly = true),
    PropertyDefinition("ROAM_REFS", "External reference URLs (space-separated)"),
    PropertyDefinition("ROAM_ALIASES", "Alternative titles (space-separated)"),
)

private data class PropertyDefinition(
    val key: String,
    val description: String,
    val readOnly: Boolean = false,
)

/**
 * Dialog for viewing and editing common org-roam properties.
 */
@Composable
fun PropertiesEditorDialog(
    properties: Map<String, String>,
    onSave: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editedProperties by remember(properties) {
        mutableStateOf(properties.toMutableMap())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = "Note Properties",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                COMMON_PROPERTIES.forEach { prop ->
                    PropertyRow(
                        label = prop.key,
                        description = prop.description,
                        value = editedProperties[prop.key] ?: "",
                        onValueChange = { newValue ->
                            if (!prop.readOnly) {
                                editedProperties = editedProperties.toMutableMap().apply {
                                    if (newValue.isBlank()) remove(prop.key) else put(prop.key, newValue)
                                }
                            }
                        },
                        readOnly = prop.readOnly,
                    )
                    if (prop != COMMON_PROPERTIES.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(editedProperties) },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PropertyRow(
    label: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
            if (readOnly) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Read-only",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.height(16.dp).width(16.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !readOnly,
            singleLine = true,
            placeholder = {
                Text(if (readOnly) "Generated automatically" else "Enter value...")
            },
        )
    }
}

@Preview
@Composable
private fun PropertiesEditorDialogPreview() {
    LinkLetAppTheme {
        Surface {
            PropertiesEditorDialog(
                properties = mapOf(
                    "ID" to "abc123-def456",
                    "ROAM_REFS" to "https://example.com",
                ),
                onSave = {},
                onDismiss = {},
            )
        }
    }
}
