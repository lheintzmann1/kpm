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
import com.github.ajalt.clikt.core.Context
import kotlinx.coroutines.runBlocking
import kpm.utils.Logger
import kpm.services.Template

class Init: CliktCommand() {
    private val templateService = Template()

    override fun run(): Unit = runBlocking {
        templateService.initProject()
            .onError { message, cause ->
                Logger.error("Failed to initialize project $message", cause)
                throw RuntimeException(message, cause)
            }
    }

    override fun help(context: Context): String {
        return """
            Initializes a new KPM project in the current directory.
        """.trimIndent()
    }
}
