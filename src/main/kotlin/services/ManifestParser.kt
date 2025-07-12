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

import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import kpm.core.KResult
import kpm.models.Manifest
import kpm.models.LockFile
import java.nio.file.Path
import kotlin.io.path.readText

class ManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun parseManifest(manifestPath: Path): KResult<Manifest> {
        return try {
            val content = manifestPath.readText()
            val manifest = json.decodeFromString<Manifest>(content)

            // Validate dependencies format
            for (dep in manifest.dependencies) {
                if (dep.split(":").size != 3) {
                    return KResult.Error("Invalid dependency format: '$dep'. Expected format: 'group:artifact:version'")
                }
            }

            KResult.Success(manifest)
        } catch (e: SerializationException) {
            KResult.Error("Invalid JSON format in manifest", e)
        } catch (e: Exception) {
            KResult.Error("Failed to read manifest file", e)
        }
    }

    fun parseLockFile(lockFilePath: Path): KResult<LockFile> {
        return try {
            val content = lockFilePath.readText()
            val lockFile = json.decodeFromString<LockFile>(content)
            KResult.Success(lockFile)
        } catch (e: SerializationException) {
            KResult.Error("Invalid JSON format in lock file", e)
        } catch (e: Exception) {
            KResult.Error("Failed to read lock file", e)
        }
    }

    fun writeLockFile(lockFilePath: Path, lockFile: LockFile): KResult<Unit> {
        return try {
            val content = json.encodeToString(LockFile.serializer(), lockFile)
            lockFilePath.toFile().writeText(content)
            KResult.Success(Unit)
        } catch (e: Exception) {
            KResult.Error("Failed to write lock file", e)
        }
    }
}