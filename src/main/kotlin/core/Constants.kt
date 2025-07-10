package kpm.core

import java.io.File

object Constants {
    const val KPM_VERSION: String  = $$"${project.version}"

    val osName = System.getProperty("os.name").lowercase()
    val PLATFORM = when {
        "windows" in osName -> "windows"
        listOf("mac", "nix", "sunos", "solaris", "bsd").any { it in osName } -> "*nix"
        else -> "other"
    }
    val HOME: File = File(System.getProperty("user.home"))
    val KPM_HOME: File = File("$HOME/.kpm").apply { if (!exists()) mkdirs() }
    val KPM_TEMPLATES: File = File("$KPM_HOME/templates").apply { if (!exists()) mkdirs() }

//    const val TEMPLATES_REPO: String = "https://github.com/KotlinPM/templates.git"
    const val TEMPLATE_BASE: String = "https://github.com/KotlinPM/template-base"

    const val MANIFEST: String = "kpm.json"
    const val MANIFEST_LOCK: String = "kpm-lock.json"
}