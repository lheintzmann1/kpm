package kpm.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kpm.core.KResult
import kpm.core.Logger
import kpm.models.MavenCoordinate
import kpm.utils.ProcessUtils
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.createDirectories
import kpm.core.Constants as Consts

class MavenDownloader {
    private val tempDir: Path = Consts.KPM_HOME.resolve("tmp")

    companion object {
        private const val MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2"
    }

    init {
        if (!tempDir.exists()) {
            tempDir.createDirectories()
        }
    }

    suspend fun downloadJar(coordinate: MavenCoordinate): KResult<Path> = withContext(Dispatchers.IO) {
        val jarUrl = buildJarUrl(coordinate)
        val tempJarPath = tempDir.resolve("${coordinate.artifactId}-${coordinate.version}-${System.currentTimeMillis()}.jar")

        Logger.debug("Downloading JAR from: $jarUrl")
        Logger.debug("Temporary path: $tempJarPath")

        // First try to download the JAR
        val downloadResult = ProcessUtils.downloadFile(jarUrl, tempJarPath.toString())

        when (downloadResult) {
            is KResult.Success -> {
                Logger.debug("Successfully downloaded JAR for $coordinate")
                KResult.Success(tempJarPath)
            }
            is KResult.Error -> {
                Logger.error("Failed to download JAR for $coordinate: ${downloadResult.message}")
                KResult.Error("Failed to download JAR for $coordinate from Maven Central", downloadResult.cause)
            }
        }
    }

    suspend fun downloadPom(coordinate: MavenCoordinate): KResult<Path> = withContext(Dispatchers.IO) {
        val pomUrl = buildPomUrl(coordinate)
        val tempPomPath = tempDir.resolve("${coordinate.artifactId}-${coordinate.version}-${System.currentTimeMillis()}.pom")

        Logger.debug("Downloading POM from: $pomUrl")

        val downloadResult = ProcessUtils.downloadFile(pomUrl, tempPomPath.toString())

        when (downloadResult) {
            is KResult.Success -> {
                Logger.debug("Successfully downloaded POM for $coordinate")
                KResult.Success(tempPomPath)
            }
            is KResult.Error -> {
                Logger.warning("Failed to download POM for $coordinate: ${downloadResult.message}")
                KResult.Error("Failed to download POM for $coordinate from Maven Central", downloadResult.cause)
            }
        }
    }

    private fun buildJarUrl(coordinate: MavenCoordinate): String {
        return "$MAVEN_CENTRAL_URL/${coordinate.toPath()}/${coordinate.toFileName()}"
    }

    private fun buildPomUrl(coordinate: MavenCoordinate): String {
        return "$MAVEN_CENTRAL_URL/${coordinate.toPath()}/${coordinate.artifactId}-${coordinate.version}.pom"
    }

    suspend fun verifyArtifactExists(coordinate: MavenCoordinate): KResult<Boolean> = withContext(Dispatchers.IO) {
        val jarUrl = buildJarUrl(coordinate)

        // Use HEAD request to check if artifact exists without downloading
        val headResult = ProcessUtils.runCommand(
            listOf("curl", "-I", "-s", "-f", jarUrl),
            timeoutMs = 10_000
        )

        when (headResult) {
            is KResult.Success -> KResult.Success(true)
            is KResult.Error -> KResult.Success(false)
        }
    }
}