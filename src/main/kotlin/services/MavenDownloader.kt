﻿/*
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
import kpm.utils.ProcessUtils
import java.nio.file.Path
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

    /**
     * Downloads the JAR file for the given Maven coordinate from Maven Central.
     * @param coordinate The Maven coordinate specifying the artifact to download.
     * @return A [KResult] containing the path to the downloaded JAR file, or an error if the download failed.
     */
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

    /**
     * Downloads the POM file for the given Maven coordinate from Maven Central.
     * @param coordinate The Maven coordinate specifying the artifact to download.
     * @return A [KResult] containing the path to the downloaded POM file, or an error if the download failed.
     */
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

    /**
     * Builds the URL for the JAR file of the given Maven coordinate.
     * @param coordinate The Maven coordinate specifying the artifact.
     * @return The URL to download the JAR file.
     */
    private fun buildJarUrl(coordinate: MavenCoordinate): String {
        return "$MAVEN_CENTRAL_URL/${coordinate.toPath()}/${coordinate.toFileName()}"
    }

    /**
     * Builds the URL for the POM file of the given Maven coordinate.
     * @param coordinate The Maven coordinate specifying the artifact.
     * @return The URL to download the POM file.
     */
    private fun buildPomUrl(coordinate: MavenCoordinate): String {
        return "$MAVEN_CENTRAL_URL/${coordinate.toPath()}/${coordinate.artifactId}-${coordinate.version}.pom"
    }

}