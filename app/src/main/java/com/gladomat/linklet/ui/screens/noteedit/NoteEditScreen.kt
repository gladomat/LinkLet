package com.gladomat.linklet.ui.screens.noteedit

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    when (val current = state) {
        is NoteEditUiState.Saved -> LaunchedEffect(current) {
            onDone(current.path)
        }

        is NoteEditUiState.Error -> LaunchedEffect(current) {
            snackbarHostState.showSnackbar(current.message)
        }

        else -> Unit
    }

    Surface {
        NoteEditScreen(
            state = state,
            onContentChange = viewModel::updateContent,
            onSave = viewModel::save,
            onCancel = onNavigateBack,
            onBack = onNavigateBack,
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
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NoteEditScreen(
    state: NoteEditUiState,
    onContentChange: (TextFieldValue) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
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
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(text = "Edit Note", style = MaterialTheme.typography.titleLarge) },
            )
            SnackbarHost(hostState = snackbarHostState)
            when (state) {
                NoteEditUiState.Loading -> LoadingState()
                is NoteEditUiState.Error -> ErrorState(state.message, onBack)
                is NoteEditUiState.Saved -> Unit
            is NoteEditUiState.Editing -> EditingState(
                uiState = state,
                onContentChange = onContentChange,
                onSave = onSave,
                onCancel = onCancel,
                onApplyHeading = onApplyHeading,
                onApplyBold = onApplyBold,
                onApplyItalic = onApplyItalic,
                onApplySrc = onApplySrc,
                onApplyUnorderedList = onApplyUnorderedList,
                onApplyOrderedList = onApplyOrderedList,
                onIncreaseIndentation = onIncreaseIndentation,
                onDecreaseIndentation = onDecreaseIndentation,
                onUndo = onUndo,
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
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onApplyHeading: (Int) -> Unit,
    onApplyBold: () -> Unit,
    onApplyItalic: () -> Unit,
    onApplySrc: () -> Unit,
    onApplyUnorderedList: () -> Unit,
    onApplyOrderedList: () -> Unit,
    onIncreaseIndentation: () -> Unit,
    onDecreaseIndentation: () -> Unit,
    onUndo: () -> Unit,
) {
    val toolbarHeight = 64.dp
    val density = LocalDensity.current
    val view = LocalView.current
    var imeVisible by remember { mutableStateOf(false) }
    var imeBottomPx by remember { mutableStateOf(0) }

    DisposableEffect(view) {
        val listener = OnApplyWindowInsetsListener { _, insets ->
            val imeType = WindowInsetsCompat.Type.ime()
            imeVisible = insets.isVisible(imeType)
            imeBottomPx = insets.getInsets(imeType).bottom
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(view, listener)
        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
        }
    }

    val imeBottom = with(density) { imeBottomPx.toDp() }
    val bottomPadding = if (imeVisible) toolbarHeight else 24.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(bottom = bottomPadding)
                .imePadding(),
        ) {
            val scrollState = rememberScrollState()
            OutlinedTextField(
                value = uiState.value,
                onValueChange = onContentChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = false,
                minLines = 8,
            )
            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSaving,
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onSave,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(text = "Save")
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(visible = imeVisible) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = imeBottom)
                        .height(toolbarHeight),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 6.dp,
                    shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
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
                        onUndo = onUndo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

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
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toolbarScroll = rememberScrollState()
    val iconTint = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .horizontalScroll(toolbarScroll),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarIconButton(
            icon = Icons.Filled.Undo,
            contentDescription = "Undo changes",
            tint = iconTint,
            onClick = onUndo,
        )
        ToolbarDivider()
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
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
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
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun ToolbarDivider() {
    Spacer(
        modifier = Modifier
            .height(24.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
    )
}

@Preview
@Composable
private fun NoteEditScreenPreview() {
    LinkLetAppTheme {
        NoteEditScreen(
            state = NoteEditUiState.Editing(value = TextFieldValue("Sample")),
            onContentChange = {},
            onSave = {},
            onCancel = {},
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
