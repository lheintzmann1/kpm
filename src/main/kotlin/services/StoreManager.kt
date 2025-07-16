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
import kpm.utils.FileUtils
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.*
import kpm.core.Constants as Consts

class StoreManager {
    private val storePath: Path = Consts.KPM_STORE
    private val gcRootsPath: Path = Consts.KPM_GCROOTS
    private val profilesPath: Path = Consts.KPM_PROFILES

    init {
        ensureStoreDirectories()
    }

    /**
     * Ensures that the necessary directories for the store, GC roots, and profiles exist.
     * If they do not exist, they are created.
     */
    private fun ensureStoreDirectories() {
        listOf(storePath, gcRootsPath, profilesPath).forEach { path ->
            if (!path.exists()) {
                FileUtils.ensureDirectoryExists(path)
            }
        }
    }

    /**
     * Adds a file to the store, moving it to a subdirectory based on its hash.
     * If the file already exists in the store, it returns the existing path.
     *
     * @param filePath The path of the file to add.
     * @param hash The hash of the file used for naming in the store.
     * @return KResult containing the path in the store or an error message.
     */
    suspend fun addToStore(filePath: Path, hash: String): KResult<Path> = withContext(Dispatchers.IO) {
        try {
            val storeSubPath = storePath.resolve(hash)

            // If already exists, return existing path
            if (storeSubPath.exists()) {
                Logger.debug("Dependency already exists in store: $storeSubPath")
                // Clean up temporary file
                if (filePath.exists()) {
                    Files.delete(filePath)
                }
                return@withContext KResult.Success(storeSubPath)
            }

            // Create store directory for this hash
            storeSubPath.createDirectories()

            // Move the file to store
            val finalPath = storeSubPath.resolve(filePath.fileName)
            Files.move(filePath, finalPath, StandardCopyOption.REPLACE_EXISTING)

            // Make it read-only (like Nix)
            readOnly(finalPath, true)

            Logger.debug("Added to store: $finalPath")
            KResult.Success(finalPath)
        } catch (e: Exception) {
            Logger.error("Failed to add file to store", e)
            KResult.Error("Failed to add file to store: ${e.message}", e)
        }
    }

    /**
     * Checks if a given store path exists in the store.
     *
     * @param storePath The path to check in the store.
     * @return Boolean indicating whether the path exists in the store.
     */
    fun isInStore(storePath: String): Boolean {
        return Path.of(storePath).exists()
    }

