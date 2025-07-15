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

import kpm.core.Logger
import kpm.models.Manifest
import kpm.core.KResult
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.*
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.ExperimentalPathApi
import java.util.jar.Manifest as JarManifest
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import java.util.concurrent.CopyOnWriteArrayList

class KotlinBuilder {

    companion object {
        private const val DEFAULT_KOTLIN_VERSION = "2.2"
        private const val DEFAULT_API_VERSION = "2.2"
        private const val DEFAULT_JVM_TARGET = "1.8"
        private const val TEMP_CLASSES_DIR = "temp-classes"

        // Standard Kotlin libraries that should be included
        private val KOTLIN_STDLIB_JARS = listOf(
            "kotlin-stdlib.jar",
            "kotlin-stdlib-jdk8.jar",
            "kotlin-reflect.jar"
        )

        // Optional libraries that might be present
        private val OPTIONAL_KOTLIN_LIBS = listOf(
            "kotlin-stdlib-jdk7.jar",
            "kotlin-script-runtime.jar",
            "kotlin-stdlib-common.jar",
            "kotlin-stdlib-js.jar"
        )
    }

    private fun getKotlinHome(): String {
        return System.getenv("KOTLIN_HOME")
            ?: throw IllegalStateException("Kotlin compiler path not configured. Please: set the KOTLIN_HOME environment variable to point to your Kotlin installation.")
    }

    suspend fun build(manifest: Manifest, outputDir: String): KResult<Unit> {
        return try {
            Logger.info("Starting Kotlin compilation for ${manifest.name}")

            val buildContext = BuildContext(manifest, outputDir)

            // Validate Kotlin installation
            validateKotlinInstallation()
                .onError { message, cause -> return KResult.Error(message, cause) }

            // Setup build directories
            setupBuildDirectories(buildContext)
                .onError { message, cause -> return KResult.Error(message, cause) }

            // Find and validate source files
            val sourceFiles = findKotlinSources()
            if (sourceFiles.isEmpty()) {
                return KResult.Error("No Kotlin source files found in src/main/kotlin")
            }
            Logger.info("Found ${sourceFiles.size} Kotlin source files")

            // Prepare compilation classpath
            val classpath = prepareClasspath()
                .onError { message, cause -> return KResult.Error(message, cause) }
                .let { (it as KResult.Success).data }

            // Compile sources using Kotlin API directly
            compileKotlinSourcesWithAPI(buildContext, sourceFiles, classpath)
                .onError { message, cause -> return KResult.Error(message, cause) }

            // Create JAR file
            createJarFile(buildContext)
                .onError { message, cause -> return KResult.Error(message, cause) }

            Logger.success("Build completed successfully: ${buildContext.jarFile.absolutePath}")
            KResult.Success(Unit)

        } catch (e: Exception) {
            Logger.error("Build failed with exception: ${e.message}", e)
            KResult.Error("Build failed: ${e.message}", e)
        }
    }

    private fun validateKotlinInstallation(): KResult<Unit> {
        val kotlinHome = getKotlinHome()
        val kotlinLibDir = Paths.get(kotlinHome, "lib")

        if (!kotlinLibDir.exists() || !kotlinLibDir.isDirectory()) {
            return KResult.Error("Kotlin lib directory not found at: $kotlinLibDir")
        }

        // Check for required jars
        val missingJars = KOTLIN_STDLIB_JARS.filter { jarName ->
            !kotlinLibDir.resolve(jarName).exists()
        }

        if (missingJars.isNotEmpty()) {
            return KResult.Error("Missing required Kotlin libraries: ${missingJars.joinToString(", ")}")
        }

        Logger.debug("Kotlin installation validated at: $kotlinHome")
        return KResult.Success(Unit)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun setupBuildDirectories(context: BuildContext): KResult<Unit> {
        return try {
            // Clean and create temp classes directory
            if (context.tempClassesDir.exists()) {
                context.tempClassesDir.deleteRecursively()
            }
            context.tempClassesDir.createDirectories()

            // Ensure output directory exists
            if (!context.outputDir.exists()) {
                context.outputDir.createDirectories()
            }

            KResult.Success(Unit)
        } catch (e: Exception) {
            KResult.Error("Failed to setup build directories: ${e.message}", e)
        }
    }

    private fun findKotlinSources(): List<File> {
        val sourceDir = Paths.get("src/main/kotlin")
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            return emptyList()
        }

        return try {
            Files.walk(sourceDir)
                .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                .map { it.toFile() }
                .toList()
        } catch (e: Exception) {
            Logger.error("Failed to scan source files: ${e.message}")
            emptyList()
        }
    }

