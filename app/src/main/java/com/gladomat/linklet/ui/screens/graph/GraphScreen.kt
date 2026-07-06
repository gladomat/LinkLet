package com.gladomat.linklet.ui.screens.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gladomat.linklet.data.graph.Vector2
import com.gladomat.linklet.data.model.NoteIndexEntry
import com.gladomat.linklet.ui.theme.LinkLetAppTheme
import com.gladomat.linklet.viewmodel.graph.CameraFocusRequest
import com.gladomat.linklet.viewmodel.graph.GraphUiState
import com.gladomat.linklet.viewmodel.graph.GraphViewModel
import kotlin.math.ln

@Composable
fun GraphRoute(
    onOpenNote: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: GraphViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GraphScreen(
        uiState = uiState,
        isLocalGraph = viewModel.isLocalGraph,
        onNodeTapped = { path -> viewModel.onNodeTapped(path, onOpenNote) },
        onEmptySpaceTapped = viewModel::onEmptySpaceTapped,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onSearchResultSelected = viewModel::onSearchResultSelected,
        onCameraFocusHandled = viewModel::onCameraFocusHandled,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(
    uiState: GraphUiState,
    isLocalGraph: Boolean,
    onNodeTapped: (String) -> Unit,
    onEmptySpaceTapped: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchResultSelected: (String) -> Unit,
    onCameraFocusHandled: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isLocalGraph) "Local Graph" else "Note Graph") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.isLoading && uiState.nodes.isEmpty() -> LoadingGraphState()
                uiState.isEmpty -> EmptyGraphState()
                uiState.nodes.size == 1 -> TrivialGraphState(title = uiState.nodes.first().title)
                else -> GraphCanvas(
                    uiState = uiState,
                    onNodeTapped = onNodeTapped,
                    onEmptySpaceTapped = onEmptySpaceTapped,
                    onCameraFocusHandled = onCameraFocusHandled,
                )
            }
            if (uiState.nodes.size > 1) {
                // Recompute only when the node set or query actually changes - uiState.positions
                // (hence uiState itself) updates every ~16ms while the layout is settling, and
                // searchResults must not re-scan the note list on every one of those ticks.
                val searchResults = remember(uiState.nodes, uiState.searchQuery) { uiState.searchResults }
                GraphSearchBar(
                    query = uiState.searchQuery,
                    results = searchResults,
                    onQueryChange = onSearchQueryChange,
                    onResultSelected = onSearchResultSelected,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun GraphCanvas(
    uiState: GraphUiState,
    onNodeTapped: (String) -> Unit,
    onEmptySpaceTapped: () -> Unit,
    onCameraFocusHandled: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val positions = uiState.positions
    val selectedPath = uiState.selectedPath

    val degreeByPath = remember(uiState.edges) {
        val counts = HashMap<String, Int>()
        uiState.edges.forEach { edge ->
            counts[edge.source] = (counts[edge.source] ?: 0) + 1
            counts[edge.target] = (counts[edge.target] ?: 0) + 1
        }
        counts
    }
    // First-order (directly connected) notes of the selected node get a highlight treatment.
    // Second-order is deliberately not surfaced here - on a real vault it lights up so much of
    // the graph that it stops meaning anything and just makes the view harder to read.
    val firstOrderPaths = remember(uiState.edges, selectedPath) {
        if (selectedPath == null) {
            emptySet<String>()
        } else {
            val adjacency = HashMap<String, MutableSet<String>>()
            uiState.edges.forEach { edge ->
                adjacency.getOrPut(edge.source) { mutableSetOf() }.add(edge.target)
                adjacency.getOrPut(edge.target) { mutableSetOf() }.add(edge.source)
            }
            adjacency[selectedPath].orEmpty().toSet()
        }
    }
    val highlightedPaths = firstOrderPaths

    // Selecting a search result (Design decision 10) pans/zooms the camera to that node.
    LaunchedEffect(uiState.cameraFocusRequest) {
        val request = uiState.cameraFocusRequest ?: return@LaunchedEffect
        val target = positions[request.path]
        if (target != null && canvasSize.width > 0 && canvasSize.height > 0) {
            val focusedScale = scale.coerceAtLeast(FOCUS_MIN_SCALE)
            scale = focusedScale
            panOffset = Offset(
                canvasSize.width / 2f - target.x * focusedScale,
                canvasSize.height / 2f - target.y * focusedScale,
            )
        }
        onCameraFocusHandled()
    }

    // Tap hit-testing reads these via rememberUpdatedState rather than as pointerInput keys -
    // `positions` changes every ~16ms while the layout is settling, and keying pointerInput on
    // it would cancel and relaunch detectTapGestures on every tick, discarding Compose's
    // gesture-detector/pointer-tracking state dozens of times a second.
    val latestPositions by rememberUpdatedState(positions)
    val latestScale by rememberUpdatedState(scale)
    val latestPanOffset by rememberUpdatedState(panOffset)
    val latestSelectedPath by rememberUpdatedState(selectedPath)

    val edgeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val highlightedEdgeColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
    val nodeColor = MaterialTheme.colorScheme.primary
    val pendingNodeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val dimmedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val selectedColor = MaterialTheme.colorScheme.secondary
    val firstOrderColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onBackground.toArgb()

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    val newScale = (oldScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    // Zoom must pivot on the gesture's centroid (pinch focal point), not the
                    // canvas origin - otherwise every pinch also drags the graph towards
                    // whatever graph-space point happens to sit at screen (0, 0), which reads as
                    // the view sliding around instead of zooming in place like a photo. Using
                    // newScale/oldScale (not the raw `zoom` factor) keeps the focal point fixed
                    // even once scale saturates at MIN_SCALE/MAX_SCALE and further pinching would
                    // otherwise have no scale effect but would still drift the pan.
                    val effectiveZoom = newScale / oldScale
                    panOffset = centroid + (panOffset - centroid) * effectiveZoom + pan
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    // Hit-testing happens entirely in screen space, matching how nodes are
                    // drawn: node radius is a fixed screen size regardless of zoom (see
                    // BASE_NODE_RADIUS_DP below), so the tappable area must be too - comparing
                    // in graph space (dividing the tap by `scale`) would shrink the effective
                    // tap target as the user zooms out, the opposite of what a bigger selected
                    // hit-radius is supposed to achieve (Design decision 7).
                    var nearestPath: String? = null
                    var nearestDistSq = Float.MAX_VALUE
                    latestPositions.forEach { (path, pos) ->
                        val screenPos = toScreen(pos, latestScale, latestPanOffset)
                        val dx = screenPos.x - tapOffset.x
                        val dy = screenPos.y - tapOffset.y
                        val distSq = dx * dx + dy * dy
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq
                            nearestPath = path
                        }
                    }
                    val allowedRadius = if (nearestPath == latestSelectedPath) {
                        HIT_TEST_RADIUS_PX * SELECTED_HIT_RADIUS_MULTIPLIER
                    } else {
                        HIT_TEST_RADIUS_PX
                    }
                    val hitPath = nearestPath?.takeIf { nearestDistSq <= allowedRadius * allowedRadius }
                    if (hitPath != null) onNodeTapped(hitPath) else onEmptySpaceTapped()
                }
            },
    ) {
        // One batched Path / single drawPath call for every edge, not one drawLine per edge
        // (Design decision 14) - Barnes-Hut only bounds the layout engine's cost, not render cost.
        // Split into two batches (regular vs. highlighted) rather than one drawPath call per
        // edge, so highlighting a selection still costs exactly two draw calls, not one per edge.
        val edgePath = Path()
        val highlightEdgePath = Path()
        uiState.edges.forEach { edge ->
            val sourcePos = positions[edge.source] ?: return@forEach
            val targetPos = positions[edge.target] ?: return@forEach
            val start = toScreen(sourcePos, scale, panOffset)
            val end = toScreen(targetPos, scale, panOffset)
            // Only edges incident to the selected node itself - an edge between two of its
            // first-order notes (not touching the selection) stays unhighlighted, same as the
            // second-order nodes it connects.
            val edgeIsHighlighted = selectedPath != null &&
                (edge.source == selectedPath || edge.target == selectedPath)
            val targetPathObj = if (edgeIsHighlighted) highlightEdgePath else edgePath
            targetPathObj.moveTo(start.x, start.y)
            targetPathObj.lineTo(end.x, end.y)
        }
        drawPath(edgePath, color = edgeColor, style = Stroke(width = 1.dp.toPx()))
        drawPath(highlightEdgePath, color = highlightedEdgeColor, style = Stroke(width = 2.dp.toPx()))

        val labelLegible = scale > LABEL_LEGIBLE_SCALE_THRESHOLD
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor
        }

        uiState.nodes.forEach { node ->
            val path = node.id.path
            val graphPos = positions[path] ?: return@forEach
            val screenPos = toScreen(graphPos, scale, panOffset)
            val isSelected = path == selectedPath
            val isFirstOrder = path in firstOrderPaths
            val isDimmed = selectedPath != null && !isSelected && path !in highlightedPaths
            val degree = degreeByPath[path] ?: 0
            val baseRadius = (BASE_NODE_RADIUS_DP + DEGREE_RADIUS_SCALE_DP * ln((degree + 1).toFloat())).dp.toPx()
            val radius = when {
                isSelected -> baseRadius * SELECTED_HIT_RADIUS_MULTIPLIER
                isFirstOrder -> baseRadius * FIRST_ORDER_RADIUS_MULTIPLIER
                else -> baseRadius
            }
            val color = when {
                !node.linksReady -> pendingNodeColor // Design decision 11: pending, not a confirmed orphan
                isSelected -> selectedColor
                isFirstOrder -> firstOrderColor
                isDimmed -> dimmedColor
                else -> nodeColor
            }
            drawCircle(color = color, radius = radius, center = screenPos)

            // First-order connections always show their label when a selection is active,
            // regardless of zoom - the whole point is to let you read which notes are connected,
            // not just see a colored dot.
            if (isSelected || isFirstOrder || labelLegible) {
                textPaint.textSize = (if (isSelected) SELECTED_LABEL_SP else LABEL_SP).sp.toPx()
                textPaint.alpha = if (isDimmed) DIMMED_LABEL_ALPHA else OPAQUE_ALPHA
                drawContext.canvas.nativeCanvas.drawText(
                    node.title,
                    screenPos.x + radius + LABEL_OFFSET_DP.dp.toPx(),
                    screenPos.y,
                    textPaint,
                )
            }
        }
    }
}

