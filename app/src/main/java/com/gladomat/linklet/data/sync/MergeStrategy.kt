package com.gladomat.linklet.data.sync

import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch
import javax.inject.Inject

sealed class MergeResult {
    data class Success(val mergedContent: String) : MergeResult()
    object Conflict : MergeResult()
}

/**
 * Represents an edit to a text: replacement of base[start..end) with replacement string
 */
private data class TextEdit(
    val start: Int,      // offset in base text
    val end: Int,        // offset in base text (exclusive)
    val replacement: String
)

class MergeStrategy @Inject constructor() {

    private val dmp = DiffMatchPatch()

    fun merge(local: String, remote: String, base: String): MergeResult {
        // If local and remote are identical, no merge needed
        if (local == remote) {
            return MergeResult.Success(local)
        }
        
        // If local unchanged, use remote
        if (local == base) {
            return MergeResult.Success(remote)
        }
        
        // If remote unchanged, use local
        if (remote == base) {
            return MergeResult.Success(local)
        }
        
        // Both changed - compute edit spans and check for conflicts
        val localEdits = computeEdits(base, local)
        val remoteEdits = computeEdits(base, remote)
        
        // Check for overlapping edits (conflicts)
        if (hasConflict(localEdits, remoteEdits)) {
            return MergeResult.Conflict
        }
        
        // No conflicts - apply both sets of edits
        val merged = applyNonConflictingEdits(base, localEdits, remoteEdits)
        return MergeResult.Success(merged)
    }
    
    /**
     * Convert DiffMatchPatch output into clean, contiguous TextEdit spans
     */
    private fun computeEdits(base: String, changed: String): List<TextEdit> {
        val diffs = dmp.diffMain(base, changed)
        dmp.diffCleanupSemantic(diffs)
        
        val edits = mutableListOf<TextEdit>()
        var basePos = 0
        
        var currentStart: Int? = null
        val currentBaseText = StringBuilder()
        val currentReplacement = StringBuilder()
        
        fun flushEdit() {
            // Flush if there's any edit (DELETE, INSERT, or both)
            if (currentStart != null && (currentBaseText.isNotEmpty() || currentReplacement.isNotEmpty())) {
                val start = currentStart!!
                val end = start + currentBaseText.length
                edits += TextEdit(start, end, currentReplacement.toString())
                currentStart =  null
                currentBaseText.clear()
                currentReplacement.clear()
            }
        }
        
        for (diff in diffs) {
            when (diff.operation) {
                DiffMatchPatch.Operation.EQUAL -> {
                    flushEdit()
                    basePos += diff.text.length
                }
                DiffMatchPatch.Operation.DELETE -> {
                    if (currentStart == null) currentStart = basePos
                    currentBaseText.append(diff.text)
                    basePos += diff.text.length
                }
                DiffMatchPatch.Operation.INSERT -> {
                    if (currentStart == null) currentStart = basePos
                    currentReplacement.append(diff.text)
                }
            }
        }
        flushEdit()
        return edits
    }
    
    /**
     * Check if two edit spans overlap in the base text
     */
    private fun overlap(a: TextEdit, b: TextEdit): Boolean {
        return a.start < b.end && b.start < a.end
    }
    
    /**
     * Detect if local and remote edits conflict (overlap with different replacements)
     */
    private fun hasConflict(localEdits: List<TextEdit>, remoteEdits: List<TextEdit>): Boolean {
        for (local in localEdits) {
            for (remote in remoteEdits) {
                val rangesOverlap = overlap(local, remote)
                
                // Also check for zero-length inserts at the same position
                val bothInsertsAtSamePos = 
                    local.start == local.end &&
                    remote.start == remote.end &&
                    local.start == remote.start
                
                if ((rangesOverlap || bothInsertsAtSamePos) && local.replacement != remote.replacement) {
                    return true
                }
            }
        }
        return false
    }
    
    /**
     * Apply non-conflicting edits from both local and remote
     */
    private fun applyNonConflictingEdits(base: String, localEdits: List<TextEdit>, remoteEdits: List<TextEdit>): String {
        // Merge both edit lists and sort by position
        // Only remove truly identical edits (same range AND same replacement)
        val allEdits = (localEdits + remoteEdits)
            .distinctBy { Triple(it.start, it.end, it.replacement) }
            .sortedBy { it.start }
        
        val result = StringBuilder()
        var pos = 0
        
        for (edit in allEdits) {
            // Append unchanged text before this edit
            result.append(base.substring(pos, edit.start))
            // Append the replacement text
            result.append(edit.replacement)
            pos = edit.end
        }
        
        // Append remaining text
        result.append(base.substring(pos))
        
        return result.toString()
    }
}
