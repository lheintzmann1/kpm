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
import kpm.core.Logger
import kpm.utils.FileUtils
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.*
import kpm.core.Constants as Consts

class StoreManager {
    private val storePath: Path = Consts.KPM_HOME.resolve("store")
    private val gcRootsPath: Path = Consts.KPM_HOME.resolve("gcroots")
    private val profilesPath: Path = Consts.KPM_HOME.resolve("profiles")

    init {
        ensureStoreDirectories()
    }

    private fun ensureStoreDirectories() {
        listOf(storePath, gcRootsPath, profilesPath).forEach { path ->
            if (!path.exists()) {
                FileUtils.ensureDirectoryExists(path)
            }
        }
    }

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
            finalPath.toFile().setReadOnly()

            Logger.debug("Added to store: $finalPath")
            KResult.Success(finalPath)
        } catch (e: Exception) {
            Logger.error("Failed to add file to store", e)
            KResult.Error("Failed to add file to store: ${e.message}", e)
        }
    }

    fun isInStore(storePath: String): Boolean {
        return Path.of(storePath).exists()
    }

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

            // Create symbolic links to store paths (GC roots)
            storePaths.forEachIndexed { index, storePath ->
                val linkPath = gcRootDir.resolve("dep-$index")
                if (linkPath.exists()) {
                    Files.delete(linkPath)
                }

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
}