private fun toScreen(graphPos: Vector2, scale: Float, panOffset: Offset): Offset =
    Offset(graphPos.x * scale + panOffset.x, graphPos.y * scale + panOffset.y)

@Composable
private fun GraphSearchBar(
    query: String,
    results: List<NoteIndexEntry>,
    onQueryChange: (String) -> Unit,
    onResultSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            tonalElevation = 3.dp,
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search notes") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        if (results.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(results, key = { it.id.path }) { result ->
                        Text(
                            text = result.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResultSelected(result.id.path) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingGraphState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyGraphState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No notes yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrivialGraphState(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No connections yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private const val MIN_SCALE = 0.1f
private const val MAX_SCALE = 8f
private const val FOCUS_MIN_SCALE = 1f
private const val HIT_TEST_RADIUS_PX = 32f
private const val SELECTED_HIT_RADIUS_MULTIPLIER = 1.8f
private const val FIRST_ORDER_RADIUS_MULTIPLIER = 1.3f
private const val LABEL_LEGIBLE_SCALE_THRESHOLD = 0.6f
private const val BASE_NODE_RADIUS_DP = 6f
private const val DEGREE_RADIUS_SCALE_DP = 3f
private const val LABEL_SP = 12f
private const val SELECTED_LABEL_SP = 16f
private const val LABEL_OFFSET_DP = 4f
private const val DIMMED_LABEL_ALPHA = 110
private const val OPAQUE_ALPHA = 255

@Preview
@Composable
private fun GraphScreenPreview() {
    LinkLetAppTheme {
        Surface {
            GraphScreen(
                uiState = GraphUiState(
                    nodes = listOf(
                        NoteIndexEntry(
                            id = com.gladomat.linklet.data.model.NoteId("a.org"),
                            title = "Alpha",
                            fileTags = emptyList(),
                            deletedAt = null,
                            linksReady = true,
                        ),
                        NoteIndexEntry(
                            id = com.gladomat.linklet.data.model.NoteId("b.org"),
                            title = "Beta",
                            fileTags = emptyList(),
                            deletedAt = null,
                            linksReady = true,
                        ),
                    ),
                    edges = listOf(
                        com.gladomat.linklet.domain.repository.LinkEntityDto(
                            source = "a.org",
                            target = "b.org",
                            alias = null,
                            sourceTitle = "Alpha",
                        ),
                    ),
                    positions = mapOf(
                        "a.org" to Vector2(0f, 0f),
                        "b.org" to Vector2(150f, 80f),
                    ),
                ),
                isLocalGraph = false,
                onNodeTapped = {},
                onEmptySpaceTapped = {},
                onSearchQueryChange = {},
                onSearchResultSelected = {},
                onCameraFocusHandled = {},
                onNavigateBack = {},
            )
        }
    }
}
