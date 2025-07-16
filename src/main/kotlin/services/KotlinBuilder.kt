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

import kpm.utils.Logger
import kpm.models.Manifest
import kpm.core.KResult
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest as JarManifest
import kotlin.io.path.*

class KotlinBuilder {

    private val kotlinHome by lazy {
        System.getenv("KOTLIN_HOME")
            ?: error("KOTLIN_HOME environment variable must be set")
    }

    private val kotlinLibDir by lazy {
        Paths.get(kotlinHome, "lib").also {
            require(it.exists() && it.isDirectory()) {
                "Kotlin lib directory not found at: $it"
            }
        }
    }

    /**
     * Builds the Kotlin project based on the provided manifest.
     *
     * @param manifest The project manifest containing metadata and configuration.
     * @param outputDir The directory where the compiled JAR file will be placed.
     * @return A [KResult] indicating success or failure of the build process.
     */
    fun build(manifest: Manifest, outputDir: String): KResult<Unit> {
        return runCatching {
            Logger.info("Starting Kotlin compilation for ${manifest.name}")

            val context = BuildContext(manifest, outputDir)

            // Setup and validate
            validateKotlinInstallation()
            setupBuildDirectories(context)

            // Find sources
            val sourceFiles = findKotlinSources()
            require(sourceFiles.isNotEmpty()) { "No Kotlin source files found in src/main/kotlin" }
            Logger.info("Found ${sourceFiles.size} Kotlin source files")

            // Prepare classpath
            val classpath = prepareClasspath()

            // Compile
            compileKotlinSources(context, sourceFiles, classpath)

            // Create JAR
            createJarFile(context)

            Logger.success("Build completed successfully: ${context.jarFile.absolutePath}")
        }.fold(
            onSuccess = { KResult.Success(Unit) },
            onFailure = { e ->
                Logger.error("Build failed: ${e.message}", e)
                KResult.Error("Build failed: ${e.message}", e)
            }
        )
    }

    /**
     * Validates the Kotlin installation by checking for required libraries.
     *
     * @throws IllegalStateException if any required Kotlin libraries are missing.
     */
    private fun validateKotlinInstallation() {
        val missingJars = REQUIRED_KOTLIN_JARS.filter { !kotlinLibDir.resolve(it).exists() }
        require(missingJars.isEmpty()) {
            "Missing required Kotlin libraries: ${missingJars.joinToString(", ")}"
        }
        Logger.debug("Kotlin installation validated at: $kotlinHome")
    }

    /**
     * Sets up the build directories, ensuring the temporary classes directory is clean and ready.
     *
     * @param context The build context containing paths and configuration.
     */
    @OptIn(ExperimentalPathApi::class)
    private fun setupBuildDirectories(context: BuildContext) {
        // Clean and create temp classes directory
        context.tempClassesDir.apply {
            if (exists()) deleteRecursively()
            createDirectories()
        }

        // Ensure output directory exists
        context.outputDir.createDirectories()
    }

    /**
     * Finds all Kotlin source files in the src/main/kotlin directory.
     *
     * @return A list of Kotlin source files found.
     */
    private fun findKotlinSources(): List<File> {
        val sourceDir = Paths.get(SRC_DIR)
        if (!sourceDir.exists() || !sourceDir.isDirectory()) return emptyList()

        return runCatching {
            Files.walk(sourceDir)
                .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                .map { it.toFile() }
                .toList()
        }.getOrElse { e ->
            Logger.error("Failed to scan source files: ${e.message}")
            emptyList()
        }
    }

    /**
     * Prepares the classpath for the Kotlin compiler, including standard libraries and project dependencies.
     *
     * @return A list of files representing the classpath.
     */
    private fun prepareClasspath(): List<File> {
        val classpathFiles = mutableListOf<File>()

        // Add Kotlin standard libraries
        classpathFiles.addAll(getKotlinStandardLibraries())
        Logger.info("Added ${classpathFiles.size} Kotlin standard libraries")

        // Add project dependencies (optional)
        getProjectDependencies().fold(
            onSuccess = { deps ->
                classpathFiles.addAll(deps)
                Logger.info("Added ${deps.size} project dependencies")
            },
            onFailure = { e ->
                Logger.warning("Failed to load project dependencies: ${e.message}")
            }
        )

        return classpathFiles
    }

