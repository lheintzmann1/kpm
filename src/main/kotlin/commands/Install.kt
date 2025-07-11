package kpm.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import kotlinx.coroutines.runBlocking
import kpm.core.Logger
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