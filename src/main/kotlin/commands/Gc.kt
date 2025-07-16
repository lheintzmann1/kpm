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
import kpm.core.KResult
import kpm.services.StoreManager

class Gc : CliktCommand() {
    private val storeManager = StoreManager()

    override fun run(): Unit = runBlocking {
        Logger.info("Running garbage collection...")

        val result = storeManager.garbageCollect()
        when (result) {
            is KResult.Success -> {
                Logger.success("Garbage collection completed successfully!")
                Logger.info("Removed ${result.data} unused dependencies from the store.")
            }
            is KResult.Error -> {
                Logger.error("Garbage collection failed: ${result.message}", result.cause)
            }
        }

        Logger.info("Finished garbage collection.")
    }

    override fun help(context: Context): String {
        return """
            Remove unused dependencies from the store.
            
            This command scans the store for dependencies that are not referenced by any project,
            and removes them to free up space.
        """.trimIndent()
    }
}