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

package kpm.utils

import kpm.core.Constants as Consts
import kpm.core.KResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kpm.core.Platform
import java.io.IOException

object ProcessUtils {
    suspend fun runCommand(
        command: List<String>,
        timeoutMs: Long = Consts.DL_TIMEOUT_MS,
        workingDirectory: String? = null
    ): KResult<String> = withContext(Dispatchers.IO) {
        try {
            Logger.debug("Executing command: ${command.joinToString(" ")}")

            val processBuilder = ProcessBuilder(command)
                .redirectErrorStream(true)

            workingDirectory?.let { processBuilder.directory(java.io.File(it)) }

            val result = withTimeoutOrNull(timeoutMs) {
                val process = processBuilder.start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    KResult.Success(output)
                } else {
                    KResult.Error("Command failed with exit code $exitCode: $output")
                }
            }

            result ?: KResult.Error("Command timed out after $timeoutMs ms")
        } catch (e: IOException) {
            KResult.Error("Failed to execute command: ${e.message}", e)
        } catch (e: Exception) {
            KResult.Error("Unexpected error: ${e.message}", e)
        }
    }

    private fun getCurlCommand(url: String, outputPath: String): List<String> {
        return when (Consts.PLATFORM) {
            Platform.WINDOWS -> listOf("cmd.exe", "/c", "curl", "-L", url, "-o", outputPath)
            else -> listOf("curl", "-L", url, "-o", outputPath)
        }
    }

    private fun getUnzipCommand(url: String, destination: String): List<String> {
        return when (Consts.PLATFORM) {
            Platform.WINDOWS -> listOf("cmd.exe", "/c", "tar", "-xf", url, "--strip-components=1")
            else -> listOf("unzip", "-o", url, "-d", destination)
        }
    }

    suspend fun downloadFile(url: String, outputPath: String): KResult<Unit> {
        val command = getCurlCommand(url, outputPath)
        return runCommand(command).map { Unit }
    }

    suspend fun unzipFile(zipPath: String, destination: String): KResult<Unit> {
        val command = getUnzipCommand(zipPath, destination)
        return runCommand(command).map { Unit }
    }
}