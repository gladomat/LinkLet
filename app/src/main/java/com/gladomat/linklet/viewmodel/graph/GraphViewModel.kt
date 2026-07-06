package com.gladomat.linklet.viewmodel.graph

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.app.di.DefaultDispatcher
import com.gladomat.linklet.data.graph.ForceLayoutEngine
import com.gladomat.linklet.data.graph.Vector2
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.data.model.NoteIndexEntry
import com.gladomat.linklet.domain.repository.GraphPoint
import com.gladomat.linklet.domain.repository.GraphSnapshot
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.LinkEntityDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the graph view (docs/plans/2026-07-06-note-graph-view.md): one shared ViewModel for
 * both the global graph (no center note) and the local graph (N-hop neighborhood of a note),
 * distinguished only by whether [GraphArgs.CENTER] is present in the nav args - see Design
 * decision 5.
 */
@HiltViewModel
class GraphViewModel @Inject constructor(
    private val repository: INoteRepository,
    savedStateHandle: SavedStateHandle,
    @DefaultDispatcher private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val centerPath: String? = savedStateHandle.get<String>(GraphArgs.CENTER)?.takeIf { it.isNotBlank() }
    val isLocalGraph: Boolean get() = centerPath != null

    private val engine = ForceLayoutEngine()

    private val _uiState = MutableStateFlow(GraphUiState())
    val uiState: StateFlow<GraphUiState> = _uiState.asStateFlow()

    private var layoutJob: Job? = null

    // Barnes-Hut/spring math must not run on the main thread at real vault scale (500-2000
    // notes) - the tick loop and the flow collection both run on backgroundDispatcher, never
    // on viewModelScope's default (Main) dispatcher.
    private var lastNodeIdSet: Set<String> = emptySet()
    private var lastEdgePairSet: Set<Pair<String, String>> = emptySet()

    init {
        viewModelScope.launch(backgroundDispatcher) {
            repository.observeGraph(center = centerPath?.let(::NoteId), hopDepth = HOP_DEPTH)
                .collect { snapshot -> onSnapshot(snapshot) }
        }
    }

    private fun onSnapshot(snapshot: GraphSnapshot) {
        _uiState.update {
            it.copy(
                isLoading = false,
                nodes = snapshot.nodes,
                edges = snapshot.edges,
                selectedPath = it.selectedPath?.takeIf { path -> snapshot.nodes.any { node -> node.id.path == path } },
            )
        }

        // Design decision (Rendering & interaction): 0/1-node graphs skip the simulation
        // entirely and just show a plain empty/trivial state - no point animating one dot.
        if (snapshot.nodes.size <= 1) {
            layoutJob?.cancel()
            lastNodeIdSet = emptySet()
            lastEdgePairSet = emptySet()
            val singleNodePath = snapshot.nodes.singleOrNull()?.id?.path
            _uiState.update { state ->
                state.copy(
                    positions = when {
                        singleNodePath == null -> emptyMap()
                        else -> mapOf(singleNodePath to (cachedPosition(snapshot, singleNodePath) ?: Vector2.ZERO))
                    },
                )
            }
            return
        }

        val nodeIds = snapshot.nodes.map { it.id.path }
        val nodeIdSet = nodeIds.toHashSet()
        val edgePairs = snapshot.edges.map { it.source to it.target }
        val edgePairSet = edgePairs.toHashSet()

        // The graph's own settle-and-persist writes to graph_positions re-emit this same
        // observeGraph() flow (Room invalidation), which would otherwise restart the whole
        // simulation from iteration 0 forever - a snapshot whose node/edge set hasn't actually
        // changed just means "our own save landed" (or an unrelated note's title changed), not
        // "re-lay-out this graph." Only restart the tick loop when the topology itself changed.
        if (nodeIdSet == lastNodeIdSet && edgePairSet == lastEdgePairSet) {
            return
        }
        lastNodeIdSet = nodeIdSet
        lastEdgePairSet = edgePairSet

        layoutJob?.cancel()

        val cachedPositions = snapshot.cachedPositions.mapValues { (_, point) -> Vector2(point.x, point.y) }
        // Prefer this session's already-live positions over the (possibly older) DB cache, so a
        // snapshot update mid-simulation (e.g. a note edited elsewhere) doesn't discard layout
        // progress already made this session - only genuinely new nodes fall back to a fresh seed.
        val startingPositions = cachedPositions + _uiState.value.positions.filterKeys { it in nodeIdSet }

        layoutJob = viewModelScope.launch(backgroundDispatcher) {
            var layoutState = engine.seed(nodeIds, startingPositions)
            _uiState.update { it.copy(positions = layoutState.positions) }
            while (!engine.isConverged(layoutState)) {
                layoutState = engine.step(layoutState, nodeIds, edgePairs)
                _uiState.update { it.copy(positions = layoutState.positions) }
                delay(TICK_INTERVAL_MILLIS)
            }
            // Local (centered, N-hop) runs settle in their own small coordinate frame - saving
            // that back to the shared graph_positions cache would corrupt the global graph's
            // layout with an unrelated frame next time it's opened. Only the global run persists.
            if (!isLocalGraph) {
                repository.saveGraphPositions(layoutState.positions.mapValues { (_, v) -> GraphPoint(v.x, v.y) })
                    .onFailure { error ->
                        Log.w(TAG, "Failed to persist graph layout positions: ${error.message}", error)
                    }
            }
        }
    }

    private fun cachedPosition(snapshot: GraphSnapshot, path: String): Vector2? =
        snapshot.cachedPositions[path]?.let { Vector2(it.x, it.y) }

    /**
     * Two-tap select-then-open (Design decision 7): tap 1 on a node selects/enlarges it; tap 2
     * on that *same already-selected* node opens it. Tapping a different node just re-selects -
     * [onOpenNote] fires only on the second tap on the same node.
     */
    fun onNodeTapped(path: String, onOpenNote: (String) -> Unit) {
        if (_uiState.value.selectedPath == path) {
            onOpenNote(path)
        } else {
            _uiState.update { it.copy(selectedPath = path) }
        }
    }

    fun onEmptySpaceTapped() {
        _uiState.update { it.copy(selectedPath = null) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Selecting a search result puts that node in the same selected state a tap-1 would, and
     * asks the screen to pan/zoom the camera to it (Design decision 10) via [GraphUiState.cameraFocusRequest]
     * - GraphScreen consumes that request and calls [onCameraFocusHandled].
     */
    fun onSearchResultSelected(path: String) {
        _uiState.update {
            it.copy(
                selectedPath = path,
                searchQuery = "",
                cameraFocusRequest = CameraFocusRequest(path = path, requestId = it.cameraFocusRequest?.requestId?.plus(1) ?: 0L),
            )
        }
    }

    fun onCameraFocusHandled() {
        _uiState.update { it.copy(cameraFocusRequest = null) }
    }

    object GraphArgs {
        const val CENTER = "center"
    }

    companion object {
        private const val TAG = "GraphViewModel"
        private const val HOP_DEPTH = 2
        private const val TICK_INTERVAL_MILLIS = 16L
    }
}

/** A one-shot request for the screen to pan/zoom to [path]; [requestId] makes repeat requests for the same node distinct. */
data class CameraFocusRequest(val path: String, val requestId: Long)

data class GraphUiState(
    val nodes: List<NoteIndexEntry> = emptyList(),
    val edges: List<LinkEntityDto> = emptyList(),
    val positions: Map<String, Vector2> = emptyMap(),
    val selectedPath: String? = null,
    val searchQuery: String = "",
    val cameraFocusRequest: CameraFocusRequest? = null,
    // observeGraph() debounces against sync/index write bursts (docs/plans/2026-07-06-note-graph-view.md,
    // Design decision 12); opening the graph mid-burst can mean waiting out the whole burst
    // before the first snapshot lands. Without this flag, that wait looks identical to "confirmed
    // empty vault" (nodes = emptyList()) - isLoading distinguishes "no snapshot yet" from "the
    // vault genuinely has no notes."
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && nodes.isEmpty()

    val searchResults: List<NoteIndexEntry>
        get() = if (searchQuery.isBlank()) {
            emptyList()
        } else {
            nodes.filter { it.title.contains(searchQuery, ignoreCase = true) }.take(SEARCH_RESULT_LIMIT)
        }

    private companion object {
        const val SEARCH_RESULT_LIMIT = 20
    }
}
