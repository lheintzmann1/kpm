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