    /**
     * Retrieves the required Kotlin standard libraries from the Kotlin installation directory.
     *
     * @return A list of files representing the required and optional Kotlin libraries.
     */
    private fun getKotlinStandardLibraries(): List<File> {
        val stdLibs = REQUIRED_KOTLIN_JARS.map { kotlinLibDir.resolve(it).toFile() }
        val optionalLibs = OPTIONAL_KOTLIN_JARS.mapNotNull { jarName ->
            kotlinLibDir.resolve(jarName).toFile().takeIf { it.exists() }
        }

        return stdLibs + optionalLibs
    }

    /**
     * Retrieves project dependencies from the local store directory.
     *
     * @return A [Result] containing a list of dependency files or an error if the store directory is invalid.
     */
    // TODO: Filter dependencies based on the project manifest to ensure only relevant dependencies are included
    private fun getProjectDependencies(): Result<List<File>> {
        val storeDir = Paths.get(STORE_DIR)
        if (!storeDir.exists() || !storeDir.isDirectory()) {
            return Result.success(emptyList())
        }

        return runCatching {
            Files.walk(storeDir)
                .filter { it.isRegularFile() && it.toString().endsWith(".jar") }
                .map { it.toFile() }
                .toList()
        }
    }

    /**
     * Compiles the provided Kotlin source files using the K2JVMCompiler.
     *
     * @param context The build context containing paths and configuration.
     * @param sourceFiles The list of Kotlin source files to compile.
     * @param classpath The classpath to use for compilation.
     */
    // TODO: Use Kotlin build tools API instead of K2JVMCompiler
    // TODO: Add support for incremental compilation and better error handling
    private fun compileKotlinSources(
        context: BuildContext,
        sourceFiles: List<File>,
        classpath: List<File>
    ) {
        Logger.info("Starting Kotlin compilation...")

        val messageCollector = CompilerMessageCollector()
        val disposable = Disposer.newDisposable()

        try {
            val compiler = K2JVMCompiler()
            val args = buildCompilerArgs(context, classpath, sourceFiles)

            Logger.debug("Compiler arguments: ${args.joinToString(" ")}")

            val exitCode = compiler.exec(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, *args)
            handleCompilationResult(exitCode, messageCollector)

        } finally {
            disposable.dispose()
        }
    }

    /**
     * Builds the compiler arguments for the K2JVMCompiler.
     *
     * @param context The build context containing paths and configuration.
     * @param classpath The classpath to use for compilation.
     * @param sourceFiles The list of Kotlin source files to compile.
     * @return An array of strings representing the compiler arguments.
     */
    private fun buildCompilerArgs(
        context: BuildContext,
        classpath: List<File>,
        sourceFiles: List<File>
    ): Array<String> = buildList {
        // Basic compilation settings
        addAll(listOf("-d", context.tempClassesDir.toString()))
        addAll(listOf("-module-name", context.manifest.name))
        addAll(listOf("-jvm-target", JVM_TARGET))
        addAll(listOf("-language-version", KOTLIN_VERSION))
        addAll(listOf("-api-version", API_VERSION))

        // Classpath
        if (classpath.isNotEmpty()) {
            addAll(listOf("-classpath", classpath.joinToString(File.pathSeparator) { it.absolutePath }))
        }

        // Optimization flags
        addAll(listOf("-no-stdlib", "-no-reflect", "-progressive", "-Xsuppress-version-warnings"))

        // Source files
        sourceFiles.forEach { add(it.absolutePath) }
    }.toTypedArray()

