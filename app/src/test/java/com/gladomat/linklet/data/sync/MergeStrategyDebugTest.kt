package com.gladomat.linklet.data.sync

import org.junit.Test

class MergeStrategyDebugTest {
    
    private val mergeStrategy = MergeStrategy()
    
    @Test
    fun `debug non-overlapping edits`() {
        val base = "Line 1\nLine 2\nLine 3"
        val local = "Line 1\nLine 2 (Local)\nLine 3"
        val remote = "Line 1 (Remote)\nLine 2\nLine 3"
        
        println("Base: $base")
        println("Local: $local")
        println("Remote: $remote")
        
        val result = mergeStrategy.merge(local, remote, base)
        
        println("Result: $result")
        if (result is MergeResult.Success) {
            println("Merged: ${result.mergedContent}")
        }
    }
}
