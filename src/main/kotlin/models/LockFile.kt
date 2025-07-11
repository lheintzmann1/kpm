package kpm.models

import kotlinx.serialization.Serializable

@Serializable
data class LockFile(
    val version: String,
    val dependencies: Map<String, DependencyEntry>
)