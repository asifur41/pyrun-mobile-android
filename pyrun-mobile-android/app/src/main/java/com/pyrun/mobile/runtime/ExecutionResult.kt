package com.pyrun.mobile.runtime

enum class ExecutionStatus { SUCCESS, ERROR, TIMEOUT, MEMORY, CANCELLED, CRASHED }

data class ExecutionResult(
    val status: ExecutionStatus,
    val exitCode: Int,
    val durationMs: Long
)
