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

import java.util.Scanner
import java.io.File

fun init() {
    val scanner = Scanner(System.`in`)

    println("Initializing a new Kotlin project with KPM...")

    println("Project name: ")
    val name = scanner.nextLine().ifBlank { "my-kpm-app" }

    val projectDir = scanner.nextLine().ifBlank { "my-project" }
    /*if (projectDir.exists()) {
        println("Directory '$projectDir' already exists.")
        return
    }*/

    // clone the template located in src/main/resources/template/base
    val templateDir = File("src/main/resources/template/base")
    if (!templateDir.exists()) {
        println("Template directory does not exist: ${templateDir.absolutePath}")
        return
    }
    val projectPath = File(projectDir)
    if (!projectPath.mkdirs()) {
        println("Failed to create project directory: ${projectPath.absolutePath}")
        return
    }
    templateDir.copyRecursively(projectPath, overwrite = true)
    println("Project '$name' initialized in directory '$projectDir'.")

    println("Project initialized successfully.")
}