    private fun prepareClasspath(): KResult<List<File>> {
        val classpathFiles = mutableListOf<File>()

        // Add Kotlin standard libraries
        getKotlinStandardLibraries()
            .onError { message, cause -> return KResult.Error(message, cause) }
            .onSuccess { stdLibs ->
                classpathFiles.addAll(stdLibs)
                Logger.info("Added ${stdLibs.size} Kotlin standard libraries")
            }

        // Add project dependencies
        getProjectDependencies()
            .onError { message, cause ->
                Logger.warning("Failed to load project dependencies: $message")
                // Don't fail the build, just continue without project dependencies
            }
            .onSuccess { projectDeps ->
                classpathFiles.addAll(projectDeps)
                Logger.info("Added ${projectDeps.size} project dependencies")
            }

        return KResult.Success(classpathFiles)
    }

    private fun getKotlinStandardLibraries(): KResult<List<File>> {
        val kotlinHome = getKotlinHome()
        val kotlinLibPath = Paths.get(kotlinHome, "lib")

        if (!kotlinLibPath.exists() || !kotlinLibPath.isDirectory()) {
            return KResult.Error("Kotlin lib directory not found at: $kotlinLibPath")
        }

        val libFiles = mutableListOf<File>()

        // TODO: Detect which dependencies are needed
        // Add required libraries
        KOTLIN_STDLIB_JARS.forEach { jarName ->
            val jarFile = kotlinLibPath.resolve(jarName).toFile()
            if (jarFile.exists()) {
                libFiles.add(jarFile)
                Logger.debug("Added required Kotlin library: ${jarFile.absolutePath}")
            } else {
                return KResult.Error("Required Kotlin library not found: $jarName")
            }
        }

        // Add optional libraries if present
        OPTIONAL_KOTLIN_LIBS.forEach { jarName ->
            val jarFile = kotlinLibPath.resolve(jarName).toFile()
            if (jarFile.exists()) {
                libFiles.add(jarFile)
                Logger.debug("Added optional Kotlin library: ${jarFile.absolutePath}")
            }
        }

        return KResult.Success(libFiles)
    }

    private fun getProjectDependencies(): KResult<List<File>> {
        // TODO: Scan the kpm.json and kpm-lock.json files for dependencies
        val storeDir = Paths.get(".kpm/store")
        if (!storeDir.exists() || !storeDir.isDirectory()) {
            return KResult.Success(emptyList())
        }

        return try {
            val projectDeps = Files.walk(storeDir)
                .filter { it.isRegularFile() && it.toString().endsWith(".jar") }
                .map { it.toFile() }
                .toList()
            KResult.Success(projectDeps)
        } catch (e: Exception) {
            KResult.Error("Failed to scan project dependencies: ${e.message}", e)
        }
    }

    private fun compileKotlinSourcesWithAPI(
        context: BuildContext,
        sourceFiles: List<File>,
        classpath: List<File>
    ): KResult<Unit> {
        Logger.info("Starting Kotlin compilation with API...")

        val messageCollector = CompilerMessageCollector()
        val disposable = Disposer.newDisposable()

        return try {
            // Create compiler configuration
            val configuration = createCompilerConfiguration(context, classpath, sourceFiles, messageCollector)

            // Create Kotlin environment
            val environment = KotlinCoreEnvironment.createForProduction(
                disposable,
                configuration,
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )

            // Use K2JVMCompiler directly
            val compiler = K2JVMCompiler()

            // Build compiler arguments based on kotlinc script behavior
            val args = buildCompilerArgs(context, classpath, sourceFiles)

            Logger.debug("Compiler arguments: ${args.joinToString(" ")}")

            // Execute compilation
            val exitCode = compiler.exec(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, *args)

            handleCompilationResult(exitCode, messageCollector)

        } catch (e: Exception) {
            Logger.error("Compilation failed with exception: ${e.message}", e)
            KResult.Error("Compilation failed: ${e.message}", e)
        } finally {
            disposable.dispose()
        }
    }

