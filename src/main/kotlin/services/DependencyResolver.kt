/*
Copyright 2025 Lucas HEINTZMANN

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package kpm.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kpm.core.KResult
import kpm.utils.Logger
import kpm.models.MavenCoordinate

class DependencyResolver {
    private val mavenDownloader = MavenDownloader()
    private val pomParser = POMParser()

    data class ResolvedDependency(
        val coordinate: MavenCoordinate,
        val transitiveDependencies: List<MavenCoordinate>,
        val depth: Int = 0
    )

    /**
     * Resolves the transitive dependencies for a list of root Maven coordinates.
     *
     * @param rootDependencies The initial list of Maven coordinates to resolve.
     * @param maxDepth The maximum depth to resolve dependencies (default is 10).
     * @return A KResult containing a list of resolved dependencies or an error message.
     */
    suspend fun resolveDependencies(
        rootDependencies: List<MavenCoordinate>,
        maxDepth: Int = 10
    ): KResult<List<ResolvedDependency>> = withContext(Dispatchers.IO) {

        Logger.info("Resolving transitive dependencies...")

        val resolvedDependencies = mutableMapOf<String, ResolvedDependency>()
        val visited = mutableSetOf<String>()
        val resolving = mutableSetOf<String>() // For cycle detection

        try {
            for (rootDep in rootDependencies) {
                resolveDependencyRecursive(
                    coordinate = rootDep,
                    depth = 0,
                    maxDepth = maxDepth,
                    resolved = resolvedDependencies,
                    visited = visited,
                    resolving = resolving
                ).onError { message, cause ->
                    return@withContext KResult.Error("Failed to resolve dependency ${rootDep}: $message", cause)
                }
            }

            // Sort by depth (dependencies first, then dependents)
            val sortedDependencies = resolvedDependencies.values.sortedBy { it.depth }

            Logger.success("Resolved ${sortedDependencies.size} total dependencies (including transitive)")
            KResult.Success(sortedDependencies)

        } catch (e: Exception) {
            KResult.Error("Dependency resolution failed: ${e.message}", e)
        }
    }

    /**
     * Recursively resolves a single Maven coordinate and its transitive dependencies.
     *
     * @param coordinate The Maven coordinate to resolve.
     * @param depth The current depth in the dependency tree.
     * @param maxDepth The maximum allowed depth for resolution.
     * @param resolved A mutable map to store resolved dependencies.
     * @param visited A mutable set to track visited coordinates (not used here, but can be useful).
     * @param resolving A mutable set to track currently resolving coordinates (for cycle detection).
     * @return A KResult indicating success or failure of the resolution.
     */
    private suspend fun resolveDependencyRecursive(
        coordinate: MavenCoordinate,
        depth: Int,
        maxDepth: Int,
        resolved: MutableMap<String, ResolvedDependency>,
        visited: MutableSet<String>,
        resolving: MutableSet<String>
    ): KResult<Unit> {

        val coordString = coordinate.toString()

        // Check if already resolved
        if (resolved.containsKey(coordString)) {
            return KResult.Success(Unit)
        }

        // Check for cycles
        if (resolving.contains(coordString)) {
            Logger.warning("Circular dependency detected: $coordString")
            return KResult.Success(Unit)
        }

        // Check max depth
        if (depth > maxDepth) {
            Logger.warning("Maximum dependency depth ($maxDepth) exceeded for $coordString")
            return KResult.Success(Unit)
        }

        // Mark as currently resolving
        resolving.add(coordString)

        Logger.debug("Resolving dependency: $coordString (depth: $depth)")

        // Download and parse POM
        val pomResult = mavenDownloader.downloadPom(coordinate)
        val transitiveDependencies = when (pomResult) {
            is KResult.Success -> {
                val parsedPOM = pomParser.parsePOM(pomResult.data)
                when (parsedPOM) {
                    is KResult.Success -> {
                        Logger.debug("Found ${parsedPOM.data.dependencies.size} dependencies for $coordString")
                        parsedPOM.data.dependencies
                    }
                    is KResult.Error -> {
                        Logger.warning("Failed to parse POM for $coordString: ${parsedPOM.message}")
                        emptyList()
                    }
                }
            }
            is KResult.Error -> {
                Logger.warning("Failed to download POM for $coordString: ${pomResult.message}")
                emptyList()
            }
        }

        // Add to resolved (even if no transitive dependencies)
        resolved[coordString] = ResolvedDependency(
            coordinate = coordinate,
            transitiveDependencies = transitiveDependencies,
            depth = depth
        )

        // Remove from resolving set
        resolving.remove(coordString)

        // Recursively resolve transitive dependencies
        for (transitiveDep in transitiveDependencies) {
            resolveDependencyRecursive(
                coordinate = transitiveDep,
                depth = depth + 1,
                maxDepth = maxDepth,
                resolved = resolved,
                visited = visited,
                resolving = resolving
            ).onError { message, cause ->
                Logger.warning("Failed to resolve transitive dependency ${transitiveDep}: $message")
                // Continue with other dependencies instead of failing completely
            }
        }

        return KResult.Success(Unit)
    }

    /**
     * Detects conflicts in resolved dependencies where multiple versions of the same artifact are present.
     *
     * @param dependencies The list of resolved dependencies to check for conflicts.
     * @return A list of DependencyConflict objects representing the conflicts found.
     */
    fun detectConflicts(dependencies: List<ResolvedDependency>): List<DependencyConflict> {
        val conflicts = mutableListOf<DependencyConflict>()
        val groupedByArtifact = dependencies.groupBy { "${it.coordinate.groupId}:${it.coordinate.artifactId}" }

        for ((artifact, versions) in groupedByArtifact) {
            if (versions.size > 1) {
                val versionList = versions.map { it.coordinate.version }.distinct()
                if (versionList.size > 1) {
                    conflicts.add(DependencyConflict(artifact, versionList))
                }
            }
        }

        return conflicts
    }

    /**
     * Represents a conflict where multiple versions of the same artifact are present.
     *
     * @param artifact The identifier of the artifact (groupId:artifactId).
     * @param versions The list of conflicting versions found for this artifact.
     */
    data class DependencyConflict(
        val artifact: String,
        val versions: List<String>
    )
}