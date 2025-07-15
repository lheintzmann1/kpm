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
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.default
import kotlinx.coroutines.runBlocking
import kpm.core.Logger
import kpm.services.ManifestParser
import kpm.core.KResult
import kpm.core.Constants as Consts
import kpm.services.KotlinBuilder
import java.nio.file.Paths

class Build : CliktCommand() {
    private val outputDir by option(
        "--output", "-o",
        help = "Output directory for the JAR file"
    ).default("build/libs")

    private val manifestParser = ManifestParser()
    private val kotlinBuilder = KotlinBuilder()

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
                Logger.info("Building project '${manifest.data.name}'...")

                kotlinBuilder.build(manifest.data, outputDir)
                    .onSuccess {
                        Logger.success("Build completed successfully!")
                    }
                    .onError { message, cause ->
                        Logger.error("Build failed: $message", cause)
                    }
            }
            is KResult.Error -> {
                Logger.error("Failed to read manifest: ${manifest.message}", manifest.cause)
            }
        }
    }

    override fun help(context: Context): String {
        return """
            Builds the KPM project defined in the manifest file.
            The output JAR will be placed in the specified output directory (default: build/libs).
        """.trimIndent()
    }
}
