package kpm.core

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object Constants {
    const val KPM_VERSION: String  = $$"${project.version}"

    private val osName = System.getProperty("os.name").lowercase()
    val PLATFORM = when {
        "windows" in osName -> Platform.WINDOWS
        listOf("mac", "nix", "sunos", "solaris", "bsd").any { it in osName } -> Platform.UNIX
        else -> Platform.OTHER
    }
    val HOME: Path = Paths.get(System.getProperty("user.home"))
    val KPM_HOME: Path = HOME.resolve(".kpm")
    val KPM_TEMPLATES: Path = KPM_HOME.resolve("templates")

    const val TEMPLATE_BASE: String = "https://github.com/KotlinPM/template-base"

    const val MANIFEST_SCHEMA: String = "https://raw.githubusercontent.com/KotlinPM/manifest-schema/refs/heads/main/manifest.schema.json"
    const val MANIFEST: String = "kpm.json"
    const val MANIFEST_LOCK: String = "kpm-lock.json"
}

enum class Platform {
    WINDOWS,
    UNIX,
    OTHER
}