    /**
     * Handles the result of the Kotlin compilation, logging messages and errors as appropriate.
     *
     * @param exitCode The exit code from the compiler.
     * @param messageCollector The collector for compiler messages.
     */
    private fun handleCompilationResult(exitCode: ExitCode, messageCollector: CompilerMessageCollector) {
        when (exitCode) {
            ExitCode.OK -> {
                Logger.success("Compilation completed successfully")
                if (messageCollector.hasWarnings()) {
                    Logger.warning("Compilation completed with ${messageCollector.warningCount} warnings")
                }
            }
            ExitCode.COMPILATION_ERROR -> {
                val errorMessage = if (messageCollector.hasErrors()) {
                    "Compilation failed with ${messageCollector.errorCount} errors:\n${messageCollector.getErrorSummary()}"
                } else {
                    "Compilation failed with unknown errors"
                }
                error(errorMessage)
            }
            else -> error("Compilation failed with exit code: $exitCode")
        }
    }

    /**
     * Creates the JAR file from the compiled classes in the temporary directory.
     *
     * @param context The build context containing paths and configuration.
     */
    private fun createJarFile(context: BuildContext) {
        Logger.info("Creating JAR file...")

        val jarManifest = createJarManifest(context.manifest)

        FileOutputStream(context.jarFile).buffered(BUFFER_SIZE).use { fileOut ->
            JarOutputStream(fileOut, jarManifest).use { jarOut ->
                addDirectoryToJar(jarOut, context.tempClassesDir.toFile())
            }
        }

        // Clean up temporary directory
        cleanupTempDirectory(context.tempClassesDir)

        Logger.success("JAR file created: ${context.jarFile.absolutePath}")
    }

    /**
     * Creates a JAR manifest with the necessary attributes.
     *
     * @param manifest The project manifest containing metadata.
     * @return A [JarManifest] object with the main attributes set.
     */
    private fun createJarManifest(manifest: Manifest): JarManifest {
        return JarManifest().apply {
            mainAttributes.apply {
                putValue("Manifest-Version", "1.0")
                putValue("Implementation-Title", manifest.name)
                putValue("Implementation-Version", manifest.version)
                putValue("Created-By", "KPM Build Tool")
                putValue("Built-By", System.getProperty("user.name", "unknown"))
                putValue("Build-Jdk", System.getProperty("java.version", "unknown"))

                if (manifest.main.isNotEmpty()) {
                    putValue("Main-Class", manifest.main)
                }
            }
        }
    }

    /**
     * Adds the contents of a directory to a JAR output stream, preserving the directory structure.
     *
     * @param jarOutputStream The JAR output stream to write to.
     * @param directory The directory to add to the JAR.
     */
    private fun addDirectoryToJar(jarOutputStream: JarOutputStream, directory: File) {
        val visited = mutableSetOf<String>()

        directory.walkTopDown()
            .onFail { file, e -> Logger.warning("Error accessing $file: ${e.message}") }
            .forEach { file ->
                val relativePath = file.relativeTo(directory).path
                    .replace('\\', '/')
                    .let { if (it.isEmpty()) file.name else it }

                if (relativePath in visited) return@forEach
                visited.add(relativePath)

                runCatching {
                    when {
                        file.isDirectory -> {
                            if (relativePath.isNotEmpty()) {
                                val dirEntry = JarEntry("$relativePath/").apply { time = file.lastModified() }
                                jarOutputStream.putNextEntry(dirEntry)
                                jarOutputStream.closeEntry()
                            }
                        }
                        else -> {
                            val fileEntry = JarEntry(relativePath).apply { time = file.lastModified() }
                            jarOutputStream.putNextEntry(fileEntry)
                            file.inputStream().buffered().use { it.copyTo(jarOutputStream) }
                            jarOutputStream.closeEntry()
                        }
                    }
                }.onFailure { e ->
                    Logger.warning("Failed to add entry $relativePath: ${e.message}")
                }
            }
    }

