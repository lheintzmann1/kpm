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

    @OptIn(ExperimentalPathApi::class)
    suspend fun garbageCollect(): KResult<Unit> = withContext(Dispatchers.IO) {
        try {
            Logger.info("Starting garbage collection...")

            // Get all store paths
            val allStorePaths = storePath.listDirectoryEntries().filter { it.isDirectory() }

            // Get all reachable paths from GC roots
            val reachablePaths = mutableSetOf<Path>()
            gcRootsPath.listDirectoryEntries().forEach { gcRootDir ->
                if (gcRootDir.isDirectory()) {
                    gcRootDir.listDirectoryEntries().forEach { link ->
                        try {
                            val target = if (link.isSymbolicLink()) {
                                Files.readSymbolicLink(link)
                            } else {
                                Path.of(link.readText())
                            }
                            reachablePaths.add(target.parent) // Add the hash directory
                        } catch (e: Exception) {
                            Logger.debug("Failed to read GC root link: ${e.message}")
                        }
                    }
                }
            }

            // Remove unreachable paths
            val removedCount = allStorePaths.count { storePath ->
                if (storePath !in reachablePaths) {
                    try {
                        storePath.deleteRecursively()
                        Logger.debug("Removed unreachable store path: $storePath")
                        true
                    } catch (e: Exception) {
                        Logger.warning("Failed to remove store path $storePath: ${e.message}")
                        false
                    }
                } else {
                    false
                }
            }

            Logger.success("Garbage collection completed. Removed $removedCount unreachable dependencies.")
            KResult.Success(Unit)
        } catch (e: Exception) {
            KResult.Error("Garbage collection failed: ${e.message}", e)
        }
    }

    fun getStorePath(): Path = storePath
    fun getGCRootsPath(): Path = gcRootsPath
}