    private fun createCompilerConfiguration(
        context: BuildContext,
        classpath: List<File>,
        sourceFiles: List<File>,
        messageCollector: MessageCollector
    ): CompilerConfiguration {
        return CompilerConfiguration().apply {
            val kotlinHome = getKotlinHome()

            // Set language version settings
            put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS,
                LanguageVersionSettingsImpl(
                    LanguageVersion.fromVersionString(DEFAULT_KOTLIN_VERSION) ?: LanguageVersion.LATEST_STABLE,
                    ApiVersion.createByLanguageVersion(LanguageVersion.fromVersionString(DEFAULT_API_VERSION) ?: LanguageVersion.LATEST_STABLE)
                ))

            // Set JVM target
            put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.fromString(DEFAULT_JVM_TARGET) ?: JvmTarget.JVM_1_8)

            // Set output directory
            put(JVMConfigurationKeys.OUTPUT_DIRECTORY, context.tempClassesDir.toFile())

            // Set module name
            put(CommonConfigurationKeys.MODULE_NAME, context.manifest.name)

            // Add message collector
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)

            // Add classpath roots
            if (classpath.isNotEmpty()) {
                addJvmClasspathRoots(classpath)
            }

            // Add source roots
            sourceFiles.forEach { sourceFile ->
                addKotlinSourceRoot(sourceFile.absolutePath)
            }

            // Enable incremental compilation
            put(CommonConfigurationKeys.USE_FIR, true)

            // Enable parallel compilation
            put(CommonConfigurationKeys.PARALLEL_BACKEND_THREADS,
                Runtime.getRuntime().availableProcessors().coerceAtMost(8))
        }
    }

    private fun buildCompilerArgs(
        context: BuildContext,
        classpath: List<File>,
        sourceFiles: List<File>
    ): Array<String> {
        val args = mutableListOf<String>()

        // Output directory
        args.addAll(listOf("-d", context.tempClassesDir.toString()))

        // Module name
        args.addAll(listOf("-module-name", context.manifest.name))

        // JVM target
        args.addAll(listOf("-jvm-target", DEFAULT_JVM_TARGET))

        // Language version
        args.addAll(listOf("-language-version", DEFAULT_KOTLIN_VERSION))

        // TODO: Fix this, use KOTLIN_HOME env or config instead
        // Prevent automatically adding the Kotlin stdlib and reflect jars
        args.addAll(listOf("-no-stdlib", "-no-reflect"))

        // API version
        args.addAll(listOf("-api-version", DEFAULT_API_VERSION))

        // Classpath (including Kotlin stdlib)
        if (classpath.isNotEmpty()) {
            args.addAll(listOf("-classpath", classpath.joinToString(File.pathSeparator) { it.absolutePath }))
        }

        // Progressive mode for better performance
        args.add("-progressive")

        // Suppress version warnings
        args.add("-Xsuppress-version-warnings")

        // Add source files
        sourceFiles.forEach { sourceFile ->
            args.add(sourceFile.absolutePath)
        }

        return args.toTypedArray()
    }

    private fun handleCompilationResult(
        exitCode: org.jetbrains.kotlin.cli.common.ExitCode,
        messageCollector: CompilerMessageCollector
    ): KResult<Unit> {
        return when (exitCode) {
            org.jetbrains.kotlin.cli.common.ExitCode.OK -> {
                Logger.success("Compilation completed successfully")
                if (messageCollector.hasWarnings()) {
                    Logger.warning("Compilation completed with ${messageCollector.warningCount} warnings")
                }
                KResult.Success(Unit)
            }
            org.jetbrains.kotlin.cli.common.ExitCode.COMPILATION_ERROR -> {
                val errorMessage = if (messageCollector.hasErrors()) {
                    "Compilation failed with ${messageCollector.errorCount} errors:\n${messageCollector.getErrorSummary()}"
                } else {
                    "Compilation failed with unknown errors"
                }
                KResult.Error(errorMessage)
            }
            else -> {
                KResult.Error("Compilation failed with exit code: $exitCode")
            }
        }
    }

    private fun createJarFile(context: BuildContext): KResult<Unit> {
        Logger.info("Creating JAR file...")

        return try {
            val jarManifest = createJarManifest(context.manifest)
            val bufferSize = 8192 // 8KB buffer

            FileOutputStream(context.jarFile).buffered(bufferSize).use { fileOut ->
                JarOutputStream(fileOut, jarManifest).use { jarOut ->
                    addDirectoryToJar(jarOut, context.tempClassesDir.toFile())
                }
            }

            // Clean up temporary directory
            cleanupTempDirectory(context.tempClassesDir)

            Logger.success("JAR file created: ${context.jarFile.absolutePath}")
            KResult.Success(Unit)
        } catch (e: Exception) {
            KResult.Error("Failed to create JAR file: ${e.message}", e)
        }
    }

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

    private fun addDirectoryToJar(jarOutputStream: JarOutputStream, directory: File) {
        val visited = mutableSetOf<String>()

        directory.walkTopDown()
            .onFail { file, e -> Logger.warning("Error accessing $file: ${e.message}") }
            .forEach { file ->
                val relativePath = file.relativeTo(directory).path
                    .replace('\\', '/')
                    .let { if (it.isEmpty()) file.name else it }

                // Avoid duplicate entries
                if (visited.contains(relativePath)) {
                    return@forEach
                }
                visited.add(relativePath)

                try {
                    if (file.isDirectory) {
                        // Add directory entry with trailing slash
                        if (relativePath.isNotEmpty()) {
                            val dirEntry = JarEntry("$relativePath/")
                            dirEntry.time = file.lastModified()
                            jarOutputStream.putNextEntry(dirEntry)
                            jarOutputStream.closeEntry()
                        }
                    } else {
                        // Add file entry
                        val fileEntry = JarEntry(relativePath)
                        fileEntry.time = file.lastModified()
                        jarOutputStream.putNextEntry(fileEntry)
                        file.inputStream().buffered().use { input ->
                            input.copyTo(jarOutputStream)
                        }
                        jarOutputStream.closeEntry()
                    }
                } catch (e: Exception) {
                    Logger.warning("Failed to add entry $relativePath: ${e.message}")
                }
            }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun cleanupTempDirectory(tempDir: Path) {
        try {
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
                Logger.debug("Cleaned up temporary directory: $tempDir")
            }
        } catch (e: Exception) {
            Logger.warning("Failed to clean up temporary directory: ${e.message}")
        }
    }

    // Helper classes for better organization
    private data class BuildContext(
        val manifest: Manifest,
        val outputDirPath: String
    ) {
        val outputDir: Path = Paths.get(outputDirPath)
        val tempClassesDir: Path = outputDir.resolve(TEMP_CLASSES_DIR)
        val jarFile: File = outputDir.resolve("${manifest.name}.jar").toFile()
    }

    private class CompilerMessageCollector : MessageCollector {
        private val errors = CopyOnWriteArrayList<String>()
        private val warnings = CopyOnWriteArrayList<String>()
        private val infos = CopyOnWriteArrayList<String>()

        val errorCount: Int get() = errors.size
        val warningCount: Int get() = warnings.size
        val infoCount: Int get() = infos.size

        override fun clear() {
            errors.clear()
            warnings.clear()
            infos.clear()
        }

        override fun hasErrors(): Boolean = errors.isNotEmpty()

        fun hasWarnings(): Boolean = warnings.isNotEmpty()

        fun hasInfos(): Boolean = infos.isNotEmpty()

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
                        if (loc.column > 0) {
                            append(':').append(loc.column)
                        }
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
                    infos.add(fullMessage)
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
            val displayErrors = errors.take(10)
            append(displayErrors.joinToString("\n"))
            if (errors.size > 10) {
                append("\n... and ${errors.size - 10} more errors")
            }
        }

        fun getWarningSummary(): String = buildString {
            val displayWarnings = warnings.take(5)
            append(displayWarnings.joinToString("\n"))
            if (warnings.size > 5) {
                append("\n... and ${warnings.size - 5} more warnings")
            }
        }
    }
}