package com.gladomat.linklet.data.sync.delta

data class DeltaResult(
    val changes: List<DeltaChange>,
    val validity: DeltaValidity,
    val serverTimeEpochMillis: Long?,
)
