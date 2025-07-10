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
import kpm.core.Constants as Consts
import java.io.File

class Init: CliktCommand() {
    override fun run() {
        println("Initializing new project...")

        try {
            if (!File(Consts.KPM_TEMPLATES.path).exists()) {
                File(Consts.KPM_TEMPLATES.path).mkdirs()
            }

            val zipPath = "${Consts.KPM_TEMPLATES}${File.separator}base.zip"
            if (!File(zipPath).exists()) {
                val zipURL = "${Consts.TEMPLATE_BASE}/archive/refs/heads/main.zip"
                val curlCmd = if (Consts.PLATFORM == "windows") {
                    arrayOf("cmd.exe", "/c", "curl", "-L", zipURL, "-o", zipPath)
                } else {
                    arrayOf("curl", "-L", zipURL, "-o", zipPath)
                }
                println("Downloading project template...")
                val process = ProcessBuilder(*curlCmd)
                    .inheritIO()
                    .start()
                process.waitFor()
                if (process.exitValue() != 0) {
                    println("Failed to download the project template. Make sure 'curl' is installed.")
                    return
                }
            }

            println("Unzipping project template to the current directory...")
            val unzipCmd = if (Consts.PLATFORM == "windows") {
                arrayOf("cmd.exe", "/c", "tar", "-xf", zipPath, "--strip-components=1")
            } else {
                arrayOf("unzip", "-o", zipPath, "-d", ".")
            }
            val unzipProcess = ProcessBuilder(*unzipCmd)
                .inheritIO()
                .start()
            unzipProcess.waitFor()
            if (unzipProcess.exitValue() != 0) {
                println("Failed to unzip the project template. Make sure 'unzip' is installed.")
                return
            }

            println("Project initialized successfully!")

        } catch (e: Exception) {
            println("An error occurred while initializing the project: ${e.message}")
        }
    }
}
