package com.gladomat.linklet.ui.screens.noteedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gladomat.linklet.ui.theme.LinkLetAppTheme
import com.gladomat.linklet.viewmodel.noteedit.NoteEditUiState
import com.gladomat.linklet.viewmodel.noteedit.NoteEditViewModel

@Composable
fun NoteEditRoute(
    onDone: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: NoteEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    val fileName = when (val current = state) {
        is NoteEditUiState.Editing -> current.fileName
        is NoteEditUiState.Saved -> current.path
        else -> null
    }

    // Dismiss dialog when save completes
    LaunchedEffect(state) {
        if (state is NoteEditUiState.Saved) {
            android.util.Log.d("NoteEditRoute", "State changed to Saved, dismissing dialog")
            showUnsavedChangesDialog = false
        }
    }

    when (val current = state) {
        is NoteEditUiState.Saved -> LaunchedEffect(current) {
            android.util.Log.d("NoteEditRoute", "Save completed, calling onDone()")
            onDone(current.path)
        }

        is NoteEditUiState.Error -> LaunchedEffect(current) {
            snackbarHostState.showSnackbar(current.message)
        }

        else -> Unit
    }

    val handleBackPress: () -> Unit = {
        val hasChanges = viewModel.hasUnsavedChanges()
        val isSaving = (state as? NoteEditUiState.Editing)?.isSaving == true
        android.util.Log.d("NoteEditRoute", "Back pressed: hasChanges=$hasChanges, isSaving=$isSaving")
        
        if (isSaving) {
            // Do nothing while saving is in progress
            android.util.Log.d("NoteEditRoute", "Back pressed ignored - save in progress")
        } else if (hasChanges) {
            android.util.Log.d("NoteEditRoute", "Showing unsaved changes dialog")
            showUnsavedChangesDialog = true
        } else {
            android.util.Log.d("NoteEditRoute", "No changes, navigating back")
            onNavigateBack()
        }
    }

    Surface {
        NoteEditScreen(
            state = state,
            fileName = fileName,
            onContentChange = viewModel::updateContent,
            onSave = viewModel::save,
            onBack = handleBackPress,
            onApplyHeading = { level -> viewModel.insertHeading(level) },
            onApplyBold = viewModel::insertBold,
            onApplyItalic = viewModel::insertItalic,
            onApplySrc = viewModel::insertSrcBlock,
            onApplyUnorderedList = viewModel::insertUnorderedList,
            onApplyOrderedList = viewModel::insertOrderedList,
            onIncreaseIndentation = viewModel::increaseIndentation,
            onDecreaseIndentation = viewModel::decreaseIndentation,
            onUndo = viewModel::undo,
            snackbarHostState = snackbarHostState,
        )

        // Only show dialog if we're in Editing state (not Saved, Loading, or Error)
        if (showUnsavedChangesDialog && state is NoteEditUiState.Editing) {
            android.util.Log.d("NoteEditRoute", "Rendering unsaved changes dialog")
            UnsavedChangesDialog(
                onDismiss = { 
                    android.util.Log.d("NoteEditRoute", "Dialog dismissed by user")
                    showUnsavedChangesDialog = false 
                },
                onDiscard = {
                    android.util.Log.d("NoteEditRoute", "User chose to discard changes")
                    showUnsavedChangesDialog = false
                    onNavigateBack()
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun NoteEditScreen(
    state: NoteEditUiState,
    fileName: String?,
    onContentChange: (TextFieldValue) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onApplyHeading: (Int) -> Unit,
    onApplyBold: () -> Unit,
    onApplyItalic: () -> Unit,
    onApplySrc: () -> Unit,
    onApplyUnorderedList: () -> Unit,
    onApplyOrderedList: () -> Unit,
    onIncreaseIndentation: () -> Unit,
    onDecreaseIndentation: () -> Unit,
    onUndo: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val isEditing = state is NoteEditUiState.Editing
    val isSaving = (state as? NoteEditUiState.Editing)?.isSaving == true
    val isKeyboardVisible = WindowInsets.isImeVisible

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            NoteEditTopAppBar(
                title = "Edit note",
                fileName = fileName,
                isEditing = isEditing,
                isSaving = isSaving,
                onBack = onBack,
                onUndo = onUndo,
                onSave = onSave,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (state is NoteEditUiState.Editing && isKeyboardVisible) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 6.dp,
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                ) {
                    FormattingToolbar(
                        onApplyHeading = onApplyHeading,
                        onApplyBold = onApplyBold,
                        onApplyItalic = onApplyItalic,
                        onApplySrc = onApplySrc,
                        onApplyUnorderedList = onApplyUnorderedList,
                        onApplyOrderedList = onApplyOrderedList,
                        onIncreaseIndentation = onIncreaseIndentation,
                        onDecreaseIndentation = onDecreaseIndentation,
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (state) {
                NoteEditUiState.Loading -> LoadingState()
                is NoteEditUiState.Error -> ErrorState(state.message, onBack)
                is NoteEditUiState.Saved -> Unit
                is NoteEditUiState.Editing -> EditingState(
                    uiState = state,
                    onContentChange = onContentChange,
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text("Go back")
        }
    }
}

@Composable
private fun EditingState(
    uiState: NoteEditUiState.Editing,
    onContentChange: (TextFieldValue) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        OutlinedTextField(
            value = uiState.value,
            onValueChange = onContentChange,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
            ),
            singleLine = false,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
            ),
        )
    }
}

@Composable
private fun UnsavedChangesDialog(
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Unsaved changes")
        },
        text = {
            Text("You have unsaved changes. Are you sure you want to discard them?")
        },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Suppress("DEPRECATION")
@Composable
private fun FormattingToolbar(
    onApplyHeading: (Int) -> Unit,
    onApplyBold: () -> Unit,
    onApplyItalic: () -> Unit,
    onApplySrc: () -> Unit,
    onApplyUnorderedList: () -> Unit,
    onApplyOrderedList: () -> Unit,
    onIncreaseIndentation: () -> Unit,
    onDecreaseIndentation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toolbarScroll = rememberScrollState()
    val iconTint = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .horizontalScroll(toolbarScroll),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarTextButton(text = "H1", onClick = { onApplyHeading(1) })
        ToolbarTextButton(text = "H2", onClick = { onApplyHeading(2) })
        ToolbarTextButton(text = "H3", onClick = { onApplyHeading(3) })
        ToolbarDivider()
        ToolbarIconButton(
            icon = Icons.Filled.FormatBold,
            contentDescription = "Bold",
            tint = iconTint,
            onClick = onApplyBold,
        )
        ToolbarIconButton(
            icon = Icons.Filled.FormatItalic,
            contentDescription = "Italic",
            tint = iconTint,
            onClick = onApplyItalic,
        )
        ToolbarDivider()
        ToolbarIconButton(
            icon = Icons.Filled.FormatListBulleted,
            contentDescription = "Bullet list",
            tint = iconTint,
            onClick = onApplyUnorderedList,
        )
        ToolbarIconButton(
            icon = Icons.Filled.FormatListNumbered,
            contentDescription = "Numbered list",
            tint = iconTint,
            onClick = onApplyOrderedList,
        )
        ToolbarDivider()
        ToolbarIconButton(
            icon = Icons.Filled.FormatIndentDecrease,
            contentDescription = "Decrease indent",
            tint = iconTint,
            onClick = onDecreaseIndentation,
        )
        ToolbarIconButton(
            icon = Icons.Filled.FormatIndentIncrease,
            contentDescription = "Increase indent",
            tint = iconTint,
            onClick = onIncreaseIndentation,
        )
        ToolbarDivider()
        ToolbarIconButton(
            icon = Icons.Filled.Code,
            contentDescription = "Source block",
            tint = iconTint,
            onClick = onApplySrc,
        )
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    onClick: () -> Unit,
) {
    ToolbarButtonContainer(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ToolbarTextButton(
    text: String,
    onClick: () -> Unit,
) {
    ToolbarButtonContainer(onClick = onClick) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ToolbarButtonContainer(
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true, radius = 18.dp),
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun ToolbarDivider() {
    Spacer(
        modifier = Modifier
            .height(20.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
    )
}

@Suppress("DEPRECATION")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NoteEditTopAppBar(
    title: String,
    fileName: String?,
    isEditing: Boolean,
    isSaving: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onSave: () -> Unit,
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        navigationIcon = {
            IconButton(
                onClick = onBack,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Navigate back",
                )
            }
        },
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!fileName.isNullOrBlank()) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        actions = {
            IconButton(
                onClick = onUndo,
                enabled = isEditing && !isSaving,
            ) {
                Icon(
                    imageVector = Icons.Filled.Undo,
                    contentDescription = "Undo",
                )
            }
            IconButton(
                onClick = onSave,
                enabled = isEditing && !isSaving,
            ) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = "Save",
                )
            }
        },
    )
}

@Preview
@Composable
private fun NoteEditScreenPreview() {
    LinkLetAppTheme {
        NoteEditScreen(
            state = NoteEditUiState.Editing(
                value = TextFieldValue("* Sample Note\n\nThis is a sample org-mode note with some content."),
                fileName = "sample_note.org",
            ),
            fileName = "sample_note.org",
            onContentChange = {},
            onSave = {},
            onBack = {},
            onApplyHeading = {},
            onApplyBold = {},
            onApplyItalic = {},
            onApplySrc = {},
            onApplyUnorderedList = {},
            onApplyOrderedList = {},
            onIncreaseIndentation = {},
            onDecreaseIndentation = {},
            onUndo = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
