package kpm.utils

import kpm.core.Constants as Consts
import java.io.File

fun downloadTemplate(): Boolean {
    val zipPath = "${Consts.KPM_TEMPLATES}${File.separator}base.zip"
    if (File(zipPath).exists()) return true

    val zipURL = "${Consts.TEMPLATE_BASE}/archive/refs/heads/main.zip"
    val cmd = when (Consts.PLATFORM) {
        "windows" -> arrayOf("cmd.exe", "/c", "curl", "-L", zipURL, "-o", zipPath)
        else -> arrayOf("curl", "-L", zipURL, "-o", zipPath)
    }
    println("Downloading project template...")
    return runProcess(cmd, "Failed to download the project template. Make sure 'curl' is installed.")
}

fun unzipTemplate(): Boolean {
    val zipPath = "${Consts.KPM_TEMPLATES}${File.separator}base.zip"
    val cmd = when (Consts.PLATFORM) {
        "windows" -> arrayOf("cmd.exe", "/c", "tar", "-xf", zipPath, "--strip-components=1")
        else -> arrayOf("unzip", "-o", zipPath, "-d", ".")
    }
    println("Unzipping project template to the current directory...")
    return runProcess(cmd, "Failed to unzip the project template. Make sure 'unzip' is installed.")
}

fun ensurePathDir() {
    val dir = File(Consts.KPM_HOME.path)
    if (!dir.exists()) dir.mkdirs()
}

fun ensureTemplatesDir() {
    ensurePathDir()
    val dir = File(Consts.KPM_TEMPLATES.path)
    if (!dir.exists()) dir.mkdirs()
}
