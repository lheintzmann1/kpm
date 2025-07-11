package kpm.models

import kotlinx.serialization.Serializable

@Serializable
data class DependencyEntry(
    val version: String,
    val hash: String,
    val storePath: String,
    val transitiveDependencies: List<String> = emptyList()
)