    /**
     * Cleans up the temporary directory used for compilation.
     *
     * @param tempDir The temporary directory to clean up.
     */
    @OptIn(ExperimentalPathApi::class)
    private fun cleanupTempDirectory(tempDir: Path) {
        runCatching {
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
                Logger.debug("Cleaned up temporary directory: $tempDir")
            }
        }.onFailure { e ->
            Logger.warning("Failed to clean up temporary directory: ${e.message}")
        }
    }

    /**
     * Represents the build context containing paths and configuration for the build process.
     *
     * @property manifest The project manifest containing metadata and configuration.
     * @property outputDirPath The path to the output directory where the JAR file will be placed.
     */
    private data class BuildContext(
        val manifest: Manifest,
        val outputDirPath: String
    ) {
        val outputDir: Path = Paths.get(outputDirPath)
        val tempClassesDir: Path = outputDir.resolve(TEMP_CLASSES_DIR)
        val jarFile: File = outputDir.resolve("${manifest.name}.jar").toFile()
    }

    /**
     * A custom message collector for the Kotlin compiler that collects and logs messages.
     */
    private class CompilerMessageCollector : MessageCollector {
        private val errors = CopyOnWriteArrayList<String>()
        private val warnings = CopyOnWriteArrayList<String>()

        val errorCount: Int get() = errors.size
        val warningCount: Int get() = warnings.size

        override fun clear() {
            errors.clear()
            warnings.clear()
        }

        override fun hasErrors(): Boolean = errors.isNotEmpty()
        fun hasWarnings(): Boolean = warnings.isNotEmpty()

        override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?
        ) {
            val locationStr = location?.let { loc ->
                buildString {
                    append(loc.path)
                    if (loc.line > 0) {
                        append(':').append(loc.line)
                        if (loc.column > 0) append(':').append(loc.column)
                    }
                }
            } ?: ""

            val fullMessage = if (locationStr.isNotEmpty()) "$locationStr: $message" else message

            when (severity) {
                CompilerMessageSeverity.ERROR -> {
                    errors.add(fullMessage)
                    Logger.error("Compilation error: $fullMessage")
                }
                CompilerMessageSeverity.STRONG_WARNING,
                CompilerMessageSeverity.WARNING -> {
                    warnings.add(fullMessage)
                    Logger.warning("Compilation warning: $fullMessage")
                }
                CompilerMessageSeverity.INFO -> {
                    Logger.info("Compiler info: $fullMessage")
                }
                CompilerMessageSeverity.LOGGING -> {
                    Logger.debug("Compiler log: $fullMessage")
                }
                else -> {
                    Logger.debug("Compiler: $fullMessage")
                }
            }
        }

        fun getErrorSummary(): String = buildString {
            val displayErrors = errors.take(MAX_ERROR_DISPLAY)
            append(displayErrors.joinToString("\n"))
            if (errors.size > MAX_ERROR_DISPLAY) {
                append("\n... and ${errors.size - MAX_ERROR_DISPLAY} more errors")
            }
        }
    }

    /**
     * A companion object containing constants and utility methods for the KotlinBuilder.
     */
    companion object {
        private const val KOTLIN_VERSION = "2.2"
        private const val API_VERSION = "2.2"
        private const val JVM_TARGET = "1.8"
        private const val TEMP_CLASSES_DIR = "temp-classes"
        private const val SRC_DIR = "src/main/kotlin"
        private const val STORE_DIR = ".kpm/store"
        private const val BUFFER_SIZE = 8192
        private const val MAX_ERROR_DISPLAY = 10

        private val REQUIRED_KOTLIN_JARS = listOf(
            "kotlin-stdlib.jar",
            "kotlin-stdlib-jdk8.jar",
            "kotlin-reflect.jar"
        )

        private val OPTIONAL_KOTLIN_JARS = listOf(
            "kotlin-stdlib-jdk7.jar",
            "kotlin-script-runtime.jar",
            "kotlin-stdlib-common.jar"
        )
    }
}