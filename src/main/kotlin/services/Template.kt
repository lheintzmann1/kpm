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
import kpm.utils.ProcessUtils
import java.nio.file.Path
import kotlin.io.path.exists
import kpm.core.Constants as Consts

class Template {
    private val templateZipPath: Path = Consts.KPM_TEMPLATES.resolve("base.zip")

    suspend fun initProject(): KResult<Unit> = withContext(Dispatchers.IO) {
        Logger.debug("Initializing project")

        if (FileUtils.isProjectDirectory()) {
            return@withContext KResult.Error("Project already initialized in this directory.")
        }

        FileUtils.ensureKpmDirectories()
            .onError { message, cause ->
                Logger.error(message, cause)
                return@withContext KResult.Error(message, cause)
            }

        downloadTemplate()
            .onError { message, cause ->
                Logger.error(message, cause)
                return@withContext KResult.Error(message, cause)
            }

        extractTemplate()
            .onError { message, cause ->
                Logger.error(message, cause)
                return@withContext KResult.Error(message, cause)
            }

        Logger.info("Project initialized successfully")
        KResult.Success(Unit)
    }

    private suspend fun downloadTemplate(): KResult<Unit> {
        if (templateZipPath.exists()) {
            Logger.debug("Template already downloaded at ${templateZipPath.toAbsolutePath()}")
            return KResult.Success(Unit)
        }

        Logger.info("Downloading base template...")
        val url = "${Consts.TEMPLATE_BASE}/archive/refs/heads/main.zip"

        return ProcessUtils.downloadFile(url, templateZipPath.toString())
            .onError { message, _ ->
                Logger.error("$message. Make sure 'curl' is installed and internet connection is available.")
            }
    }

    private suspend fun extractTemplate(): KResult<Unit> {
        Logger.info("Extracting base template...")

        return ProcessUtils.unzipFile(templateZipPath.toString(), ".")
            .onError { message, _ ->
                Logger.error("$message. Make sure 'unzip' or 'tar' is installed.")
            }
    }

}