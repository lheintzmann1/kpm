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
import kpm.core.Logger
import kpm.core.Constants as Consts
import java.io.IOException
import java.nio.file.*
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

object FileUtils {
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

    fun isProjectDirectory(): Boolean {
        val manifestPath = Paths.get(Consts.MANIFEST)
        return manifestPath.exists()
    }
}