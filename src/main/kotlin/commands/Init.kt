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
import java.util.Scanner
import java.io.File

class Init: CliktCommand() {
    override fun run() {
        println("Initializing new project...")

        try {
            if (!File(Consts.KPM_TEMPLATES.path).exists()) {
                File(Consts.KPM_TEMPLATES.path).mkdirs()
            }

            if (!File("${Consts.KPM_TEMPLATES}/base.zip").exists()) {
                val zipURL: String = "${Consts.TEMPLATE_BASE}/archive/refs/heads/main.zip"
                val command = if (System.getProperty("os.name").startsWith("Windows")) {
                    "curl -L $zipURL -o ${Consts.KPM_TEMPLATES}\\base.zip"
                } else {
                    "wget $zipURL -O ${Consts.KPM_TEMPLATES}/base.zip"
                }
                println("Downloading project template...")
                val process = Runtime.getRuntime().exec(arrayOf(command))
                process.waitFor()
                if (process.exitValue() != 0) {
                    println("Failed to download the project template.")
                    return
                }
            }

            println("Unzipping project template to the current directory...")
            val unzipCommand = if (System.getProperty("os.name").startsWith("Windows")) {
                arrayOf("cmd.exe", "/c", "tar -xf ${Consts.KPM_TEMPLATES}\\base.zip --strip-components=1")
            } else {
                arrayOf("sh", "-c", "unzip -o ${Consts.KPM_TEMPLATES}/base.zip -d .")
            }
            val unzipProcess = ProcessBuilder(*unzipCommand)
                .inheritIO()
                .start()
            unzipProcess.waitFor()
            if (unzipProcess.exitValue() != 0) {
                println("Failed to unzip the project template.")
                return
            }

            println("Project initialized successfully!")

        } catch (e: Exception) {
            println("An error occurred while initializing the project: ${e.message}")
        }
    }

}