    /**
     * Creates GC roots for the specified store paths.
     * This method creates symbolic links in the GC roots directory pointing to the store paths.
     * If symbolic links are not supported, it creates reference files instead.
     *
     * @param storePaths List of store paths to create GC roots for.
     * @return KResult indicating success or failure of the operation.
     */
    suspend fun createGCRoots(storePaths: List<String>): KResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentProject = System.getProperty("user.dir")
            val projectHash = MessageDigest.getInstance("SHA-256")
                .digest(currentProject.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(8)

            val gcRootDir = gcRootsPath.resolve("project-$projectHash")
            if (!gcRootDir.exists()) {
                gcRootDir.createDirectories()
            }

            // Clear existing GC roots for this project
            clearGCRoots(gcRootDir)

            // Create symbolic links to store paths (GC roots)
            storePaths.forEachIndexed { index, storePath ->
                val linkPath = gcRootDir.resolve("dep-$index")

                try {
                    Files.createSymbolicLink(linkPath, Path.of(storePath))
                    Logger.debug("Created GC root: $linkPath -> $storePath")
                } catch (e: Exception) {
                    Logger.warning("Failed to create symbolic link for GC root: ${e.message}")
                    // On systems that don't support symlinks, create a reference file
                    linkPath.writeText(storePath)
                }
            }

            KResult.Success(Unit)
        } catch (e: Exception) {
            KResult.Error("Failed to create GC roots: ${e.message}", e)
        }
    }

    /**
     * Clears all GC roots for the current project.
     * This method deletes all symbolic links and reference files in the GC roots directory.
     *
     * @param gcRootDir The directory containing the GC roots to clear.
     */
    private fun clearGCRoots(gcRootDir: Path) {
        try {
            if (gcRootDir.exists()) {
                Files.walk(gcRootDir)
                    .sorted(Comparator.reverseOrder())
                    .filter { it != gcRootDir }
                    .forEach { path ->
                        try {
                            Files.delete(path)
                        } catch (e: Exception) {
                            Logger.warning("Failed to delete GC root: $path - ${e.message}")
                        }
                    }
            }
        } catch (e: Exception) {
            Logger.warning("Failed to clear GC roots: ${e.message}")
        }
    }

    /**
     * Performs garbage collection by removing unreferenced dependencies from the store.
     * It identifies all referenced paths from GC roots, compares them with all store paths,
     * and deletes those that are not referenced.
     *
     * @return KResult containing the number of deleted dependencies or an error message.
     */
    suspend fun garbageCollect(): KResult<Int> = withContext(Dispatchers.IO) {
        try {
            Logger.info("Starting garbage collection...")

            // Get all referenced store paths from GC roots
            val referencedPaths = getAllReferencedPaths()

            // Get all store paths
            val allStorePaths = getAllStorePaths()

            // Find unreferenced paths
            val unreferencedPaths = allStorePaths - referencedPaths

            Logger.info("Found ${unreferencedPaths.size} unreferenced dependencies to clean up")

            // Delete unreferenced paths
            var deletedCount = 0
            for (path in unreferencedPaths) {
                try {
                    Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .forEach { filePath ->
                            try {
                                readOnly(filePath, false)
                                Files.delete(filePath)
                            } catch (e: Exception) {
                                Logger.warning("Failed to delete file: $filePath - ${e.message}")
                            }
                        }
                    deletedCount++
                    Logger.debug("Deleted unreferenced dependency: $path")
                } catch (e: Exception) {
                    Logger.warning("Failed to delete unreferenced dependency: $path - ${e.message}")
                }
            }

            Logger.success("Garbage collection completed: $deletedCount dependencies removed")
            KResult.Success(deletedCount)
        } catch (e: Exception) {
            KResult.Error("Garbage collection failed: ${e.message}", e)
        }
    }

    /**
     * Sets the read-only status for a file or directory.
     * This is used to mimic the behavior of Nix store, where files are immutable.
     *
     * @param path The path to set as read-only or writable.
     * @param readOnly If true, sets the file as read-only; otherwise, makes it writable.
     */
    private fun readOnly(path: Path, readOnly: Boolean) {
        try {
            if (readOnly) {
                path.toFile().setReadOnly()
            } else {
                path.toFile().setWritable(true)
            }
        } catch (e: Exception) {
            Logger.warning("Failed to set read-only status for $path: ${e.message}")
        }
    }

    /**
     * Retrieves all referenced paths from the GC roots directory.
     * It collects paths from symbolic links or reference files in the GC roots directory.
     *
     * @return Set of Paths that are referenced by GC roots.
     */
    private fun getAllReferencedPaths(): Set<Path> {
        val referencedPaths = mutableSetOf<Path>()

        try {
            if (gcRootsPath.exists()) {
                Files.walk(gcRootsPath)
                    .filter { it.isRegularFile() || Files.isSymbolicLink(it) }
                    .forEach { gcRoot ->
                        try {
                            val targetPath = if (Files.isSymbolicLink(gcRoot)) {
                                Files.readSymbolicLink(gcRoot)
                            } else {
                                // For reference files (on systems without symlink support)
                                Path.of(gcRoot.readText().trim())
                            }

                            if (targetPath.exists()) {
                                referencedPaths.add(targetPath.parent)
                            }
                        } catch (e: Exception) {
                            Logger.warning("Failed to read GC root: $gcRoot - ${e.message}")
                        }
                    }
            }
        } catch (e: Exception) {
            Logger.warning("Failed to collect referenced paths: ${e.message}")
        }

        return referencedPaths
    }

    /**
     * Retrieves all store paths from the store directory.
     * It collects all subdirectories in the store directory, which represent stored dependencies.
     *
     * @return Set of Paths representing all stored dependencies.
     */
    private fun getAllStorePaths(): Set<Path> {
        val storePaths = mutableSetOf<Path>()

        try {
            if (storePath.exists()) {
                Files.walk(storePath, 1)
                    .filter { it != storePath && it.isDirectory() }
                    .forEach { storePaths.add(it) }
            }
        } catch (e: Exception) {
            Logger.warning("Failed to collect store paths: ${e.message}")
        }

        return storePaths
    }
}