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

package kpm.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import kotlinx.coroutines.runBlocking
import kpm.utils.Logger
import kpm.services.DependencyManager
import kpm.services.ManifestParser
import kpm.core.KResult
import java.nio.file.Paths
import kpm.core.Constants as Consts

class Install : CliktCommand() {
    private val dependencyManager = DependencyManager()
    private val manifestParser = ManifestParser()

    override fun run(): Unit = runBlocking {
        val manifestPath = Paths.get(Consts.MANIFEST)

        if (!manifestPath.toFile().exists()) {
            Logger.error("No ${Consts.MANIFEST} found. Run 'kpm init' to initialize a project.")
            return@runBlocking
        }

        Logger.info("Reading project manifest...")
        val manifest = manifestParser.parseManifest(manifestPath)
            .onError { message, cause ->
                Logger.error("Failed to parse manifest: $message", cause)
                return@runBlocking
            }

        when (manifest) {
            is KResult.Success -> {
                Logger.info("Installing dependencies for project '${manifest.data.name}'...")

                dependencyManager.installDependencies(manifest.data.dependencies)
                    .onSuccess {
                        Logger.success("All dependencies installed successfully!")
                    }
                    .onError { message, cause ->
                        Logger.error("Failed to install dependencies: $message", cause)
                    }
            }
            is KResult.Error -> {
                Logger.error("Failed to read manifest: ${manifest.message}", manifest.cause)
            }
        }
    }

    override fun help(context: Context): String {
        return """
            Install project dependencies from kpm.json.
            
            This command reads the project manifest and installs all declared dependencies
            into the local KPM store with content-addressable storage similar to Nix.
        """.trimIndent()
    }
}