package com.gladomat.linklet.data.sync.delta

enum class DeltaValidityState {
    VALID,
    SOFT_INVALID, // Delta might be stale, prefer full sweep
    HARD_INVALID, // Delta is unusable, must full sweep
}

data class DeltaValidity(
    val state: DeltaValidityState,
    val reason: String,
) {
    companion object {
        fun valid() = DeltaValidity(DeltaValidityState.VALID, "ok")
        fun softInvalid(reason: String) = DeltaValidity(DeltaValidityState.SOFT_INVALID, reason)
        fun hardInvalid(reason: String) = DeltaValidity(DeltaValidityState.HARD_INVALID, reason)
    }

    val isUsable: Boolean get() = state == DeltaValidityState.VALID
}
