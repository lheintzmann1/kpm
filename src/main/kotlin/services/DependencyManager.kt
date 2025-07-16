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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kpm.core.KResult
import kpm.utils.Logger
import kpm.models.MavenCoordinate
import kpm.models.LockFile
import kpm.models.DependencyEntry
import kpm.utils.HashUtils
import java.nio.file.Paths
import kotlin.io.path.exists
import kpm.core.Constants as Consts

class DependencyManager {
    private val mavenDownloader = MavenDownloader()
    private val manifestParser = ManifestParser()
    private val storeManager = StoreManager()
    private val dependencyResolver = DependencyResolver()

    private val lockFilePath = Paths.get(Consts.MANIFEST_LOCK)

    /**
     * Installs the specified dependencies, resolving transitive dependencies and handling conflicts.
     *
     * @param dependencies List of dependency coordinates in Maven format (e.g., "groupId:artifactId:version").
     * @return KResult indicating success or failure.
     */
    suspend fun installDependencies(dependencies: List<String>): KResult<Unit> = withContext(Dispatchers.IO) {
        Logger.info("Installing ${dependencies.size} dependencies...")

        // Parse coordinates
        val coordinates = dependencies.mapNotNull { dep ->
            MavenCoordinate.fromString(dep)?.also { coord ->
                Logger.debug("Parsed dependency: $coord")
            } ?: run {
                Logger.error("Invalid dependency format: $dep")
                null
            }
        }

        if (coordinates.size != dependencies.size) {
            return@withContext KResult.Error("Some dependencies have invalid format")
        }

        // Check existing lock file
        val existingLockFile = if (lockFilePath.exists()) {
            manifestParser.parseLockFile(lockFilePath).onError { _, _ -> null }
        } else {
            KResult.Success(LockFile("1.0", emptyMap()))
        }

        val lockFileData = when (existingLockFile) {
            is KResult.Success -> existingLockFile.data
            is KResult.Error -> LockFile("1.0", emptyMap())
        }

        // Resolve all dependencies (including transitive)
        Logger.info("Resolving transitive dependencies...")
        val resolvedDependencies = dependencyResolver.resolveDependencies(coordinates)
        val allDependencies = when (resolvedDependencies) {
            is KResult.Success -> resolvedDependencies.data
            is KResult.Error -> {
                Logger.error("Failed to resolve dependencies: ${resolvedDependencies.message}")
                return@withContext resolvedDependencies
            }
        }

        // Check for conflicts
        val conflicts = dependencyResolver.detectConflicts(allDependencies)
        if (conflicts.isNotEmpty()) {
            Logger.warning("Dependency conflicts detected:")
            conflicts.forEach { conflict ->
                Logger.warning("  ${conflict.artifact}: ${conflict.versions.joinToString(", ")}")
            }
            Logger.warning("Using latest version for conflicting dependencies")
        }

        // Deduplicate dependencies (use latest version for conflicts)
        val uniqueDependencies = deduplicateDependencies(allDependencies)

        Logger.info("Installing ${uniqueDependencies.size} unique dependencies (including ${uniqueDependencies.size - coordinates.size} transitive)")

        // Install dependencies concurrently
        val newDependencies = mutableMapOf<String, DependencyEntry>()

        try {
            coroutineScope {
                uniqueDependencies.map { resolvedDep ->
                    async {
                        val coordString = resolvedDep.coordinate.toString()

                        // Check if already installed with same version
                        val existing = lockFileData.dependencies[coordString]
                        if (existing != null && storeManager.isInStore(existing.storePath)) {
                            Logger.debug("Dependency $coordString already installed at ${existing.storePath}")
                            return@async coordString to existing
                        }

                        // Download and install
                        val result = installSingleDependency(resolvedDep)
                        when (result) {
                            is KResult.Success -> {
                                val logPrefix = if (resolvedDep.depth == 0) "Installed" else "Installed (transitive)"
                                Logger.success("$logPrefix $coordString")
                                coordString to result.data
                            }
                            is KResult.Error -> {
                                Logger.error("Failed to install $coordString: ${result.message}")
                                throw RuntimeException("Failed to install $coordString: ${result.message}")
                            }
                        }
                    }
                }.forEach { deferred ->
                    val (coordString, entry) = deferred.await()
                    newDependencies[coordString] = entry
                }
            }
        } catch (e: Exception) {
            return@withContext KResult.Error("Failed to install dependencies", e)
        }

        // Update lock file
        val updatedLockFile = lockFileData.copy(
            dependencies = lockFileData.dependencies + newDependencies
        )

        manifestParser.writeLockFile(lockFilePath, updatedLockFile)
            .onError { message, cause ->
                Logger.error("Failed to update lock file: $message", cause)
                return@withContext KResult.Error("Failed to update lock file: $message", cause)
            }

        // Create GC roots for current project
        storeManager.createGCRoots(newDependencies.values.map { it.storePath })
            .onError { message, cause ->
                Logger.warning("Failed to create GC roots: $message")
            }

        // garbage collection
        storeManager.garbageCollect()
            .onError { message, cause ->
                Logger.warning("Failed to perform garbage collection: $message")
            }

        Logger.success("Successfully installed all dependencies!")

        KResult.Success(Unit)
    }

    /**
     * Deduplicates dependencies by grouping them by artifact and selecting the latest version.
     * This is a simple implementation that assumes version strings can be compared lexicographically.
     *
     * @param dependencies List of resolved dependencies.
     * @return List of deduplicated dependencies, sorted by depth.
     */
    private fun deduplicateDependencies(dependencies: List<DependencyResolver.ResolvedDependency>): List<DependencyResolver.ResolvedDependency> {
        val groupedByArtifact = dependencies.groupBy { "${it.coordinate.groupId}:${it.coordinate.artifactId}" }

        return groupedByArtifact.values.map { versions ->
            // Choose the latest version (simple string comparison for now)
            versions.maxByOrNull { it.coordinate.version } ?: versions.first()
        }.sortedBy { it.depth }
    }

    /**
     * Installs a single resolved dependency, downloading its JAR and storing it.
     *
     * @param resolvedDep The resolved dependency to install.
     * @return KResult containing the installed DependencyEntry or an error.
     */
    private suspend fun installSingleDependency(resolvedDep: DependencyResolver.ResolvedDependency): KResult<DependencyEntry> {
        val coordinate = resolvedDep.coordinate
        Logger.debug("Installing dependency: $coordinate")

        // Download JAR
        val jarResult = mavenDownloader.downloadJar(coordinate)
        val jarPath = when (jarResult) {
            is KResult.Success -> jarResult.data
            is KResult.Error -> return jarResult
        }

        // Calculate hash
        val hash = HashUtils.calculateFileHash(jarPath)

        // Move to store
        val storePath = storeManager.addToStore(jarPath, hash)
        when (storePath) {
            is KResult.Success -> {
                val transitiveDeps = resolvedDep.transitiveDependencies.map { it.toString() }
                val entry = DependencyEntry(
                    version = coordinate.version,
                    hash = hash,
                    storePath = storePath.data.toString(),
                    transitiveDependencies = transitiveDeps
                )
                return KResult.Success(entry)
            }
            is KResult.Error -> return storePath
        }
    }
}