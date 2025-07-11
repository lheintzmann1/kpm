package kpm.models

import kotlinx.serialization.Serializable

@Serializable
data class Manifest(
    val name: String,
    val version: String,
    val main: String,
    val dependencies: List<String>
)
