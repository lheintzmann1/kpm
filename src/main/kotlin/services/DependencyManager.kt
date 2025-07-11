package kpm.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kpm.core.KResult
import kpm.core.Logger
import kpm.models.MavenCoordinate
import kpm.models.LockFile
import kpm.models.DependencyEntry
import kpm.utils.HashUtils
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.createDirectories
import kpm.core.Constants as Consts

class DependencyManager {
    private val mavenDownloader = MavenDownloader()
    private val manifestParser = ManifestParser()
    private val storeManager = StoreManager()

    private val lockFilePath = Paths.get(Consts.MANIFEST_LOCK)

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

        // Install dependencies concurrently
        val newDependencies = mutableMapOf<String, DependencyEntry>()

        try {
            coroutineScope {
                coordinates.map { coord ->
                    async {
                        val coordString = coord.toString()

                        // Check if already installed with same version
                        val existing = lockFileData.dependencies[coordString]
                        if (existing != null && storeManager.isInStore(existing.storePath)) {
                            Logger.debug("Dependency $coordString already installed at ${existing.storePath}")
                            return@async coordString to existing
                        }

                        // Download and install
                        val result = installSingleDependency(coord)
                        when (result) {
                            is KResult.Success -> {
                                Logger.success("Installed $coordString")
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

        KResult.Success(Unit)
    }

    private suspend fun installSingleDependency(coordinate: MavenCoordinate): KResult<DependencyEntry> {
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
                val entry = DependencyEntry(
                    version = coordinate.version,
                    hash = hash,
                    storePath = storePath.data.toString()
                )
                return KResult.Success(entry)
            }
            is KResult.Error -> return storePath
        }
    }
}