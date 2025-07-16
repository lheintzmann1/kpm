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

import kpm.core.KResult
import kpm.core.Constants as Consts
import java.io.IOException
import java.nio.file.*
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

object FileUtils {
    /**
     * Ensures that the specified directory exists. If it does not exist, it will be created.
     * Returns a KResult indicating success or failure.
     *
     * @param path The path to the directory to ensure exists.
     * @return KResult<Unit> indicating success or failure.
     */
    fun ensureDirectoryExists(path: Path): KResult<Unit> {
        return try {
            if (!path.exists()) {
                path.createDirectory()
                Logger.debug("Created directory: $path")
            }
            KResult.Success(Unit)
        } catch (e: IOException) {
            KResult.Error("Failed to create directory: $path", e)
        }
    }

    /**
     * Ensures that the KPM directories exist.
     *
     * @return KResult<Unit> indicating success or failure.
     */
    fun ensureKpmDirectories(): KResult<Unit> {
        val dirs = listOf(
            Consts.KPM_HOME,
            Consts.KPM_TEMPLATES
        )

        for (dir in dirs) {
            when (val result = ensureDirectoryExists(dir)) {
                is KResult.Success -> continue
                is KResult.Error -> return result
            }
        }

        return KResult.Success(Unit)
    }

    /**
     * Checks if the current directory is a KPM project directory by looking for the presence of the manifest file.
     *
     * @return Boolean indicating whether the current directory is a KPM project directory.
     */
    fun isProjectDirectory(): Boolean {
        val manifestPath = Paths.get(Consts.MANIFEST)
        return manifestPath.exists()
    }
}